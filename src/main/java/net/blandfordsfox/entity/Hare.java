package net.blandfordsfox.entity;

import net.blandfordsfox.BlandfordsFoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A vanilla {@link Rabbit} in behaviour: flees players, wolves and monsters, panics when hurt,
 * is tempted by and breeds with carrots, golden carrots and dandelions, and raids carrot
 * crops. Two coats: desert (deserts) and white (snowy plains and taiga, and other places
 * vanilla puts white rabbits).
 *
 * <p>Its run animation is a gallop, so instead of hopping it bounds along the ground: one
 * stride at a time with rests in between when ambling (like a rabbit's hop-and-pause), and
 * without stopping when it flees. The speed is matched to the animation so the paws stay
 * planted.
 */
public class Hare extends Rabbit implements GeoEntity {
    /** Blocks it can spawn on: everything rabbits spawn on, plus the desert rock. */
    public static final TagKey<Block> SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "hare_spawnable_on"));

    public enum Coat { DESERT, WHITE }

    private static final EntityDataAccessor<Integer> COAT = SynchedEntityData.defineId(Hare.class, EntityDataSerializers.INT);

    private static final String P = "animation.hare.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public Hare(EntityType<? extends Rabbit> type, Level level) {
        super(type, level);
        // walk on the ground like other animals; obstacle jumps go through HareJumpControl
        this.moveControl = new MoveControl(this);
        this.jumpControl = new HareJumpControl(this);
    }

    // ------------------------------------------------------------------ coat

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COAT, Coat.DESERT.ordinal());
    }

    public Coat getCoat() {
        int i = this.entityData.get(COAT);
        return i >= 0 && i < Coat.values().length ? Coat.values()[i] : Coat.DESERT;
    }

    public void setCoat(Coat coat) {
        this.entityData.set(COAT, coat.ordinal());
    }

    /** White where vanilla puts white rabbits (snowy biomes), desert everywhere else. */
    public static Coat coatFor(LevelAccessor level, BlockPos pos) {
        return level.getBiome(pos).is(BiomeTags.SPAWNS_WHITE_RABBITS) ? Coat.WHITE : Coat.DESERT;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("HareCoat", getCoat().name().toLowerCase());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCoat("white".equals(tag.getString("HareCoat")) ? Coat.WHITE : Coat.DESERT);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        setCoat(coatFor(level, blockPosition()));
        return data;
    }

    /** Leverets from hares are hares, with one parent's coat. */
    @Override
    @Nullable
    public Rabbit getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        Hare baby = BlandfordsFoxMod.HARE.get().create(level);
        if (baby != null) {
            Coat coat = getCoat();
            if (otherParent instanceof Hare other && this.random.nextBoolean()) coat = other.getCoat();
            baby.setCoat(coat);
        }
        return baby;
    }

    /** Only with other hares (vanilla would let it breed with any rabbit). */
    @Override
    public boolean canMate(Animal other) {
        return other instanceof Hare && super.canMate(other);
    }

    public static boolean checkHareSpawnRules(EntityType<Hare> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                              BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    // ------------------------------------------------------------- movement
    //
    // Vanilla rabbits move only while airborne, hop by hop. The hare's run is a gallop, so it
    // moves along the ground instead: the rabbit's own hop requests are ignored, and it jumps
    // only to get over a block, like other animals.

    @Override
    public void startJumping() {
        // the rabbit's hop; not used
    }

    /** No hop sound: the hare only jumps to get over things. */
    @Override
    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    /** Full jump height (the rabbit would hop lower when ambling). */
    @Override
    protected float getJumpPower() {
        return getJumpPower(1.0F);
    }

    /** A plain jump control: jumps on the tick the movement control asks for it. */
    static final class HareJumpControl extends Rabbit.RabbitJumpControl {
        private final Hare hare;

        HareJumpControl(Hare hare) {
            super(hare);
            this.hare = hare;
        }

        @Override
        public void tick() {
            hare.setJumping(this.jump);
            this.jump = false;
        }
    }

    // ------------------------------------------------------------- gait & speed
    //
    // The run's ground speed (how fast the front paws travel back while planted), measured
    // from the keyframes: 88 px/s at normal playback, 5.5 blocks a second. While it moves the
    // hare always goes at that speed, so its paws stay planted; the animation plays at its own
    // speed.
    //
    // Like a rabbit's hop-and-pause, an ambling hare goes in bounds: one stride of the run
    // (0.365 s, about 7 ticks), then a rest on the ground before the next. A fleeing or
    // panicking hare (the rabbit's x2.2 speed) runs without stopping.

    static final double RUN_SPEED = 5.5;
    static final int BOUND_TICKS = 7;
    static final int REST_TICKS = 10;
    static final double FLEE_MODIFIER = 2.0;

    private int boundTick;      // ticks into the current bound
    private int restLeft;       // ticks of rest left before the next bound
    private long lastGaitTick = Long.MIN_VALUE;

    /** On flat ground a mob covers about 44.05 * s^2 blocks per second for setSpeed(s). */
    private static float inputFor(double blocksPerSecond) {
        return (float) Math.sqrt(blocksPerSecond / 44.05);
    }

    private double sizeScale() {
        return isBaby() ? 0.5 : 1.0;   // leverets are drawn at half size
    }

    /** True while running flat out (fleeing or panicking): no rests between strides. */
    public boolean isFleeing() {
        return this.moveControl.getSpeedModifier() >= FLEE_MODIFIER;
    }

    // A mob only gets to full speed over a few ticks and coasts a few more after it stops,
    // which would make the paws skid at the start and end of every bound. So the first tick
    // of a bound pushes 2.2x as hard (input sqrt(2.2) larger: the push grows with the square
    // of the input), and the hare stops dead when the bound ends.
    private static final float LAUNCH = (float) Math.sqrt(2.2);
    private float gaitSpeed;
    private boolean stopNow;

    @Override
    public void setSpeed(float speed) {
        if (speed > 0 && !level().isClientSide) {
            long t = this.tickCount;
            if (t != lastGaitTick) {
                if (t != lastGaitTick + 1) { boundTick = 0; restLeft = 0; }   // setting off
                lastGaitTick = t;
                float run = inputFor(RUN_SPEED * sizeScale());
                if (isFleeing() || !onGround()) {
                    restLeft = 0;
                    gaitSpeed = run;
                } else if (restLeft > 0) {
                    restLeft--;
                    gaitSpeed = 0;
                    if (stopNow) {
                        stopNow = false;
                        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
                    }
                } else {
                    boolean launch = boundTick == 0;
                    if (launch) playSound(getJumpSound(), getSoundVolume() * 0.6F,
                            ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
                    gaitSpeed = launch ? run * LAUNCH : run;
                    if (++boundTick >= BOUND_TICKS) {
                        boundTick = 0;
                        restLeft = REST_TICKS + random.nextInt(6);
                        stopNow = true;
                    }
                }
            }
            speed = gaitSpeed;
        }
        super.setSpeed(speed);
    }

    // client-side: ground speed in blocks/s, lightly smoothed
    private double groundSpeed;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            double dx = getX() - xo, dz = getZ() - zo;
            double now = Math.sqrt(dx * dx + dz * dz) * 20;
            groundSpeed += (now - groundSpeed) * 0.6;
        }
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, this::pickAnimation));
    }

    private PlayState pickAnimation(AnimationState<Hare> state) {
        double v = groundSpeed / sizeScale();
        return state.setAndContinue(v > RUN_SPEED * 0.2 ? RUN : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

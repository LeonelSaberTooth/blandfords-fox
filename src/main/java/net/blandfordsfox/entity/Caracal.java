package net.blandfordsfox.entity;

import net.blandfordsfox.BlandfordsFoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.level.Level;
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
 * A vanilla {@link Ocelot} in every way that matters for behaviour: skittish around players,
 * won over with raw cod or salmon, creeps up when tempted, hunts chickens and baby turtles,
 * scares off creepers and phantoms once trusting, and breeds. Only the look, the spawn rules,
 * the movement speeds (matched to the animations) and the kitten type differ.
 */
public class Caracal extends Ocelot implements GeoEntity {
    /** Blocks it can spawn on: desert sand and rock, and savanna grass. */
    public static final TagKey<Block> SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "caracal_spawnable_on"));

    private final RawAnimation IDLE, WALK, RUN, SNEAK;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public Caracal(EntityType<? extends Ocelot> type, Level level) {
        super(type, level);
        String p = "animation." + animationName() + ".";
        IDLE = RawAnimation.begin().thenLoop(p + "idle");
        WALK = RawAnimation.begin().thenLoop(p + "walk");
        RUN = RawAnimation.begin().thenLoop(p + "run");
        SNEAK = RawAnimation.begin().thenLoop(p + "sneak");
    }

    /** The name its animations go by (animation.NAME.walk ...). */
    protected String animationName() {
        return "caracal";
    }

    /** Ground speed of the walk, run and sneak animations, in blocks per second (see below). */
    protected double walkSpeed() { return WALK_SPEED; }
    protected double runSpeed() { return RUN_SPEED; }
    protected double sneakSpeed() { return SNEAK_SPEED; }

    /** Kittens from caracals are caracals (vanilla would make an ocelot). */
    @Override
    @Nullable
    public Ocelot getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return BlandfordsFoxMod.CARACAL.get().create(level);
    }

    /** On desert or savanna ground, at or above sea level. */
    public static boolean checkCaracalSpawnRules(EntityType<Caracal> type, ServerLevelAccessor level,
                                                MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return pos.getY() >= level.getSeaLevel() && level.getBlockState(pos.below()).is(SPAWNABLE_ON);
    }

    // ------------------------------------------------------------- gaits & speed
    //
    // Each moving animation's ground speed (how fast its paws travel back while on the
    // ground), measured from the keyframes; the caracal moves at that speed so the paws
    // stay planted, and the animations play at their original speed.

    /** Movement in blocks per second for an adult, and animation playback speed, per gait. */
    static final double WALK_SPEED = 0.50;
    static final float WALK_PLAYBACK = 1.0F;
    static final double RUN_SPEED = 4.70;
    static final float RUN_PLAYBACK = 1.0F;
    static final double SNEAK_SPEED = 0.37;
    static final float SNEAK_PLAYBACK = 1.0F;

    /** Vanilla AI asks for a speed multiplier; at or above this the caracal runs instead of walks. */
    private static final double RUN_FROM_MULTIPLIER = 1.15;

    /** On flat ground a mob covers about 44.05 * s^2 blocks per second for setSpeed(s). */
    private static float inputFor(double blocksPerSecond) {
        return (float) Math.sqrt(blocksPerSecond / 44.05);
    }

    private double sizeScale() {
        return isBaby() ? 0.5 : 1.0;   // kittens are drawn at half size, so their paws cover half the ground
    }

    @Override
    public void setSpeed(float speed) {
        if (speed > 0 && !level().isClientSide) {
            double attribute = getAttributeValue(Attributes.MOVEMENT_SPEED);
            double multiplier = attribute > 0 ? speed / attribute : 1;
            double gait = isCrouching() ? sneakSpeed() : multiplier >= RUN_FROM_MULTIPLIER ? runSpeed() : walkSpeed();
            speed = inputFor(gait * sizeScale());
        }
        super.setSpeed(speed);
    }

    // client-side: smoothed ground speed in blocks/s, and which gait is showing
    private double groundSpeed;
    private boolean showingRun;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            double dx = getX() - xo, dz = getZ() - zo;
            double now = Math.sqrt(dx * dx + dz * dz) * 20;
            groundSpeed += (now - groundSpeed) * 0.35;
        }
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::pickAnimation));
    }

    private PlayState pickAnimation(AnimationState<Caracal> state) {
        state.setControllerSpeed(1.0F);
        double v = groundSpeed / sizeScale();   // in adult terms

        if (isCrouching()) {
            // creeping up on a player holding fish; holds still when not moving
            state.setControllerSpeed(v > sneakSpeed() * 0.3 ? SNEAK_PLAYBACK : 0F);
            return state.setAndContinue(SNEAK);
        }
        if (v < walkSpeed() * 0.3) {
            showingRun = false;
            return state.setAndContinue(IDLE);
        }
        // switch gaits with a little overlap so it doesn't flicker between them
        double mid = (walkSpeed() + runSpeed()) / 2;
        showingRun = showingRun ? v > mid * 0.8 : v > mid * 1.1;
        state.setControllerSpeed(showingRun ? RUN_PLAYBACK : WALK_PLAYBACK);
        return state.setAndContinue(showingRun ? RUN : WALK);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

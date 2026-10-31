package net.blandfordsfox.entity;

import net.blandfordsfox.BlandfordsFoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * A vanilla {@link Chicken} in behaviour and drops: wanders, pecks about, is tempted by and
 * breeds with seeds, lays eggs, drops feathers and raw chicken, flutters down instead of
 * taking fall damage.
 *
 * <p>But it is wary: when a player comes close (6 blocks, 3 if sneaking) or it is hit, it
 * bursts into flight away from them, fast and low over the ground, and lands a few seconds
 * later. Players holding seeds don't scare it, so it can still be led and bred.
 */
public class Partridge extends Chicken implements GeoEntity {
    public static final TagKey<Block> SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "partridge_spawnable_on"));

    /** 0 on the ground, 1 flying, 2 gliding in to land. */
    private static final EntityDataAccessor<Integer> FLIGHT = SynchedEntityData.defineId(Partridge.class, EntityDataSerializers.INT);
    public static final int GROUND = 0, FLYING = 1, LANDING = 2;

    static final double SCARE_RANGE = 6, SCARE_RANGE_SNEAKING = 3;
    static final double FLY_SPEED = 0.42;       // blocks a tick: about 8.4 blocks a second
    static final double CRUISE_HEIGHT = 1.6;    // above the ground below and just ahead
    static final int FLIGHT_MIN = 40, FLIGHT_MAX = 70, FLIGHT_LIMIT = 200;
    static final int COOLDOWN = 50;

    private static final String P = "animation.partridge.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop(P + "fly");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop(P + "glide");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private Entity scaredBy;
    private int flightCooldown;

    public Partridge(EntityType<? extends Chicken> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FlushGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLIGHT, GROUND);
    }

    public int getFlight() {
        return this.entityData.get(FLIGHT);
    }

    void setFlight(int flight) {
        this.entityData.set(FLIGHT, flight);
    }

    // ------------------------------------------------------------- chicken bits

    /** Same drops as a chicken: feathers and raw chicken (cooked if it burns). */
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return EntityType.CHICKEN.getDefaultLootTable();
    }

    @Override
    @Nullable
    public Chicken getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return BlandfordsFoxMod.PARTRIDGE.get().create(level);
    }

    @Override
    public boolean canMate(Animal other) {
        return other instanceof Partridge && super.canMate(other);
    }

    public static boolean checkPartridgeSpawnRules(EntityType<Partridge> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                                   BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    // ------------------------------------------------------------- getting scared

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() != null) {
            scaredBy = source.getEntity();
            flightCooldown = 0;
        }
        return hurt;
    }

    /** The nearest player close enough to flush it, if any. */
    @Nullable
    Entity findThreat() {
        if (scaredBy != null) {
            Entity e = scaredBy;
            scaredBy = null;
            if (e.isAlive() && e.distanceToSqr(this) < 24 * 24) return e;
        }
        return level().getNearestPlayer(getX(), getY(), getZ(), SCARE_RANGE, e -> e instanceof Player p && scares(p));
    }

    private boolean scares(Player p) {
        if (p.isSpectator() || p.isCreative()) return false;
        if (isFood(p.getMainHandItem()) || isFood(p.getOffhandItem())) return false;   // seeds: come closer
        double range = p.isCrouching() ? SCARE_RANGE_SNEAKING : SCARE_RANGE;
        return p.distanceToSqr(this) < range * range && hasLineOfSight(p);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && flightCooldown > 0 && getFlight() == GROUND) flightCooldown--;
    }

    // ------------------------------------------------------------- flight

    /**
     * Take off away from the threat, fly fast at about {@link #CRUISE_HEIGHT} over the ground
     * (rising over whatever is ahead), then glide down and land. Won't come down on water.
     */
    static final class FlushGoal extends Goal {
        private final Partridge bird;
        private Vec3 dir = Vec3.ZERO;
        private int ticks, flightTicks, stuck, landingStart;

        FlushGoal(Partridge bird) {
            this.bird = bird;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (bird.flightCooldown > 0 || bird.isPassenger() || bird.isLeashed() || bird.isInWater()) return false;
            Entity threat = bird.findThreat();
            if (threat == null) return false;
            dir = pickDirection(threat);
            return true;
        }

        /** Away from the threat, give or take; whichever way the ground rises least. */
        private Vec3 pickDirection(Entity threat) {
            Vec3 away = bird.position().subtract(threat.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = Vec3.directionFromRotation(0, bird.getYRot());
            away = away.normalize();
            double base = Math.atan2(away.z, away.x) + (bird.random.nextDouble() - 0.5) * 0.6;
            Vec3 best = null;
            double bestScore = Double.MAX_VALUE;
            for (double off : new double[] {0, 0.5, -0.5, 1.0, -1.0}) {
                Vec3 d = new Vec3(Math.cos(base + off), 0, Math.sin(base + off));
                double rise = 0;
                for (int i = 2; i <= 12; i += 2) rise = Math.max(rise, groundAt(bird.position().add(d.scale(i))) - bird.getY());
                double score = Math.max(0, rise - 3) * 4 + Math.abs(off) * 2;
                if (score < bestScore) { bestScore = score; best = d; }
            }
            return best;
        }

        private double groundAt(Vec3 p) {
            return bird.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            ticks = 0;
            stuck = 0;
            flightTicks = FLIGHT_MIN + bird.random.nextInt(FLIGHT_MAX - FLIGHT_MIN + 1);
            bird.getNavigation().stop();
            bird.setFlight(FLYING);
            bird.setNoGravity(true);
            bird.setDeltaMovement(dir.x * FLY_SPEED * 0.5, 0.42, dir.z * FLY_SPEED * 0.5);
            bird.playSound(SoundEvents.PARROT_FLY, 1.0F, 1.3F + bird.random.nextFloat() * 0.2F);
            bird.playSound(SoundEvents.CHICKEN_HURT, 0.6F, 1.4F);
        }

        @Override
        public boolean canContinueToUse() {
            if (!bird.isAlive() || bird.isInWater()) return false;
            if (bird.getFlight() == FLYING) return true;
            // gliding in: until it touches down (or catches on vines, or takes too long)
            return ticks < 3 || (!bird.onGround() && !bird.onClimbable() && ticks - landingStart < 60);
        }

        @Override
        public void tick() {
            ticks++;
            bird.setZza(0);
            float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
            bird.setYRot(yaw);
            bird.setYBodyRot(yaw);
            bird.setYHeadRot(yaw);

            if (bird.getFlight() == FLYING) {
                double ground = Math.max(groundAt(bird.position()), groundAt(bird.position().add(dir.scale(3))));
                double vy = Mth.clamp((ground + CRUISE_HEIGHT - bird.getY()) * 0.3, -0.25, 0.4);
                double speed = ticks < 4 ? FLY_SPEED * (0.5 + ticks * 0.125) : FLY_SPEED;
                if (bird.horizontalCollision) {   // a wall: climb, and give up if it won't clear
                    vy = 0.4;
                    speed *= 0.4;
                    if (++stuck > 12) flightTicks = 0;
                }
                bird.setDeltaMovement(dir.x * speed, vy, dir.z * speed);
                if (ticks % 6 == 0) bird.playSound(SoundEvents.PARROT_FLY, 0.5F, 1.4F);
                boolean overFluid = !bird.level().getFluidState(BlockPos.containing(bird.getX(), ground - 1, bird.getZ())).isEmpty();
                if ((ticks >= flightTicks && !overFluid) || ticks >= FLIGHT_LIMIT) {
                    bird.setFlight(LANDING);
                    bird.setNoGravity(false);
                    landingStart = ticks;
                }
            } else {
                // glide in: carry on forward, slowing, and come down gently
                Vec3 v = bird.getDeltaMovement();
                bird.setDeltaMovement(v.x * 0.88, Math.max(v.y - 0.04, -0.3), v.z * 0.88);
            }
        }

        @Override
        public void stop() {
            bird.setFlight(GROUND);
            bird.setNoGravity(false);
            bird.flightCooldown = COOLDOWN;
        }
    }

    // ------------------------------------------------------------- animation (client)

    // stride phase for the legs, advanced by the distance walked
    private float stride, strideO, stepAmount, stepAmountO;

    /** Leg swing: 0.7 rad either way on 2 px legs, so the planted foot moves 1 block per block walked at mid-step. */
    public static final float LEG_SWING = 0.7F;
    private static final float STRIDE_PER_BLOCK = 1.0F / (0.125F * LEG_SWING);

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            strideO = stride;
            stepAmountO = stepAmount;
            double dx = getX() - xo, dz = getZ() - zo;
            double dist = Math.sqrt(dx * dx + dz * dz);
            boolean walking = onGround() && getFlight() == GROUND && dist > 0.01;
            if (walking) stride += (float) (dist * STRIDE_PER_BLOCK / (isBaby() ? 0.5 : 1.0));
            stepAmount += ((walking ? 1.0F : 0.0F) - stepAmount) * 0.4F;
        }
    }

    public float getStride(float partialTick) {
        return Mth.lerp(partialTick, strideO, stride);
    }

    public float getStepAmount(float partialTick) {
        return Mth.lerp(partialTick, stepAmountO, stepAmount);
    }

    /** Flapping or gliding (also when falling: it flutters down, like a chicken). */
    public boolean isAirborneLook() {
        return getFlight() != GROUND || (!onGround() && getY() - yo < -0.08 && !isInWater());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::pickAnimation));
    }

    private PlayState pickAnimation(AnimationState<Partridge> state) {
        int f = getFlight();
        if (f == FLYING || (f == GROUND && isAirborneLook())) return state.setAndContinue(FLY);
        if (f == LANDING) return state.setAndContinue(GLIDE);
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

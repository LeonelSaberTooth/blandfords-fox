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
import net.minecraft.world.entity.animal.Fox;
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
 * A vanilla {@link Fox} in every way that matters for behaviour: it inherits all of the
 * fox's goals (sleeping by day, stalking and pouncing, berry eating, item carrying,
 * fleeing, trusting players, breeding) and its sounds, drops and saved data.
 * Only the look, the spawn rules and the cub type differ.
 */
public class BlandfordsFox extends Fox implements GeoEntity {
    /** Blocks it can spawn on: sand, red sand, terracotta and the vanilla fox's blocks. */
    public static final TagKey<Block> SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "blandfords_fox_spawnable_on"));

    private static final String P = "animation.blandfords_fox.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop(P + "sleep");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop(P + "sit");
    private static final RawAnimation CROUCH = RawAnimation.begin().thenLoop(P + "crouch");
    private static final RawAnimation STALK = RawAnimation.begin().thenLoop(P + "stalk");
    private static final RawAnimation POUNCE = RawAnimation.begin().thenPlayAndHold(P + "pounce");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BlandfordsFox(EntityType<? extends Fox> type, Level level) {
        super(type, level);
    }

    /** Cubs from two Blandford's foxes are Blandford's foxes (vanilla would make a red fox). */
    @Override
    @Nullable
    public Fox getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        BlandfordsFox cub = BlandfordsFoxMod.BLANDFORDS_FOX.get().create(level);
        if (cub != null && otherParent instanceof Fox other) {
            cub.setVariant(this.random.nextBoolean() ? this.getVariant() : other.getVariant());
        }
        return cub;
    }

    /** Vanilla's fox rule, but with desert ground: right block underneath and bright enough. */
    public static boolean checkBlandfordsFoxSpawnRules(EntityType<BlandfordsFox> type, ServerLevelAccessor level,
                                                       MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    // ------------------------------------------------------------- gaits & speed
    //
    // Each moving animation has a ground speed: how fast its planted paws sweep
    // backwards, measured from the keyframes (blocks per second at 1x playback,
    // for an adult). The fox moves at a speed chosen for each gait, and on the
    // client the animation is played back at (actual speed / ground speed), so
    // the paws stay planted even while it speeds up, slows down or turns.

    static final double WALK_GROUND = 6.6 / 16;    // WALK from the .bbmodel
    static final double RUN_GROUND = 40.0 / 16;    // RUN from the .bbmodel
    static final double STALK_GROUND = 3.33 / 16;  // STALK, made in tools/convert_model.py

    /** How fast it actually travels in each gait, blocks per second. */
    static final double WALK_SPEED = WALK_GROUND * 1.75;   // unhurried trot, ~0.7 blocks/s
    static final double RUN_SPEED = RUN_GROUND * 1.5;      // fleeing, chasing: ~3.75 blocks/s
    static final double STALK_SPEED = STALK_GROUND * 1.5;  // creeping up on prey, ~0.3 blocks/s

    /** Vanilla AI asks for a speed multiplier; at or above this the fox runs instead of walks. */
    private static final double RUN_FROM_MULTIPLIER = 1.15;

    /**
     * A mob on flat ground ends up moving at about 31.9 * s^2 blocks per second,
     * where s is the value MoveControl passes to setSpeed (friction 0.6 ground,
     * since it is both the movement input and the speed factor). Solve for s.
     */
    private static float inputFor(double blocksPerSecond) {
        return (float) Math.sqrt(blocksPerSecond / 31.9);
    }

    /**
     * Vanilla's goals still decide where the fox goes and how urgently; this turns
     * the speed they ask for into the gait whose animation fits.
     */
    @Override
    public void setSpeed(float speed) {
        if (speed > 0 && !level().isClientSide) {
            double attribute = getAttributeValue(Attributes.MOVEMENT_SPEED);
            double base = getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            double multiplier = attribute > 0 ? speed / attribute : 1;
            double gait = isCrouching() ? STALK_SPEED : multiplier >= RUN_FROM_MULTIPLIER ? RUN_SPEED : WALK_SPEED;
            // keep potions and other speed modifiers working, in proportion
            speed = inputFor(gait) * (float) (base > 0 ? attribute / base : 1);
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

    private PlayState pickAnimation(AnimationState<BlandfordsFox> state) {
        state.setControllerSpeed(1.0F);

        if (isSleeping()) return state.setAndContinue(SLEEP);
        if ((isPouncing() && !onGround()) || isFaceplanted()) return state.setAndContinue(POUNCE);
        if (isSitting()) return state.setAndContinue(SIT);

        double size = isBaby() ? 0.5 : 1.0;    // cubs are drawn at half size, so their paws cover half the ground
        double v = groundSpeed;

        if (isCrouching()) {
            if (v < 0.04) return state.setAndContinue(CROUCH);
            state.setControllerSpeed(rate(v, STALK_GROUND * size, 0.4, 3.0));
            return state.setAndContinue(STALK);
        }

        if (v < 0.06) {
            showingRun = false;
            return state.setAndContinue(IDLE);
        }
        // switch gaits with a little overlap so it doesn't flicker between them
        double walkTop = 1.6 * size, runBottom = 1.2 * size;
        showingRun = showingRun ? v > runBottom : v > walkTop;
        if (showingRun) {
            state.setControllerSpeed(rate(v, RUN_GROUND * size, 0.5, 2.2));
            return state.setAndContinue(RUN);
        }
        state.setControllerSpeed(rate(v, WALK_GROUND * size, 0.4, 4.0));
        return state.setAndContinue(WALK);
    }

    private static float rate(double speed, double ground, double lo, double hi) {
        return (float) Math.max(lo, Math.min(hi, speed / ground));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

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

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::pickAnimation));
    }

    private PlayState pickAnimation(AnimationState<BlandfordsFox> state) {
        state.setControllerSpeed(1.0F);

        if (isSleeping()) return state.setAndContinue(SLEEP);
        if (isPouncing() || isFaceplanted()) return state.setAndContinue(POUNCE);
        if (isSitting()) return state.setAndContinue(SIT);
        if (isCrouching()) return state.setAndContinue(CROUCH);

        float swing = state.getLimbSwingAmount();
        if (state.isMoving() && swing > 0.05F) {
            if (swing > 0.6F) {
                state.setControllerSpeed(clamp(swing / 0.8F, 0.8F, 1.3F));
                return state.setAndContinue(RUN);
            }
            state.setControllerSpeed(clamp(swing / 0.35F, 0.6F, 1.5F));
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.EventHooks;
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
 * A vanilla {@link Wolf} in most ways (hunting, taming, following, sitting, breeding, armour),
 * with these differences:
 * <ul>
 *   <li>no pack behaviour: hitting one doesn't bring the others, and they spawn alone;</li>
 *   <li>tamed with rotten flesh instead of bones;</li>
 *   <li>feeding it a bone makes it drop a bone block a few seconds later, at most 5 a day;</li>
 *   <li>sleeps through the day and is up at night ("sit" is lying down to rest).</li>
 * </ul>
 */
public class Hyena extends Wolf implements GeoEntity {
    public static final TagKey<Block> SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "hyena_spawnable_on"));

    private static final EntityDataAccessor<Boolean> SLEEPING = SynchedEntityData.defineId(Hyena.class, EntityDataSerializers.BOOLEAN);

    /** Bone blocks a hyena will make per Minecraft day. */
    public static final int BONE_BLOCKS_PER_DAY = 5;

    private static final String P = "animation.hyena.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
    private static final RawAnimation REST = RawAnimation.begin().thenLoop(P + "rest");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop(P + "sleep");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // bone blocks: which day the count is for, how many made that day, how many still to drop
    private long boneDay = -1;
    private int bonesToday, bonesQueued, boneTimer;

    public Hyena(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLEEPING, false);
    }

    public boolean isHyenaSleeping() {
        return this.entityData.get(SLEEPING);
    }

    void setHyenaSleeping(boolean sleeping) {
        this.entityData.set(SLEEPING, sleeping);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new SleepByDayGoal(this));
        // no pack: being hurt makes this hyena fight back, without calling the others
        this.targetSelector.removeAllGoals(g -> g instanceof HurtByTargetGoal);
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    /** For the self-test: how many "fight back when hurt" target goals it has (should be 1, without pack alerts). */
    public long hyenaHurtGoalsForTest() {
        return this.targetSelector.getAvailableGoals().stream().filter(g -> g.getGoal() instanceof HurtByTargetGoal).count();
    }

    /** On desert or savanna ground, at or above sea level. */
    public static boolean checkHyenaSpawnRules(EntityType<Hyena> type, ServerLevelAccessor level,
                                              MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return pos.getY() >= level.getSeaLevel() && level.getBlockState(pos.below()).is(SPAWNABLE_ON);
    }

    // ------------------------------------------------------------- feeding

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // a bone becomes a bone block, at most 5 a day (any hyena, tame or wild)
        if (stack.is(Items.BONE)) {
            if (this.level().isClientSide) return InteractionResult.CONSUME;
            long day = this.level().getDayTime() / 24000L;
            if (day != boneDay) { boneDay = day; bonesToday = 0; }
            if (bonesToday >= BONE_BLOCKS_PER_DAY) {
                this.level().broadcastEntityEvent(this, (byte) 6);   // smoke: not hungry for more today
                return InteractionResult.FAIL;
            }
            stack.consume(1, player);
            bonesToday++;
            if (bonesQueued++ == 0) boneTimer = 50 + this.random.nextInt(50);
            this.playSound(SoundEvents.GENERIC_EAT, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
            this.setHyenaSleeping(false);
            return InteractionResult.SUCCESS;
        }

        // wild hyenas are tamed with rotten flesh
        if (!this.isTame() && stack.is(Items.ROTTEN_FLESH) && !this.isAngry()) {
            if (this.level().isClientSide) return InteractionResult.CONSUME;
            stack.consume(1, player);
            if (this.random.nextInt(3) == 0 && !EventHooks.onAnimalTame(this, player)) {
                this.tame(player);
                this.navigation.stop();
                this.setTarget(null);
                this.setOrderedToSit(true);
                this.level().broadcastEntityEvent(this, (byte) 7);   // hearts
            } else {
                this.level().broadcastEntityEvent(this, (byte) 6);   // smoke
            }
            this.setHyenaSleeping(false);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && bonesQueued > 0 && --boneTimer <= 0) {
            this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, 0.6F + this.random.nextFloat() * 0.2F);
            this.spawnAtLocation(new ItemStack(Items.BONE_BLOCK));
            bonesQueued--;
            boneTimer = 30 + this.random.nextInt(40);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("BoneDay", boneDay);
        tag.putInt("BonesToday", bonesToday);
        tag.putInt("BonesQueued", bonesQueued);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boneDay = tag.contains("BoneDay") ? tag.getLong("BoneDay") : -1;
        bonesToday = tag.getInt("BonesToday");
        bonesQueued = tag.getInt("BonesQueued");
    }

    // ------------------------------------------------------------- breeding

    @Override
    public boolean canMate(Animal other) {
        return other instanceof Hyena && super.canMate(other);
    }

    @Override
    @Nullable
    public Wolf getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        Hyena cub = BlandfordsFoxMod.HYENA.get().create(level);
        if (cub != null && this.isTame()) {
            cub.setOwnerUUID(this.getOwnerUUID());
            cub.setTame(true, true);
        }
        return cub;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide) this.setHyenaSleeping(false);
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------- sleeping by day

    /**
     * By day a hyena lies down and sleeps: wild ones whenever they're idle, tame ones while
     * told to sit. Being hurt, getting a target, or a player walking up (unless sneaking)
     * wakes it. At night it's awake and goes about like a wolf.
     */
    static class SleepByDayGoal extends Goal {
        private final Hyena hyena;
        private int wakeCheck;

        SleepByDayGoal(Hyena hyena) {
            this.hyena = hyena;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private boolean sleepyNow() {
            Hyena h = hyena;
            if (!h.level().isDay() || h.isInWaterOrBubble() || !h.onGround() || h.isLeashed()) return false;
            if (h.getTarget() != null || h.isAngry() || h.getLastHurtByMob() != null) return false;
            if (h.isTame() && !h.isOrderedToSit()) return false;
            return !playerApproaching();
        }

        private boolean playerApproaching() {
            for (Player p : hyena.level().getEntitiesOfClass(Player.class, hyena.getBoundingBox().inflate(5))) {
                if (p.isSpectator() || p.isCreative() || p.isCrouching()) continue;
                if (hyena.isTame() && hyena.isOwnedBy(p)) continue;
                return true;
            }
            return false;
        }

        @Override
        public boolean canUse() {
            return hyena.random.nextInt(60) == 0 && sleepyNow();
        }

        @Override
        public boolean canContinueToUse() {
            if (--wakeCheck > 0) return true;
            wakeCheck = 10;
            return sleepyNow();
        }

        @Override
        public void start() {
            hyena.getNavigation().stop();
            hyena.setHyenaSleeping(true);
            wakeCheck = 10;
        }

        @Override
        public void stop() {
            hyena.setHyenaSleeping(false);
        }
    }

    // ------------------------------------------------------------- gaits & speed
    //
    // Ground speed of each animation (paws travelling back while on the ground), measured
    // from the keyframes; the hyena moves at that speed so its paws stay planted.

    static final double WALK_SPEED = 0.59;
    static final double RUN_SPEED = 4.3;
    private static final double RUN_FROM_MULTIPLIER = 1.15;

    private static float inputFor(double blocksPerSecond) {
        return (float) Math.sqrt(blocksPerSecond / 44.05);
    }

    private double sizeScale() {
        return isBaby() ? 0.5 : 1.0;
    }

    /** Runs when the AI is in a hurry, chasing something, or catching up with its owner. */
    private boolean wantsToRun(double multiplier) {
        if (multiplier >= RUN_FROM_MULTIPLIER || this.getTarget() != null) return true;
        LivingEntity owner = this.getOwner();
        return this.isTame() && owner != null && !this.isOrderedToSit() && this.distanceToSqr(owner) > 36;
    }

    @Override
    public void setSpeed(float speed) {
        if (speed > 0 && !level().isClientSide) {
            double attribute = getAttributeValue(Attributes.MOVEMENT_SPEED);
            double multiplier = attribute > 0 ? speed / attribute : 1;
            speed = inputFor((wantsToRun(multiplier) ? RUN_SPEED : WALK_SPEED) * sizeScale());
        }
        super.setSpeed(speed);
    }

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
        controllers.add(new AnimationController<>(this, "main", 5, this::pickAnimation));
    }

    private PlayState pickAnimation(AnimationState<Hyena> state) {
        if (isHyenaSleeping()) return state.setAndContinue(SLEEP);
        if (isInSittingPose()) return state.setAndContinue(REST);
        double v = groundSpeed / sizeScale();
        if (v < WALK_SPEED * 0.3) {
            showingRun = false;
            return state.setAndContinue(IDLE);
        }
        double mid = (WALK_SPEED + RUN_SPEED) / 2;
        showingRun = showingRun ? v > mid * 0.8 : v > mid * 1.1;
        return state.setAndContinue(showingRun ? RUN : WALK);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

package net.blandfordsfox.compat;

import net.blandfordsfox.BlandfordsFoxMod;
import net.blandfordsfox.mixin.FarEastTigerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Far East compatibility, server side: the tiger grooms now and then while resting, and likes to
 * mark birch trees (scratching the bark, rubbing its cheek on it, spraying it). Before a tree
 * animation starts the tiger walks to the exact spot and faces the exact way the animation was
 * made for, so nothing jumps when it begins.
 */
@EventBusSubscriber(modid = BlandfordsFoxMod.MOD_ID)
public final class FeTigerGoals {
    private FeTigerGoals() {}

    private static final ResourceLocation TIGER = ResourceLocation.fromNamespaceAndPath(FeTiger.MOD_ID, "tiger");

    /**
     * Where each tree animation was made with the trunk, in model pixels from the tiger's origin:
     * x to its right, z backwards (the trunk's centre; from the page's preview trees).
     */
    private static final double[][] TRUNK = {
            {0, -36},          // MARK: straight ahead, its face 28 px in front
            {19.25, -19},      // CHEEK_RUB: beside the head, on the right, its face 11.25 px to the side
            {0, 35.3},         // SPRAY: straight behind, its face 27.3 px back
    };
    /** Logs the trunk needs from the ground up for each (rearing up needs a taller one). */
    private static final int[] TRUNK_LOGS = {3, 2, 2};
    /** How much they like each: scratching most, then cheek rubs, then spraying. */
    private static final int[] WEIGHT = {4, 4, 3};

    /** Test hook: the next tree action to pick (-1: choose at random). */
    public static int forceNext = -1;
    /** Test hook: start right away instead of waiting for the mood to take them. */
    public static boolean eager;

    public static boolean isTiger(Mob mob) {
        return TIGER.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PathfinderMob mob) || !ModList.get().isLoaded(FeTiger.MOD_ID)) return;
        if (!isTiger(mob)) return;
        mob.goalSelector.addGoal(5, new GroomGoal(mob));
        mob.goalSelector.addGoal(6, new TreeGoal(mob));
    }

    private static FarEastTigerAccess fe(Mob mob) {
        return (FarEastTigerAccess) mob;
    }

    /** While lying down to rest, now and then it lifts its head and licks its foreleg. */
    public static final class GroomGoal extends Goal {
        private final Mob tiger;

        GroomGoal(Mob tiger) {
            this.tiger = tiger;
            this.setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            FarEastTigerAccess t = fe(this.tiger);
            return !this.tiger.isBaby() && t.blandfordsfox$getActivity() == 1 && t.blandfordsfox$getAction() == 0
                    && this.tiger.getTarget() == null && t.blandfordsfox$activityTicks() > FeTiger.GROOM_TICKS + 20
                    && (eager || this.tiger.getRandom().nextInt(300) == 0);
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            fe(this.tiger).blandfordsfox$startAction(FeTiger.GROOM, FeTiger.GROOM_TICKS);
        }
    }

    /** A tree, which animation, and where to stand for it. */
    record Plan(int kind, BlockPos trunk, Vec3 spot, float yaw, Vec3 lead) {}

    /** Walks to a birch, lines up exactly, and plays one of the three tree animations. */
    public static final class TreeGoal extends Goal {
        private enum Phase { APPROACH, ALIGN, TURN, PLAY, DONE }

        private final PathfinderMob tiger;
        private Plan plan;
        private Phase phase;
        private int ticks, phaseTicks, repaths;
        private long nextTry;
        private boolean viaLead;
        /** For the self-test: what happened last. */
        public static String last = "";

        TreeGoal(PathfinderMob tiger) {
            this.tiger = tiger;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        private boolean calm() {
            FarEastTigerAccess t = fe(this.tiger);
            return this.tiger.getTarget() == null && t.blandfordsfox$getMood() == 0 && t.blandfordsfox$getActivity() == 0
                    && !this.tiger.isInWater() && !this.tiger.isInLove();
        }

        @Override
        public boolean canUse() {
            if (this.tiger.isBaby() || !fe(this.tiger).blandfordsfox$isIdle()) return false;
            long now = this.tiger.tickCount;
            if (now < this.nextTry) return false;
            // they like it: an idle tiger near a birch goes to mark it every half a minute or so
            if (!eager && this.tiger.getRandom().nextInt(200) != 0) return false;
            this.nextTry = now + (eager ? 0 : 100);
            this.plan = choose();
            return this.plan != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.phase == Phase.DONE) return false;
            if (this.phase == Phase.PLAY) return fe(this.tiger).blandfordsfox$getAction() == this.plan.kind() + FeTiger.MARK;
            return calm() && this.ticks < 600 && isBirch(this.tiger.level(), this.plan.trunk());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.ticks = 0;
            this.phaseTicks = 0;
            this.repaths = 0;
            this.phase = Phase.APPROACH;
            this.viaLead = this.plan.lead() != null;
            Vec3 to = this.viaLead ? this.plan.lead() : this.plan.spot();
            this.tiger.getNavigation().moveTo(to.x, to.y, to.z, 0.7);
            last = "start " + this.plan;
        }

        @Override
        public void stop() {
            this.tiger.getNavigation().stop();
            if (this.phase != Phase.PLAY && this.phase != Phase.DONE) last += "; gave up in " + this.phase + " after " + this.ticks + " ticks";
            // a good while before the next one
            this.nextTry = this.tiger.tickCount + (eager ? 0 : 1200 + this.tiger.getRandom().nextInt(1200));
            this.phase = Phase.DONE;
        }

        private void setPhase(Phase p) {
            this.phase = p;
            this.phaseTicks = 0;
        }

        private double flatDistance(Vec3 v) {
            double dx = v.x - this.tiger.getX(), dz = v.z - this.tiger.getZ();
            return Math.sqrt(dx * dx + dz * dz);
        }

        private void face(float yaw) {
            this.tiger.setYRot(yaw);
            this.tiger.yRotO = yaw;
            this.tiger.yBodyRot = yaw;
            this.tiger.setYHeadRot(yaw);
        }

        @Override
        public void tick() {
            this.ticks++;
            this.phaseTicks++;
            Vec3 spot = this.plan.spot();
            Vec3 trunk = Vec3.atBottomCenterOf(this.plan.trunk()).add(0, 0.8, 0);
            switch (this.phase) {
                case APPROACH -> {
                    this.tiger.getLookControl().setLookAt(trunk.x, trunk.y, trunk.z, 20, 20);
                    if (this.viaLead && flatDistance(this.plan.lead()) < 0.6) {
                        // from the lead-in point it walks straight onto the spot, already facing the right way
                        this.viaLead = false;
                        this.tiger.getNavigation().stop();
                        setPhase(Phase.ALIGN);
                    } else if (!this.viaLead && flatDistance(spot) < 1.2) {
                        this.tiger.getNavigation().stop();
                        setPhase(Phase.ALIGN);
                    } else if (this.tiger.getNavigation().isDone()) {
                        Vec3 to = this.viaLead ? this.plan.lead() : spot;
                        if (flatDistance(to) < 2.0) {
                            this.viaLead = false;
                            setPhase(Phase.ALIGN);
                        } else if (this.repaths++ < 4) {
                            this.tiger.getNavigation().moveTo(to.x, to.y, to.z, 0.7);
                        } else {
                            this.phase = Phase.DONE;
                        }
                    }
                }
                case ALIGN -> {
                    double d = flatDistance(spot);
                    if (d < 0.07) {
                        // the last few hundredths of a block: set it on the spot (too small to see)
                        this.tiger.setPos(spot.x, this.tiger.getY(), spot.z);
                        this.tiger.setDeltaMovement(Vec3.ZERO);
                        last += "; on the spot after " + this.ticks + " ticks (was " + String.format(java.util.Locale.ROOT, "%.3f", d) + " off)";
                        setPhase(Phase.TURN);
                    } else if (this.phaseTicks > 80) {
                        last += "; could not reach the spot (" + String.format(java.util.Locale.ROOT, "%.2f", d) + " off)";
                        this.phase = Phase.DONE;
                    } else {
                        // walk the last stretch slowly, slowing down as it arrives
                        double speed = Mth.clamp(d * 1.6, 0.25, 0.6);
                        this.tiger.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, speed);
                    }
                }
                case TURN -> {
                    this.tiger.setPos(spot.x, this.tiger.getY(), spot.z);
                    this.tiger.setDeltaMovement(Vec3.ZERO);
                    float yaw = Mth.approachDegrees(this.tiger.getYRot(), this.plan.yaw(), 9F);
                    face(yaw);
                    if (Math.abs(Mth.wrapDegrees(this.plan.yaw() - yaw)) < 0.01F) {
                        face(this.plan.yaw());
                        fe(this.tiger).blandfordsfox$startAction(this.plan.kind() + FeTiger.MARK, FeTiger.ticks(this.plan.kind() + FeTiger.MARK));
                        last += "; playing " + (this.plan.kind() + FeTiger.MARK) + " at tick " + this.ticks + ", at "
                                + String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f yaw %.1f", this.tiger.getX(), this.tiger.getY(), this.tiger.getZ(), this.tiger.getYRot());
                        setPhase(Phase.PLAY);
                    }
                }
                case PLAY -> {
                    // stays exactly where the animation needs it
                    this.tiger.setPos(spot.x, this.tiger.getY(), spot.z);
                    this.tiger.setDeltaMovement(Vec3.ZERO);
                    face(this.plan.yaw());
                }
                default -> { }
            }
        }

        /** The nearest birch the tiger can mark, with an action it fits and a free spot to stand. */
        private Plan choose() {
            Level level = this.tiger.level();
            BlockPos at = this.tiger.blockPosition();
            List<BlockPos> trunks = new ArrayList<>();
            for (int dx = -10; dx <= 10; dx++)
                for (int dz = -10; dz <= 10; dz++)
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos p = at.offset(dx, dy, dz);
                        if (isBirch(level, p) && !isBirch(level, p.below()) && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP))
                            trunks.add(p);
                    }
            trunks.sort((a, b) -> Double.compare(a.distSqr(at), b.distSqr(at)));
            int tried = 0;
            for (BlockPos trunk : trunks) {
                if (tried++ >= 6) break;
                for (int kind : order()) {
                    if (!tall(level, trunk, TRUNK_LOGS[kind])) continue;
                    Plan best = null;
                    double bestD = Double.MAX_VALUE;
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        Plan plan = planFor(level, trunk, kind, dir);
                        if (plan == null) continue;
                        double d = plan.spot().distanceToSqr(this.tiger.position());
                        if (d < bestD) { bestD = d; best = plan; }
                    }
                    if (best != null) return best;
                }
            }
            return null;
        }

        private int[] order() {
            if (forceNext >= 0) return new int[]{forceNext};
            int total = WEIGHT[0] + WEIGHT[1] + WEIGHT[2], r = this.tiger.getRandom().nextInt(total);
            int first = r < WEIGHT[0] ? 0 : r < WEIGHT[0] + WEIGHT[1] ? 1 : 2;
            return new int[]{first, (first + 1) % 3, (first + 2) % 3};
        }

        /** Where to stand, facing dir, so the trunk is where this animation was made with it. */
        private Plan planFor(Level level, BlockPos trunk, int kind, Direction dir) {
            float yaw = dir.toYRot();
            double rad = Math.toRadians(yaw);
            // the tiger's forward and right, in the world
            double fx = -Math.sin(rad), fz = Math.cos(rad), rx = -Math.cos(rad), rz = -Math.sin(rad);
            double side = TRUNK[kind][0] / 16, back = TRUNK[kind][1] / 16;
            // trunk = spot + right * side - forward * back  (back is negative when it is in front)
            double cx = trunk.getX() + 0.5, cz = trunk.getZ() + 0.5;
            double sx = cx - rx * side + fx * back, sz = cz - rz * side + fz * back;
            Vec3 spot = new Vec3(sx, trunk.getY(), sz);
            if (!standable(level, spot)) return null;
            // a lead-in point a block and a half behind, so it walks onto the spot already facing the right way
            Vec3 lead = new Vec3(sx - fx * 1.5, trunk.getY(), sz - fz * 1.5);
            if (!standable(level, lead) || !clear(level, lead, spot)) lead = null;
            Path path = this.tiger.getNavigation().createPath(BlockPos.containing(lead != null ? lead : spot), 0);
            if (path == null || !path.canReach()) return null;
            return new Plan(kind, trunk, spot, yaw, lead);
        }

        private boolean standable(Level level, Vec3 spot) {
            AABB box = this.tiger.getDimensions(this.tiger.getPose()).makeBoundingBox(spot);
            if (!level.noCollision(this.tiger, box)) return false;
            BlockPos under = BlockPos.containing(spot.x, spot.y - 0.5, spot.z);
            return level.getBlockState(under).isFaceSturdy(level, under, Direction.UP) && level.getFluidState(BlockPos.containing(spot)).isEmpty();
        }

        private boolean clear(Level level, Vec3 a, Vec3 b) {
            AABB box = this.tiger.getDimensions(this.tiger.getPose()).makeBoundingBox(a)
                    .minmax(this.tiger.getDimensions(this.tiger.getPose()).makeBoundingBox(b));
            return level.noCollision(this.tiger, box);
        }
    }

    static boolean isBirch(Level level, BlockPos p) {
        return level.getBlockState(p).is(Blocks.BIRCH_LOG);
    }

    private static boolean tall(Level level, BlockPos base, int logs) {
        for (int i = 1; i < logs; i++) if (!isBirch(level, base.above(i))) return false;
        return true;
    }
}

package net.maidens.maiden.war;

import net.maidens.maiden.Maiden;
import net.maidens.politics.WarParties;
import net.maidens.village.VillageRecord;
import net.maidens.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/** What raiders do, and what defenders do about them. */
public final class RaidGoals {
    private RaidGoals() {}

    /** A raider presses into the village (towards its centre), and helps herself to what's in its chests. */
    public static final class Advance extends Goal {
        private final Maiden m;
        private int repath, lootCheck, chestSearch, stuck;
        private double closest = Double.MAX_VALUE;
        @org.jetbrains.annotations.Nullable private BlockPos aim;
        @org.jetbrains.annotations.Nullable private BlockPos chest;

        public Advance(Maiden m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return m.raider().active() && !m.raider().surrendered() && m.getTarget() == null;
        }

        @Override
        public void tick() {
            if (!(m.level() instanceof ServerLevel level)) return;
            VillageRecord v = Villages.get(level).byId(m.raider().target());
            if (v == null) return;
            if (--lootCheck <= 0) {
                lootCheck = 40;
                if (steal(level)) {
                    chest = null;
                    return;
                }
            }
            if (--repath > 0) return;
            repath = 40;
            // a walled village: in through the nearest gate (to just outside it, then just inside it)
            BlockPos through = throughGate(v);
            if (through != null) {
                go(through);
                return;
            }
            Vec3 c = Vec3.atBottomCenterOf(v.bell() != null ? v.bell() : v.centre());
            // in the village: off to the nearest chest with something in it
            if (m.distanceToSqr(c) < 40 * 40) {
                if (chest == null || --chestSearch <= 0) {
                    chestSearch = 10;
                    chest = nearestChest(level, v);
                }
                if (chest != null) {
                    m.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 0.7);
                    return;
                }
            }
            Vec3 step = m.distanceToSqr(c) > 30 * 30 ? LandRandomPos.getPosTowards(m, 20, 7, c) : LandRandomPos.getPosTowards(m, 8, 4, c);
            if (step != null) m.getNavigation().moveTo(step.x, step.y, step.z, 0.7);
        }

        /** Outside the walls: the spot to make for (outside the nearest gate, then inside it); null once she's inside. */
        @org.jetbrains.annotations.Nullable
        private BlockPos throughGate(VillageRecord v) {
            if (v.wallBox == null || v.gates.isEmpty()) return null;
            var inner = v.wallBox.inflatedBy(-2);
            BlockPos at = m.blockPosition();
            if (at.getX() >= inner.minX() && at.getX() <= inner.maxX() && at.getZ() >= inner.minZ() && at.getZ() <= inner.maxZ()) return null;
            VillageRecord.Gate best = null;
            double bd = Double.MAX_VALUE;
            for (VillageRecord.Gate g : v.gates) {
                double d = at.distSqr(g.pos);
                if (d < bd) {
                    bd = d;
                    best = g;
                }
            }
            int dx = Integer.signum(best.dx), dz = Integer.signum(best.dz);
            BlockPos out = best.pos.offset(dx * 5, 0, dz * 5), in = best.pos.offset(-dx * 6, 0, -dz * 6);
            // close to the gate already (outside it, or in it): straight through
            return at.distSqr(out) < 4 * 4 || at.distSqr(best.pos) < 5 * 5 ? in : out;
        }

        /** Makes for a spot: the whole way planned now and then (further than usual), a stretch at a time otherwise. */
        private void go(BlockPos to) {
            Vec3 c = Vec3.atBottomCenterOf(to);
            if (!to.equals(aim)) {
                aim = to;
                closest = Double.MAX_VALUE;
                stuck = 0;
            }
            double d = m.distanceToSqr(c);
            if (d < closest - 1) {
                closest = d;
                stuck = 0;
            } else if (++stuck > 8 && m.arriveNear(to)) {     // 15 s without getting closer (no one watching): she gets there
                stuck = 0;
                return;
            }
            var range = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            double was = range.getBaseValue();
            range.setBaseValue(128);
            var path = m.getNavigation().createPath(to, 1);
            range.setBaseValue(was);
            if (path != null && path.getNodeCount() > 1) m.getNavigation().moveTo(path, 0.7);
            else {
                Vec3 step = LandRandomPos.getPosTowards(m, 16, 7, c);
                if (step != null) m.getNavigation().moveTo(step.x, step.y, step.z, 0.7);
            }
        }

        /** The nearest chest in the village (loaded land only) that isn't empty. */
        @org.jetbrains.annotations.Nullable
        private BlockPos nearestChest(ServerLevel level, VillageRecord v) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            var box = v.box;
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    var chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (!(be instanceof ChestBlockEntity c) || c.isEmpty()) continue;
                        double d = m.distanceToSqr(Vec3.atCenterOf(be.getBlockPos()));
                        if (d < bestD) {
                            bestD = d;
                            best = be.getBlockPos();
                        }
                    }
                }
            return best;
        }

        /** Empties up to two stacks from a chest beside her into her bag; after four stacks she runs home with them. */
        private boolean steal(ServerLevel level) {
            BlockPos at = m.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 2, 2))) {
                BlockEntity be = level.getBlockEntity(p);
                if (!(be instanceof ChestBlockEntity) || !(be instanceof Container chest)) continue;
                int taken = 0;
                for (int i = 0; i < chest.getContainerSize() && taken < 2; i++) {
                    ItemStack s = chest.getItem(i);
                    if (s.isEmpty()) continue;
                    m.employment().store(s.copy());
                    chest.setItem(i, ItemStack.EMPTY);
                    taken++;
                }
                if (taken == 0) continue;
                chest.setChanged();
                m.swing(InteractionHand.MAIN_HAND);
                level.playSound(null, p, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6F, 1.1F);
                m.raider().stole(taken);
                WarParties.onStolen(level, m, taken);
                if (m.raider().stolen() >= 4) m.raider().surrender();
                return true;
            }
            return false;
        }
    }

    /** On a raid a player leads: she follows them until they're close to the village they're raiding. */
    public static final class FollowLeader extends Goal {
        private final Maiden m;
        private int repath;

        public FollowLeader(Maiden m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private net.minecraft.world.entity.player.Player leader() {
            java.util.UUID id = m.raider().leader();
            return id == null ? null : m.level().getPlayerByUUID(id);
        }

        /** Still on the way: no raided village around here yet, or not near it. */
        private boolean onTheWay() {
            if (!m.raider().active() || m.raider().surrendered() || leader() == null || !(m.level() instanceof ServerLevel level)) return false;
            net.maidens.politics.KnownVillage t = net.maidens.politics.Network.get(level).village(m.raider().target());
            if (t == null) return true;
            double dx = m.getX() - t.x, dz = m.getZ() - t.z;
            return dx * dx + dz * dz > 72 * 72 || Villages.get(level).byId(t.id) == null;
        }

        @Override
        public boolean canUse() {
            return onTheWay() && m.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return onTheWay() && m.getTarget() == null;
        }

        @Override
        public void tick() {
            var p = leader();
            if (p == null) return;
            m.getLookControl().setLookAt(p);
            double d = m.distanceToSqr(p);
            // left far behind (the leader rode off): she catches up, out of sight
            if (d > 40 * 40) {
                m.moveNear(p.blockPosition().relative(p.getDirection().getOpposite(), 6));
                return;
            }
            if (d < 4 * 4) { m.getNavigation().stop(); return; }
            if (--repath > 0) return;
            repath = 10;
            m.getNavigation().moveTo(p, d > 12 * 12 ? 0.9 : 0.6);
        }
    }

    /** A raider who's given up runs back the way she came and leaves. */
    public static final class Flee extends Goal {
        private final Maiden m;
        private int ticks, repath;

        public Flee(Maiden m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return m.raider().active() && m.raider().surrendered();
        }

        @Override
        public void start() {
            ticks = 0;
            repath = 0;
        }

        @Override
        public void tick() {
            BlockPos home = m.raider().retreat();
            if (++ticks > 600 || home == null || m.distanceToSqr(Vec3.atCenterOf(home)) < 4 * 4) {
                if (m.raider().resident()) m.raider().finish();     // one of a player's raiders: back to her life
                else m.discard();                                  // gone home
                return;
            }
            if (--repath > 0) return;
            repath = 30;
            Vec3 c = Vec3.atBottomCenterOf(home);
            Vec3 step = m.distanceToSqr(c) > 30 * 30 ? LandRandomPos.getPosTowards(m, 20, 7, c) : c;
            if (step != null) m.getNavigation().moveTo(step.x, step.y, step.z, 0.9);
        }
    }

    /** A raider goes for the raided village's maidens and its citizens. */
    public static final class Targets extends NearestAttackableTargetGoal<LivingEntity> {
        public Targets(Maiden m) {
            super(m, LivingEntity.class, 10, false, false, e -> isEnemyOf(m, e));
        }

        @Override
        protected double getFollowDistance() {
            return 24;
        }

        @Override
        public boolean canUse() {
            Maiden m = (Maiden) mob;
            return m.raider().active() && !m.raider().surrendered() && super.canUse();
        }

        private static boolean isEnemyOf(Maiden raider, LivingEntity e) {
            if (e instanceof Maiden other) return !other.raider().active() && other.residence().village() == raider.raider().target();
            if (e instanceof Player p && !p.isCreative() && !p.isSpectator() && raider.level() instanceof ServerLevel level) {
                VillageRecord v = Villages.get(level).byId(raider.raider().target());
                return v != null && v.isCitizen(p.getUUID());
            }
            return false;
        }
    }

    /** A village's fighters go for raiders attacking it. */
    public static final class Defend extends NearestAttackableTargetGoal<Maiden> {
        public Defend(Maiden m) {
            super(m, Maiden.class, 10, true, false, e -> e instanceof Maiden r && r.raider().active() && !r.raider().surrendered()
                    && r.raider().target() == m.residence().village());
        }

        /** Raiders anywhere near are seen to (the whole village turns out). */
        @Override
        protected double getFollowDistance() {
            return 48;
        }

        @Override
        public boolean canUse() {
            Maiden m = (Maiden) mob;
            return m.isFighter() && !m.raider().active() && super.canUse();
        }
    }
}

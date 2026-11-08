package net.maidens.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** The maiden's smaller goals: standing still while someone talks to her, running from or fighting monsters, following her employer. */
final class MaidenGoals {
    private MaidenGoals() {}

    /** Stands still and looks at whoever has her menu open. */
    static final class Talk extends Goal {
        private final Maiden m;

        Talk(Maiden m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player p = m.getTalker();
            return p != null && p.distanceToSqr(m) < 12 * 12 && m.getTarget() == null;
        }

        @Override
        public void start() {
            m.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player p = m.getTalker();
            if (p != null) m.getLookControl().setLookAt(p, 30.0F, 30.0F);
        }
    }

    /** Unless she's a guard, she runs from monsters like a villager does. */
    static final class Flee extends AvoidEntityGoal<Monster> {
        private final Maiden m;

        Flee(Maiden m) {
            super(m, Monster.class, 8.0F, 0.6, 0.75);
            this.m = m;
        }

        @Override
        public boolean canUse() {
            return !m.isGuarding() && super.canUse();
        }
    }

    /** A guard's melee attack, with whatever weapon is in her hand. */
    static final class Fight extends MeleeAttackGoal {
        private final Maiden m;

        Fight(Maiden m) {
            super(m, 0.8, true);
            this.m = m;
        }

        @Override
        public boolean canUse() {
            return m.isGuarding() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return m.isGuarding() && super.canContinueToUse();
        }
    }

    /** A guard keeps close to the player who hired her, and catches up if left far behind. */
    static final class FollowEmployer extends Goal {
        private final Maiden m;
        private Player p;
        private int repath;

        FollowEmployer(Maiden m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!m.isGuarding() || m.getTarget() != null) return false;
            p = m.getEmployerPlayer();
            return p != null && !p.isSpectator() && m.distanceToSqr(p) > 4 * 4;
        }

        @Override
        public boolean canContinueToUse() {
            return m.isGuarding() && p != null && p.isAlive() && m.getTarget() == null && m.distanceToSqr(p) > 2.5 * 2.5;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            m.getNavigation().stop();
        }

        @Override
        public void tick() {
            m.getLookControl().setLookAt(p, 10.0F, m.getMaxHeadXRot());
            if (--repath > 0) return;
            repath = 10;
            if (m.distanceToSqr(p) > 24 * 24 && p.level() == m.level()) {
                teleportNear();
                return;
            }
            m.getNavigation().moveTo(p, m.distanceToSqr(p) > 10 * 10 ? 0.9 : 0.7);
        }

        private void teleportNear() {
            for (int i = 0; i < 10; i++) {
                int dx = m.getRandom().nextInt(5) - 2, dz = m.getRandom().nextInt(5) - 2;
                if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
                var pos = p.blockPosition().offset(dx, 0, dz);
                if (m.level().getBlockState(pos.below()).isSolid() && m.level().isEmptyBlock(pos) && m.level().isEmptyBlock(pos.above())) {
                    m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, m.getYRot(), m.getXRot());
                    m.getNavigation().stop();
                    return;
                }
            }
        }
    }

    /** Wanders around, but only when she isn't hired. */
    static final class Stroll extends WaterAvoidingRandomStrollGoal {
        private final Maiden m;

        Stroll(Maiden m) {
            super(m, 0.5);
            this.m = m;
        }

        @Override
        public boolean canUse() {
            return m.getJob() == Job.NONE && super.canUse();
        }
    }

    /** A guard hits back at whatever hurt her. */
    static final class Retaliate extends HurtByTargetGoal {
        private final Maiden m;

        Retaliate(Maiden m) {
            super(m);
            this.m = m;
        }

        @Override
        public boolean canUse() {
            return m.isGuarding() && !(m.getLastHurtByMob() instanceof Player) && super.canUse();
        }
    }

    /** A guard goes for whatever attacks her employer, or whatever her employer attacks (players aside). */
    static final class DefendEmployer extends TargetGoal {
        private final Maiden m;
        private LivingEntity enemy;
        private int lastHurtBy, lastHurt;

        DefendEmployer(Maiden m) {
            super(m, false);
            this.m = m;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!m.isGuarding()) return false;
            Player p = m.getEmployerPlayer();
            if (p == null) return false;
            LivingEntity a = p.getLastHurtByMob();
            if (a != null && p.getLastHurtByMobTimestamp() != lastHurtBy && ok(a)) {
                enemy = a;
                lastHurtBy = p.getLastHurtByMobTimestamp();
                return true;
            }
            LivingEntity b = p.getLastHurtMob();
            if (b != null && p.getLastHurtMobTimestamp() != lastHurt && ok(b)) {
                enemy = b;
                lastHurt = p.getLastHurtMobTimestamp();
                return true;
            }
            return false;
        }

        private boolean ok(LivingEntity e) {
            return e != m && !(e instanceof Player) && !(e instanceof Maiden) && e.isAlive() && canAttack(e, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(enemy);
            super.start();
        }
    }

    /** A guard attacks monsters near her (creepers she leaves alone). */
    static final class HuntMonsters extends NearestAttackableTargetGoal<Monster> {
        private final Maiden m;

        HuntMonsters(Maiden m) {
            super(m, Monster.class, 10, true, false, e -> !(e instanceof Creeper));
            this.m = m;
        }

        @Override
        public boolean canUse() {
            return m.isGuarding() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return m.isGuarding() && super.canContinueToUse();
        }
    }

    static Vec3 centre(net.minecraft.core.BlockPos p) {
        return Vec3.atCenterOf(p);
    }

    static boolean near(Mob m, net.minecraft.core.BlockPos p, double reach) {
        return m.distanceToSqr(Vec3.atCenterOf(p)) <= reach * reach;
    }
}

package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** A hired guard keeps close to the player who hired her, and catches up if left far behind. */
final class FollowEmployerGoal extends Goal {
    private final Maiden m;
    private Player p;
    private int repath;

    FollowEmployerGoal(Maiden m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!m.employment().isGuarding() || m.getTarget() != null) return false;
        p = m.employment().employerPlayer();
        return p != null && !p.isSpectator() && m.distanceToSqr(p) > 4 * 4;
    }

    @Override
    public boolean canContinueToUse() {
        return m.employment().isGuarding() && p != null && p.isAlive() && m.getTarget() == null && m.distanceToSqr(p) > 2.5 * 2.5;
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
            BlockPos pos = p.blockPosition().offset(dx, 0, dz);
            if (m.level().getBlockState(pos.below()).isSolid() && m.level().isEmptyBlock(pos) && m.level().isEmptyBlock(pos.above())) {
                m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, m.getYRot(), m.getXRot());
                m.getNavigation().stop();
                return;
            }
        }
    }
}

package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Stands still and looks at whoever has her menu open. */
final class TalkGoal extends Goal {
    private final Maiden m;

    TalkGoal(Maiden m) {
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

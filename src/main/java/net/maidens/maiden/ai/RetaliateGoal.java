package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;

/** A fighter hits back at whatever hurt her (never at players). */
final class RetaliateGoal extends HurtByTargetGoal {
    private final Maiden m;

    RetaliateGoal(Maiden m) {
        super(m);
        this.m = m;
    }

    @Override
    public boolean canUse() {
        return m.isFighter() && !(m.getLastHurtByMob() instanceof Player) && super.canUse();
    }
}

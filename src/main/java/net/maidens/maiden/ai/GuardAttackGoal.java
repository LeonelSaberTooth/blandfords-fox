package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** A fighter's melee attack (a hired guard, or a village defender in a raid), with whatever weapon is in her hand. */
final class GuardAttackGoal extends MeleeAttackGoal {
    private final Maiden m;

    GuardAttackGoal(Maiden m) {
        super(m, 0.8, true);
        this.m = m;
    }

    @Override
    public boolean canUse() {
        return m.isFighter() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return m.isFighter() && super.canContinueToUse();
    }
}

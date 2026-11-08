package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;

/** A fighter attacks monsters near her (creepers she leaves alone). */
final class HuntMonstersGoal extends NearestAttackableTargetGoal<Monster> {
    private final Maiden m;

    HuntMonstersGoal(Maiden m) {
        super(m, Monster.class, 10, true, false, e -> !(e instanceof Creeper));
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

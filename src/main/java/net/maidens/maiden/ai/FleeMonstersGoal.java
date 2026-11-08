package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Monster;

/** Unless she's a guard, she runs from monsters like a villager does. */
final class FleeMonstersGoal extends AvoidEntityGoal<Monster> {
    private final Maiden m;

    FleeMonstersGoal(Maiden m) {
        super(m, Monster.class, 8.0F, 0.6, 0.75);
        this.m = m;
    }

    @Override
    public boolean canUse() {
        return !m.isFighter() && super.canUse();
    }
}

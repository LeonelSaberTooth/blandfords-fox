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

    private int cooldown;

    /** Looks for monsters twice a second, not every other tick (the search is a scan of the entities around her). */
    @Override
    public boolean canUse() {
        if (--cooldown > 0 || m.isFighter()) return false;
        cooldown = 5;
        return super.canUse();
    }
}

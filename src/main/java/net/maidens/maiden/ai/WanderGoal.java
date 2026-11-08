package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

/** Wanders around when she has nothing else to do. */
final class WanderGoal extends WaterAvoidingRandomStrollGoal {
    private final Maiden m;

    WanderGoal(Maiden m) {
        super(m, 0.5);
        this.m = m;
    }

    @Override
    public boolean canUse() {
        return !m.employment().isWorking() && super.canUse();
    }
}

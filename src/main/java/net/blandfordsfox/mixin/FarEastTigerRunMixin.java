package net.blandfordsfox.mixin;

import net.blandfordsfox.compat.FeTiger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Far East compatibility: the tiger runs at the speed the gallop is made for (1.3x). */
@Pseudo
@Mixin(targets = "com.chickendesigner.fareast.entity.TigerGoals$AttackGoal", remap = false)
public abstract class FarEastTigerRunMixin {
    @ModifyConstant(method = "tick()V", constant = @Constant(doubleValue = 2.1), remap = false, require = 0)
    private double blandfordsfox$runSpeed(double speed) {
        return FeTiger.RUN_SPEED;
    }
}

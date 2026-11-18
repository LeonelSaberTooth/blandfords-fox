package net.blandfordsfox.mixin;

import net.blandfordsfox.compat.FeTiger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Far East compatibility: the new STRETCH and roar are longer than Far East's, so those actions
 * last as long as their animations; and the tiger holds still through our own actions.
 */
@Pseudo
@Mixin(targets = "com.chickendesigner.fareast.entity.Tiger", remap = false)
public abstract class FarEastTigerMixin {
    @Shadow(remap = false)
    private int actionTicks;

    @Shadow(remap = false)
    public abstract int getAction();

    @Inject(method = "startAction(II)V", at = @At("TAIL"), remap = false, require = 0)
    private void blandfordsfox$actionLength(int action, int ticks, CallbackInfo ci) {
        if (action == FeTiger.STRETCH) this.actionTicks = FeTiger.STRETCH_TICKS;
        else if (action == FeTiger.ROAR) this.actionTicks = FeTiger.ROAR_TICKS;
    }

    @Inject(method = "isHoldingStill()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void blandfordsfox$holdForOurs(CallbackInfoReturnable<Boolean> cir) {
        if (FeTiger.ours(this.getAction())) cir.setReturnValue(true);
    }
}

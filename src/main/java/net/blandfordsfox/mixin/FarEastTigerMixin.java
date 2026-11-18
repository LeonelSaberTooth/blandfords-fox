package net.blandfordsfox.mixin;

import net.blandfordsfox.compat.FeTiger;
import net.blandfordsfox.compat.FeTigerAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Far East compatibility (applied only when Far East is installed): the new STRETCH and roar are
 * longer than Far East's, so those actions last as long as their animations; the tiger holds
 * still through our own actions; and the tiger can be reached through FeTigerAccess.
 */
@Pseudo
@Mixin(targets = "com.chickendesigner.fareast.entity.Tiger", remap = false)
public abstract class FarEastTigerMixin implements FeTigerAccess {
    @Shadow(remap = false)
    private int actionTicks;

    @Shadow(remap = false)
    private int activityTicks;

    @Shadow(remap = false)
    public abstract int getAction();

    @Shadow(remap = false)
    public abstract int getActivity();

    @Shadow(remap = false)
    public abstract int getMood();

    @Shadow(remap = false)
    abstract void startAction(int action, int ticks);

    @Shadow(remap = false)
    abstract boolean isIdle();

    @Shadow(remap = false)
    private void setActivity(int activity, int ticks) {
        throw new AssertionError();
    }

    @Inject(method = "startAction(II)V", at = @At("TAIL"), remap = false, require = 0)
    private void blandfordsfox$actionLength(int action, int ticks, CallbackInfo ci) {
        if (action == FeTiger.STRETCH) this.actionTicks = FeTiger.STRETCH_TICKS;
        else if (action == FeTiger.ROAR) this.actionTicks = FeTiger.ROAR_TICKS;
    }

    @Inject(method = "isHoldingStill()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void blandfordsfox$holdForOurs(CallbackInfoReturnable<Boolean> cir) {
        if (FeTiger.ours(this.getAction())) cir.setReturnValue(true);
    }

    @Override
    public void blandfordsfox$startAction(int action, int ticks) {
        this.startAction(action, ticks);
    }

    @Override
    public boolean blandfordsfox$isIdle() {
        return this.isIdle();
    }

    @Override
    public void blandfordsfox$setActivity(int activity, int ticks) {
        this.setActivity(activity, ticks);
    }

    @Override
    public int blandfordsfox$getAction() {
        return this.getAction();
    }

    @Override
    public int blandfordsfox$getActivity() {
        return this.getActivity();
    }

    @Override
    public int blandfordsfox$getMood() {
        return this.getMood();
    }

    @Override
    public int blandfordsfox$activityTicks() {
        return this.activityTicks;
    }
}

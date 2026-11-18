package net.blandfordsfox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Far East compatibility: reaches into Far East's tiger (only applied when Far East is installed). */
@Pseudo
@Mixin(targets = "com.chickendesigner.fareast.entity.Tiger", remap = false)
public interface FarEastTigerAccess {
    @Invoker(value = "startAction", remap = false)
    void blandfordsfox$startAction(int action, int ticks);

    @Invoker(value = "isIdle", remap = false)
    boolean blandfordsfox$isIdle();

    @Invoker(value = "setActivity", remap = false)
    void blandfordsfox$setActivity(int activity, int ticks);

    @Invoker(value = "getAction", remap = false)
    int blandfordsfox$getAction();

    @Invoker(value = "getActivity", remap = false)
    int blandfordsfox$getActivity();

    @Invoker(value = "getMood", remap = false)
    int blandfordsfox$getMood();

    @Accessor(value = "activityTicks", remap = false)
    int blandfordsfox$activityTicks();
}

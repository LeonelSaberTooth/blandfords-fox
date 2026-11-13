package net.blandfordsfox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Compatibility with Far East: no lichen grows on its trees or fallen logs. All of it is placed by
 * WinterTreeDecoration.decorate / decorateTop, whose first float is the lichen chance; it is set
 * to 0 (side moss is untouched). Pseudo: without Far East there is nothing to change and nothing loads.
 */
@Pseudo
@Mixin(targets = "com.chickendesigner.fareast.worldgen.WinterTreeDecoration", remap = false)
public abstract class FarEastNoLichenMixin {
    @ModifyVariable(method = {"decorate", "decorateTop"}, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0, remap = false)
    private static float blandfordsfox$noLichen(float lichenChance) {
        return 0F;
    }
}

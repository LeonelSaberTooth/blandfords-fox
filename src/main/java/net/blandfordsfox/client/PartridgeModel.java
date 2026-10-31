package net.blandfordsfox.client;

import net.blandfordsfox.BlandfordsFoxMod;
import net.blandfordsfox.entity.Partridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Loads geo/entity/partridge.geo.json and its animations. Walking is done here rather than
 * keyframed: the legs swing with the distance walked (not with time), so the planted foot
 * travels back exactly as fast as the bird goes forward, at any speed. The foot lifts on the
 * forward swing, the head bobs with each step.
 */
public class PartridgeModel extends DefaultedEntityGeoModel<Partridge> {
    private static final float LIFT = 0.8F;   // px the foot rises on the forward swing

    public PartridgeModel() {
        super(ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "partridge"), false);
    }

    @Override
    public void setCustomAnimations(Partridge bird, long instanceId, AnimationState<Partridge> state) {
        float pt = state.getPartialTick();
        GeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);

        if (!bird.isAirborneLook()) {
            float phase = bird.getStride(pt), amount = bird.getStepAmount(pt);
            leg("leg_right", phase, amount);
            leg("leg_left", phase + Mth.PI, amount);
            GeoBone body = getAnimationProcessor().getBone("body");
            if (body != null) body.setPosY(-0.3F * (0.5F - 0.5F * Mth.cos(2 * phase)) * amount);
            if (head != null) head.setRotX(head.getRotX() + 0.12F * Mth.sin(2 * phase) * amount);
        }
        // the head follows where it is looking (every animation keys the head, so add to it)
        if (head != null) {
            head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + data.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }

    /** Positive X rotation swings the foot forward; it lifts while swinging forward (cos > 0). */
    private void leg(String name, float phase, float amount) {
        GeoBone leg = getAnimationProcessor().getBone(name);
        if (leg == null) return;
        leg.setRotX(Partridge.LEG_SWING * Mth.sin(phase) * amount);
        leg.setPosY(LIFT * Math.max(0, Mth.cos(phase)) * amount);
    }
}

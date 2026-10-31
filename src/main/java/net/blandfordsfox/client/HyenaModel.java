package net.blandfordsfox.client;

import net.blandfordsfox.BlandfordsFoxMod;
import net.blandfordsfox.entity.Hyena;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Loads geo/entity/hyena.geo.json, its animations and textures. */
public class HyenaModel extends DefaultedEntityGeoModel<Hyena> {
    private static final ResourceLocation SLEEPING =
            ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "textures/entity/hyena_sleeping.png");

    public HyenaModel() {
        super(ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "hyena"), false);
    }

    /** Eyes closed while asleep. */
    @Override
    public ResourceLocation getTextureResource(Hyena hyena) {
        return hyena.isHyenaSleeping() ? SLEEPING : super.getTextureResource(hyena);
    }

    @Override
    public void setCustomAnimations(Hyena hyena, long instanceId, AnimationState<Hyena> state) {
        // the model's paws sit one pixel above its origin; lower it onto the ground
        GeoBone root = getAnimationProcessor().getBone("hyena");
        if (root != null) root.setPosY(-1);

        GeoBone head = getAnimationProcessor().getBone("head");
        if (head == null) return;
        if (!hyena.isHyenaSleeping()) {
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + data.netHeadYaw() * Mth.DEG_TO_RAD);
        }
        head.setRotZ(head.getRotZ() + hyena.getHeadRollAngle(state.getPartialTick()));   // curious head tilt, like a wolf
    }
}

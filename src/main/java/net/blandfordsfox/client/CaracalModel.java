package net.blandfordsfox.client;

import net.blandfordsfox.BlandfordsFoxMod;
import net.blandfordsfox.entity.Caracal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Loads geo/entity/caracal.geo.json, its animations and texture. */
public class CaracalModel extends DefaultedEntityGeoModel<Caracal> {
    public CaracalModel() {
        this("caracal");
    }

    /** "caracal" or "lynx": geo/entity/NAME.geo.json, animations and texture. */
    public CaracalModel(String name) {
        super(ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, name), false);
    }

    /** The head follows where the caracal is looking, on top of the current animation. */
    @Override
    public void setCustomAnimations(Caracal caracal, long instanceId, AnimationState<Caracal> state) {
        GeoBone head = getAnimationProcessor().getBone("head");
        if (head == null) return;
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + data.netHeadYaw() * Mth.DEG_TO_RAD);
    }
}

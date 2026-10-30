package net.blandfordsfox.client;

import net.blandfordsfox.BlandfordsFoxMod;
import net.blandfordsfox.entity.BlandfordsFox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Loads geo/entity/blandfords_fox.geo.json, its animations and texture. */
public class BlandfordsFoxModel extends DefaultedEntityGeoModel<BlandfordsFox> {
    private static final ResourceLocation SLEEPING =
            ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "textures/entity/blandfords_fox_sleeping.png");

    public BlandfordsFoxModel() {
        super(ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "blandfords_fox"), false);
    }

    /** Eyes closed while asleep. */
    @Override
    public ResourceLocation getTextureResource(BlandfordsFox fox) {
        return fox.isSleeping() ? SLEEPING : super.getTextureResource(fox);
    }

    /**
     * Like the vanilla fox model: the head follows where the fox is looking, and tilts
     * sideways when it's curious (watching a player hold food, eyeing prey).
     * Added on top of the current animation so walk/run head bob still shows.
     */
    @Override
    public void setCustomAnimations(BlandfordsFox fox, long instanceId, AnimationState<BlandfordsFox> state) {
        GeoBone head = getAnimationProcessor().getBone("head");
        if (head == null) return;

        if (!fox.isSleeping() && !fox.isPouncing() && !fox.isFaceplanted()) {
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + data.netHeadYaw() * Mth.DEG_TO_RAD);
        }
        head.setRotZ(head.getRotZ() + fox.getHeadRollAngle(state.getPartialTick()));
    }
}

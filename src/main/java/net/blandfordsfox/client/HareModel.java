package net.blandfordsfox.client;

import net.blandfordsfox.BlandfordsFoxMod;
import net.blandfordsfox.entity.Hare;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Loads geo/entity/hare.geo.json and its animations; one texture per coat. */
public class HareModel extends DefaultedEntityGeoModel<Hare> {
    private static final ResourceLocation DESERT =
            ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "textures/entity/hare_desert.png");
    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "textures/entity/hare_white.png");

    public HareModel() {
        super(ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "hare"), false);
    }

    @Override
    public ResourceLocation getTextureResource(Hare hare) {
        return hare.getCoat() == Hare.Coat.WHITE ? WHITE : DESERT;
    }

    @Override
    public void setCustomAnimations(Hare hare, long instanceId, AnimationState<Hare> state) {
        // the head and ears have a shape for each coat
        boolean white = hare.getCoat() == Hare.Coat.WHITE;
        for (String part : new String[] {"head", "ear_left", "ear_right"}) {
            GeoBone w = getAnimationProcessor().getBone(part + "_white");
            GeoBone d = getAnimationProcessor().getBone(part + "_desert");
            if (w != null) w.setHidden(!white);
            if (d != null) d.setHidden(white);
        }
        // the head follows where the hare is looking
        GeoBone head = getAnimationProcessor().getBone("head");
        if (head == null) return;
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + data.netHeadYaw() * Mth.DEG_TO_RAD);
    }
}

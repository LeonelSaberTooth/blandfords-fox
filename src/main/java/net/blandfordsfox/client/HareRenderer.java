package net.blandfordsfox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.blandfordsfox.entity.Hare;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class HareRenderer extends GeoEntityRenderer<Hare> {
    public HareRenderer(EntityRendererProvider.Context context) {
        super(context, new HareModel());
        this.shadowRadius = 0.3F;
    }

    /** Leverets are drawn at half size, like vanilla. */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, Hare hare,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        if (!isReRender && hare.isBaby()) {
            widthScale *= 0.5F;
            heightScale *= 0.5F;
        }
        super.scaleModelForRender(widthScale, heightScale, poseStack, hare, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}

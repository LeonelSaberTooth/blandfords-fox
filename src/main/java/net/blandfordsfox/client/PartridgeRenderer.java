package net.blandfordsfox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.blandfordsfox.entity.Partridge;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class PartridgeRenderer extends GeoEntityRenderer<Partridge> {
    public PartridgeRenderer(EntityRendererProvider.Context context) {
        super(context, new PartridgeModel());
        this.shadowRadius = 0.25F;
    }

    /** Chicks are drawn at half size, like vanilla. */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, Partridge bird,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        if (!isReRender && bird.isBaby()) {
            widthScale *= 0.5F;
            heightScale *= 0.5F;
        }
        super.scaleModelForRender(widthScale, heightScale, poseStack, bird, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}

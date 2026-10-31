package net.blandfordsfox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.blandfordsfox.entity.Hyena;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class HyenaRenderer extends GeoEntityRenderer<Hyena> {
    public HyenaRenderer(EntityRendererProvider.Context context) {
        super(context, new HyenaModel());
        this.shadowRadius = 0.55F;
    }

    /** Cubs are drawn at half size, like vanilla. */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, Hyena hyena,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        if (!isReRender && hyena.isBaby()) {
            widthScale *= 0.5F;
            heightScale *= 0.5F;
        }
        super.scaleModelForRender(widthScale, heightScale, poseStack, hyena, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}

package net.blandfordsfox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.blandfordsfox.entity.Caracal;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CaracalRenderer extends GeoEntityRenderer<Caracal> {
    public CaracalRenderer(EntityRendererProvider.Context context) {
        this(context, "caracal", 0.4F);
    }

    /** Also draws the lynx (a caracal underneath) with its own model. */
    public CaracalRenderer(EntityRendererProvider.Context context, String name, float shadow) {
        super(context, new CaracalModel(name));
        this.shadowRadius = shadow;
    }

    /** Kittens are drawn at half size, like vanilla. */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, Caracal caracal,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        if (!isReRender && caracal.isBaby()) {
            widthScale *= 0.5F;
            heightScale *= 0.5F;
        }
        super.scaleModelForRender(widthScale, heightScale, poseStack, caracal, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}

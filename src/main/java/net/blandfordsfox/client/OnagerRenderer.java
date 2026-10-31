package net.blandfordsfox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.blandfordsfox.BlandfordsFoxMod;
import net.blandfordsfox.entity.Onager;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class OnagerRenderer extends MobRenderer<Onager, OnagerModel> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "onager"), "main");
    public static final ModelLayerLocation TACK_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "onager"), "tack");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "textures/entity/onager.png");
    private static final ResourceLocation DONKEY = ResourceLocation.withDefaultNamespace("textures/entity/horse/donkey.png");

    /** Same size as a donkey. */
    private static final float SCALE = 0.87F;

    public OnagerRenderer(EntityRendererProvider.Context context) {
        super(context, new OnagerModel(context.bakeLayer(LAYER), false), 0.75F * SCALE);
        this.addLayer(new Tack(this, new OnagerModel(context.bakeLayer(TACK_LAYER), true)));
    }

    @Override
    protected void scale(Onager onager, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
        super.scale(onager, poseStack, partialTick);
    }

    @Override
    public ResourceLocation getTextureLocation(Onager onager) {
        return TEXTURE;
    }

    /** Saddle, bridle and chests from the vanilla donkey texture. */
    static final class Tack extends RenderLayer<Onager, OnagerModel> {
        private final OnagerModel model;

        Tack(RenderLayerParent<Onager, OnagerModel> parent, OnagerModel model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Onager onager, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!onager.isSaddled() && !onager.hasChest()) return;
            getParentModel().copyPropertiesTo(model);
            model.prepareMobModel(onager, limbSwing, limbSwingAmount, partialTick);
            model.setupAnim(onager, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(DONKEY));
            model.renderToBuffer(poseStack, vc, light, LivingEntityRenderer.getOverlayCoords(onager, 0.0F));
        }
    }
}

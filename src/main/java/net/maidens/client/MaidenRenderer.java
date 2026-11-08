package net.maidens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.maidens.MaidensMod;
import net.maidens.entity.Maiden;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class MaidenRenderer extends HumanoidMobRenderer<Maiden, MaidenModel> {
    /** The model is 35.5 px tall; this makes her 1.95 blocks, the height of her hitbox, so she fits through doors. */
    public static final float SCALE = 1.95F * 16.0F / 35.5F;

    private static final ResourceLocation[] SKINS = new ResourceLocation[Maiden.SKINS];
    static {
        for (int i = 0; i < SKINS.length; i++) {
            SKINS[i] = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "textures/entity/maiden/maiden_" + (i + 1) + ".png");
        }
    }

    public MaidenRenderer(EntityRendererProvider.Context context) {
        super(context, new MaidenModel(context.bakeLayer(MaidenModel.LAYER)), 0.45F);
    }

    @Override
    public ResourceLocation getTextureLocation(Maiden maiden) {
        return SKINS[maiden.getSkin()];
    }

    @Override
    protected void scale(Maiden maiden, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}

package net.maidens.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.maidens.MaidensMod;
import net.maidens.maiden.Maiden;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class MaidenRenderer extends HumanoidMobRenderer<Maiden, MaidenModel> {
    /** The model is 36.5 px tall; this makes her 1.95 blocks, the height of her hitbox, so she fits through doors. */
    public static final float SCALE = 1.95F * 16.0F / 36.5F;
    /** Children: the same body, this much smaller (their hitbox is vanilla's half). */
    public static final float CHILD = 0.55F;

    private static final ResourceLocation[] SKINS = textures("maiden_"), CASUAL = textures("casual_"), CHILDREN = textures("child_");

    private static ResourceLocation[] textures(String prefix) {
        ResourceLocation[] out = new ResourceLocation[Maiden.SKINS];
        for (int i = 0; i < out.length; i++) {
            out[i] = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "textures/entity/maiden/" + prefix + (i + 1) + ".png");
        }
        return out;
    }

    /** Her usual body, and her body at home in casual clothes. */
    private final MaidenModel usual, casual;

    public MaidenRenderer(EntityRendererProvider.Context context) {
        super(context, new MaidenModel(context.bakeLayer(MaidenModel.LAYER)), 0.45F);
        usual = model;
        casual = new MaidenModel(context.bakeLayer(MaidenModel.CASUAL));
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(MaidenArmorGeometry.INNER)),
                new HumanoidModel<>(context.bakeLayer(MaidenArmorGeometry.OUTER)), context.getModelManager()));
        addLayer(new CrownLayer(this, context.getModelSet()));
        addLayer(new JobHatLayer(this, context.getModelSet()));
    }

    @Override
    public void render(Maiden maiden, float yaw, float partialTick, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        model = maiden.isCasual() && !maiden.isBaby() ? casual : usual;
        super.render(maiden, yaw, partialTick, pose, buffers, light);
    }

    /** Her name only shows in her menu, never above her head. */
    @Override
    protected boolean shouldShowName(Maiden maiden) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(Maiden maiden) {
        int skin = maiden.getSkin();
        if (maiden.isBaby()) return CHILDREN[skin];
        return maiden.isCasual() ? CASUAL[skin] : SKINS[skin];
    }

    @Override
    protected void scale(Maiden maiden, PoseStack poseStack, float partialTick) {
        float s = maiden.isBaby() ? SCALE * CHILD : SCALE;
        poseStack.scale(s, s, s);
    }
}

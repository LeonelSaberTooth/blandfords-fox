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
    /** The model is 36.5 px tall; this makes her 1.8 blocks, as tall as a player (and her hitbox). */
    public static final float SCALE = 1.8F * 16.0F / 36.5F;
    /** Children: the same body, half the size (as their hitbox). */
    public static final float CHILD = 0.5F;



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
        LayeredSkin.Body body = maiden.isBaby() ? LayeredSkin.Body.CHILD : maiden.isCasual() ? LayeredSkin.Body.CASUAL : LayeredSkin.Body.USUAL;
        String job = body == LayeredSkin.Body.USUAL ? maiden.hat() : "";
        String type = "plains";
        if (!job.isEmpty()) {
            // a villager's clothes for where she is (a desert robe in the desert...)
            var t = net.minecraft.world.entity.npc.VillagerType.byBiome(maiden.level().getBiome(maiden.blockPosition()));
            type = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.getKey(t).getPath();
        }
        return LayeredSkin.get(body, maiden.getSkin(), maiden.outfit(), maiden.eyes(), maiden.hair(), job, type);
    }

    @Override
    protected void scale(Maiden maiden, PoseStack poseStack, float partialTick) {
        float s = maiden.isBaby() ? SCALE * CHILD : SCALE;
        poseStack.scale(s, s, s);
    }
}

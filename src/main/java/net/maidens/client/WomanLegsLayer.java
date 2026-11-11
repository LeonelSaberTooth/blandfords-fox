package net.maidens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.maidens.maiden.Maiden;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** A grown woman's straight outdoor legs under her dress (outside and at work; at home she's in swimwear). */
public class WomanLegsLayer extends RenderLayer<Maiden, MaidenModel> {
    public WomanLegsLayer(RenderLayerParent<Maiden, MaidenModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Maiden maiden, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(getParentModel() instanceof WomanModel woman) || !maiden.isWoman() || maiden.isInvisible()) return;
        WomanSkin.Wear wear = MaidenRenderer.wear(maiden);
        if (wear == WomanSkin.Wear.SWIM) return;
        var tex = WomanSkin.legs(maiden.getSkin(), maiden.outfit(), wear == WomanSkin.Wear.WORK ? maiden.hat() : "");
        woman.renderDressLegs(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light,
                LivingEntityRenderer.getOverlayCoords(maiden, 0.0F), -1);
    }
}

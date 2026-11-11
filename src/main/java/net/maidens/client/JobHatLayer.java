package net.maidens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.maidens.maiden.Maiden;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

/**
 * The hat of her job while she works: the vanilla villager's head and hat, drawn with that profession's texture
 * (clear except for the hat), on her head. The villager head is 10 tall to her 8, so it sits 2 lower to put the
 * hat right on top of her head.
 */
public class JobHatLayer extends RenderLayer<Maiden, MaidenModel> {
    private final ModelPart head;
    private final Map<String, ResourceLocation> textures = new HashMap<>();

    public JobHatLayer(RenderLayerParent<Maiden, MaidenModel> parent, EntityModelSet models) {
        super(parent);
        head = models.bakeLayer(ModelLayers.VILLAGER).getChild("head");
        if (head.hasChild("nose")) head.getChild("nose").visible = false;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Maiden maiden, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        String hat = maiden.hat();
        if (hat.isEmpty() || maiden.isInvisible() || !maiden.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        boolean woman = maiden.isWoman();
        if (woman && maiden.isCasual()) return;
        // a grown woman's: only the trade's own pixels (glasses and the like a pixel higher, on her eyes), and the
        // head a little bigger than hers, so the trade's things show over her hair
        ResourceLocation tex = woman ? WomanSkin.hat(hat)
                : textures.computeIfAbsent(hat, h -> ResourceLocation.withDefaultNamespace("textures/entity/villager/profession/" + h + ".png"));
        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 2.0F / 16.0F, 0.0F);
        if (woman) {
            float s = 8.3F / 8.0F;                          // 0.15 out on each side, past her hair (0.1)
            poseStack.translate(0.0F, -5.0F / 16.0F, 0.0F);
            poseStack.scale(s, s, s);
            poseStack.translate(0.0F, 5.0F / 16.0F, 0.0F);
        }
        head.xRot = head.yRot = head.zRot = 0.0F;
        head.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}

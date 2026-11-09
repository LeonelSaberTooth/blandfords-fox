package net.maidens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.maidens.MaidensMod;
import net.maidens.maiden.Maiden;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** A royal's gold crown, on her head (hidden under a helmet). */
public class CrownLayer extends RenderLayer<Maiden, MaidenModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "maiden"), "crown");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "textures/entity/maiden/crown.png");
    private final ModelPart crown;

    public CrownLayer(RenderLayerParent<Maiden, MaidenModel> parent, EntityModelSet models) {
        super(parent);
        crown = models.bakeLayer(LAYER).getChild("crown");
    }

    /** A gold band round the top of her head (8 wide, 7 deep, like it) with points and a red gem at the front. */
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder c = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -9.5F, -4.0F, 9F, 2F, 1F)       // front
                .texOffs(0, 3).addBox(-4.5F, -9.5F, 3.0F, 9F, 2F, 1F)        // back
                .texOffs(0, 6).addBox(-4.5F, -9.5F, -3.0F, 1F, 2F, 6F)       // left
                .texOffs(14, 6).addBox(3.5F, -9.5F, -3.0F, 1F, 2F, 6F);      // right
        // points: the corners and the middle of each side
        float[][] points = {{-4.5F, -4.0F}, {3.5F, -4.0F}, {-4.5F, 3.0F}, {3.5F, 3.0F}, {-0.5F, -4.0F}, {-0.5F, 3.0F}, {-4.5F, -0.5F}, {3.5F, -0.5F}};
        for (float[] p : points) c.texOffs(20, 0).addBox(p[0], -10.5F, p[1], 1F, 1F, 1F);
        c.texOffs(24, 0).addBox(-1.0F, -9.2F, -4.3F, 2F, 1F, 1F);            // the gem
        root.addOrReplaceChild("crown", c, PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Maiden maiden, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!maiden.isRoyal() || maiden.isInvisible() || !maiden.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        crown.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}

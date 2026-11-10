package net.maidens.client;

import net.maidens.MaidensMod;
import net.maidens.maiden.Maiden;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The maiden's body, driven by the vanilla humanoid code (walking, looking, holding and swinging items),
 * with three changes: her own pivots are put back after vanilla moves the parts to the player's; the walk
 * swings less than vanilla's (legs up to 24 degrees, arms 17) with slightly quicker steps, so her feet keep
 * up with the ground without her legs flailing; and she looks up
 * less far than down, so her long hair doesn't swing out in front of her.
 */
public class MaidenModel extends HumanoidModel<Maiden> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "maiden"), "main");

    private static final float LEG = 24.0F * Mth.DEG_TO_RAD, ARM = 17.0F * Mth.DEG_TO_RAD;
    /** Steps per distance walked, relative to vanilla (smaller steps need a few more of them). */
    private static final float CADENCE = 1.25F;
    /** Looking up is scaled down by this, and stops at MAX_UP degrees. */
    private static final float UP_SCALE = 0.4F, MAX_UP = 20.0F;

    /** Her chest piece (hidden on men and children). */
    private final ModelPart breast;
    /** Colour over the head while rendering (placeholder looks: blue for men, dark red / blue for the old), or -1. */
    private int headTint = -1;

    public MaidenModel(ModelPart root) {
        super(root);
        breast = body.hasChild("breast_cube") ? body.getChild("breast_cube") : null;
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay, int color) {
        // no baby proportions (children are the same body, shrunk by the renderer); the head tinted for the placeholders
        int head = headTint == -1 ? color : net.minecraft.util.FastColor.ARGB32.multiply(color, headTint);
        for (ModelPart p : headParts()) p.render(pose, buffer, light, overlay, head);
        for (ModelPart p : bodyParts()) p.render(pose, buffer, light, overlay, color);
    }

    @Override
    public void setupAnim(Maiden maiden, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        Maiden.Kind kind = maiden.kind();
        headTint = switch (kind) {
            case MAN -> 0xFF5070F0;
            case OLD_WOMAN -> 0xFF8A2020;
            case OLD_MAN -> 0xFF203070;
            default -> -1;
        };
        if (breast != null) breast.visible = kind == Maiden.Kind.WOMAN && !maiden.isBaby() || kind == Maiden.Kind.OLD_WOMAN;
        young = false;
        float pitch = headPitch < 0 ? Math.max(headPitch * UP_SCALE, -MAX_UP) : headPitch;
        super.setupAnim(maiden, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, pitch);
        put(head, MaidenGeometry.HEAD);
        put(hat, MaidenGeometry.HEAD);
        put(body, MaidenGeometry.BODY);
        put(rightArm, MaidenGeometry.RIGHT_ARM);
        put(leftArm, MaidenGeometry.LEFT_ARM);
        put(rightLeg, MaidenGeometry.RIGHT_LEG);
        put(leftLeg, MaidenGeometry.LEFT_LEG);
        float c = Mth.cos(limbSwing * 0.6662F * CADENCE);
        float k = Math.min(1.0F, limbSwingAmount * 1.6F);
        rightLeg.xRot = c * LEG * k;
        leftLeg.xRot = -c * LEG * k;
        rightLeg.yRot = leftLeg.yRot = rightLeg.zRot = leftLeg.zRot = 0.0F;
        // swap vanilla's arm swing (cos * amount) for the smaller one, keeping what holding and attacking added
        float v = Mth.cos(limbSwing * 0.6662F);     // vanilla's own phase, to take its swing back out
        rightArm.xRot += v * limbSwingAmount - c * ARM * k;
        leftArm.xRot += -v * limbSwingAmount + c * ARM * k;
    }

    private static void put(ModelPart part, float[] pivot) {
        part.x = pivot[0];
        part.y = pivot[1];
        part.z = pivot[2];
    }
}

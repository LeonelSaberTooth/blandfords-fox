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
 * swings less (legs up to 14 degrees, arms 10), so a leg's top never dips below her hips; and she looks up
 * less far than down, so her long hair doesn't swing out in front of her.
 */
public class MaidenModel extends HumanoidModel<Maiden> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "maiden"), "main");

    private static final float LEG = 14.0F * Mth.DEG_TO_RAD, ARM = 10.0F * Mth.DEG_TO_RAD;
    /** Looking up is scaled down by this, and stops at MAX_UP degrees. */
    private static final float UP_SCALE = 0.4F, MAX_UP = 20.0F;

    public MaidenModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(Maiden maiden, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float pitch = headPitch < 0 ? Math.max(headPitch * UP_SCALE, -MAX_UP) : headPitch;
        super.setupAnim(maiden, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, pitch);
        put(head, MaidenGeometry.HEAD);
        put(hat, MaidenGeometry.HEAD);
        put(body, MaidenGeometry.BODY);
        put(rightArm, MaidenGeometry.RIGHT_ARM);
        put(leftArm, MaidenGeometry.LEFT_ARM);
        put(rightLeg, MaidenGeometry.RIGHT_LEG);
        put(leftLeg, MaidenGeometry.LEFT_LEG);
        float c = Mth.cos(limbSwing * 0.6662F);
        float k = Math.min(1.0F, limbSwingAmount * 1.6F);
        rightLeg.xRot = c * LEG * k;
        leftLeg.xRot = -c * LEG * k;
        rightLeg.yRot = leftLeg.yRot = rightLeg.zRot = leftLeg.zRot = 0.0F;
        // swap vanilla's arm swing (cos * amount) for the smaller one, keeping what holding and attacking added
        rightArm.xRot += c * limbSwingAmount - c * ARM * k;
        leftArm.xRot += -c * limbSwingAmount + c * ARM * k;
    }

    private static void put(ModelPart part, float[] pivot) {
        part.x = pivot[0];
        part.y = pivot[1];
        part.z = pivot[2];
    }
}

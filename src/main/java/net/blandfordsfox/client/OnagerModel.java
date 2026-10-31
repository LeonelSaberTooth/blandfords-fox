package net.blandfordsfox.client;

import net.blandfordsfox.entity.Onager;
import net.minecraft.client.model.ChestedHorseModel;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The vanilla donkey model with the onager's ears and tail (art/onager.bbmodel), so every
 * vanilla horse animation (grazing, rearing, kicking, tail swish, foals) works as is.
 *
 * <p>The onager texture has no saddle or chest on it, so the model is used twice: once for the
 * animal itself (tack hidden), and once with the vanilla donkey texture drawing only the
 * saddle, bridle and chests.
 */
public class OnagerModel extends ChestedHorseModel<Onager> {
    /** Saddle, bridle, reins and chests: drawn from the vanilla donkey texture. */
    static final Set<String> TACK = Set.of("saddle", "left_saddle_mouth", "right_saddle_mouth", "left_saddle_line",
            "right_saddle_line", "head_saddle", "mouth_saddle_wrap", "left_chest", "right_chest");

    private static final float TAIL_REST = 10 * Mth.DEG_TO_RAD;

    private final boolean tackOnly;
    private final ModelPart tail;
    private final List<ModelPart> tack = new ArrayList<>();
    private final List<ModelPart> rest = new ArrayList<>();

    public OnagerModel(ModelPart root, boolean tackOnly) {
        super(root);
        this.tackOnly = tackOnly;
        this.tail = root.getChild("body").getChild("tail");
        sort(root, "root");
    }

    private void sort(ModelPart part, String name) {
        if (!name.equals("root")) (TACK.contains(name) ? tack : rest).add(part);
        for (String child : childNames(part)) sort(part.getChild(child), child);
    }

    private static List<String> childNames(ModelPart part) {
        List<String> names = new ArrayList<>();
        part.getAllParts().forEach(p -> { });   // (keeps the API surface the same across versions)
        for (String n : ALL_PARTS) if (part.hasChild(n)) names.add(n);
        return names;
    }

    /** Every part name in the vanilla horse/donkey mesh. */
    private static final String[] ALL_PARTS = {
            "body", "head_parts", "head", "mane", "upper_mouth", "left_ear", "right_ear", "tail",
            "left_hind_leg", "right_hind_leg", "left_front_leg", "right_front_leg",
            "left_hind_baby_leg", "right_hind_baby_leg", "left_front_baby_leg", "right_front_baby_leg",
            "saddle", "left_saddle_mouth", "right_saddle_mouth", "left_saddle_line", "right_saddle_line",
            "head_saddle", "mouth_saddle_wrap", "left_chest", "right_chest"};

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HorseModel.createBodyMesh(CubeDeformation.NONE);
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.getChild("body");
        CubeListBuilder chest = CubeListBuilder.create().texOffs(26, 21).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 8.0F, 3.0F);
        body.addOrReplaceChild("left_chest", chest, PartPose.offsetAndRotation(6.0F, -8.0F, 0.0F, 0.0F, -Mth.HALF_PI, 0.0F));
        body.addOrReplaceChild("right_chest", chest, PartPose.offsetAndRotation(-6.0F, -8.0F, 0.0F, 0.0F, Mth.HALF_PI, 0.0F));
        // thin tail, hanging from the rump
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(42, 36).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 12.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -6.5F, 5.5F, TAIL_REST, 0.0F, 0.0F));
        // short upright ears
        PartDefinition head = root.getChild("head_parts").getChild("head");
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(8, 4).addBox(-0.25F, -5.0F, 0.0F, 2.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(1.25F, -10.0F, 4.0F, -30 * Mth.DEG_TO_RAD, 0.0F, 8 * Mth.DEG_TO_RAD));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 12).addBox(-1.0F, -5.0F, 0.0F, 2.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(-2.0F, -10.0F, 4.0F, -30 * Mth.DEG_TO_RAD, 0.0F, -8 * Mth.DEG_TO_RAD));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Onager onager, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(onager, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // vanilla swings its own, thicker tail from a point inside the body; use this one's
        this.tail.x = 0.0F;
        this.tail.y = -6.5F + limbSwingAmount * 0.5F;
        this.tail.z = 5.5F;
        this.tail.xRot = TAIL_REST + limbSwingAmount * 0.75F;
        if (tackOnly) {
            for (ModelPart p : rest) p.skipDraw = true;
        } else {
            for (ModelPart p : tack) p.visible = false;
        }
    }
}

package net.maidens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.maidens.MaidensMod;
import net.maidens.maiden.Maiden;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * A grown woman's body (the base model of the model viewer), drawn from {@link WomanMesh}'s quads bone by bone. The
 * game's humanoid parts (head, body, arms, legs) still carry her pose, so armour, what she holds, her crown and her
 * job's hat follow her as before.
 *
 * <p>Usually she moves as every maiden does (MaidenModel): her chest, waist and hips go with the body part. A maiden
 * named with a "model" name tag has the proportions saved in the viewer and walks the runway walk made for it: her
 * hips roll up over the standing leg and turn with the leg in front, her waist and chest turn back against them so her
 * shoulders stay level, her arms swing gently, her chest piece lags her rise and fall, and her height comes from
 * keeping the lower foot on the ground. Her bones there are the viewer's (root, hips carrying the legs and waist,
 * waist the torso, torso the head, arms and chest piece), and the game's parts are put where her bones are.
 */
public class WomanModel extends MaidenModel {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "maiden"), "woman");

    /** The game's parts, empty (her body is drawn from the mesh), at her pivots. */
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0F, -4.5F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offset(0F, -4.5F, 0F));
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0F, -3.5F, 0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-4.5F, -2.5F, 0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(4.5F, -2.5F, 0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-2F, 9F, 0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(2F, 9F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private WomanMesh mesh = WomanMesh.of(WomanMesh.Body.DEFAULT);
    /** Each bone: from its own space (relative to its pivot) to the model's, in model units. */
    private final Map<String, Matrix4f> bones = new HashMap<>();
    /** The lowest point of her soles standing still (model units; larger is lower), per mesh. */
    private final Map<WomanMesh, Float> rest = new HashMap<>();

    public WomanModel(ModelPart root) {
        super(root);
    }

    // ------------------------------------------------------------------ pose

    @Override
    public void setupAnim(Maiden maiden, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean runway = maiden.isModelPose();
        mesh = WomanMesh.of(runway ? WomanMesh.Body.MODEL : WomanMesh.Body.DEFAULT);
        super.setupAnim(maiden, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (runway) runway(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        else fromParts();
    }

    /** Her bones from the game's parts: chest, waist and hips with the body. */
    private void fromParts() {
        put("head", head, mesh.pivot.get("head"));
        put("right_arm", rightArm, mesh.pivot.get("right_arm"));
        put("left_arm", leftArm, mesh.pivot.get("left_arm"));
        put("right_leg", rightLeg, mesh.pivot.get("right_leg"));
        put("left_leg", leftLeg, mesh.pivot.get("left_leg"));
        for (String b : new String[]{"hips", "waist", "torso", "chest_piece"}) put(b, body, mesh.bodyPivot);
        bones.put("root", new Matrix4f().translate(0, 24, 0));
    }

    private void put(String bone, ModelPart part, float[] partPivot) {
        float[] p = mesh.pivot.get(bone);
        Matrix4f m = new Matrix4f().translate(part.x, part.y, part.z).rotateZYX(part.zRot, part.yRot, part.xRot);
        // the bone's own pivot, relative to where the part's is on her body
        bones.put(bone, m.translate(p[0] - partPivot[0], p[1] - partPivot[1], p[2] - partPivot[2]));
    }

    private void runway(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float t = (float) (limbSwing * 0.6662F * 1.25F / (2 * Math.PI));
        t -= (float) Math.floor(t);
        float k = Math.min(1.0F, limbSwingAmount * 1.6F);
        Map<String, float[]> pose = Runway.pose(t, mesh.body, k);
        float pitch = headPitch < 0 ? Math.max(headPitch * 0.4F, -20.0F) : headPitch;
        float[] look = pose.get("head");
        pose.put("head", new float[]{look[0] - pitch, look[1] - netHeadYaw, look[2]});   // in the viewer's turning
        // her idle arm sway, as every maiden's
        float swayZ = (Mth.cos(ageInTicks * 0.09F) * 0.05F + 0.05F) * 0.5F * Mth.RAD_TO_DEG, swayX = Mth.sin(ageInTicks * 0.067F) * 0.05F * 0.5F * Mth.RAD_TO_DEG;
        float[] ra = pose.get("right_arm"), la = pose.get("left_arm");
        pose.put("right_arm", new float[]{ra[0] + swayX, ra[1], ra[2] - swayZ});
        pose.put("left_arm", new float[]{la[0] - swayX, la[1], la[2] + swayZ});
        float[] lift = pose.remove("_lift");
        // her soles' lowest point standing still, then the walking pose moved up or down to put the lower foot there
        float still = rest.computeIfAbsent(mesh, m -> {
            layOut(Runway.still(), new float[2], 0);
            return lowest();
        });
        layOut(pose, lift, 0);
        layOut(pose, lift, still - lowest());
        // the game's parts where her bones are (for armour, held items, her crown and hat)
        toPart(head, bones.get("head"));
        toPart(rightArm, bones.get("right_arm"));
        toPart(leftArm, bones.get("left_arm"));
        toPart(rightLeg, bones.get("right_leg"));
        toPart(leftLeg, bones.get("left_leg"));
        float[] tp = mesh.pivot.get("torso");
        toPart(body, new Matrix4f(bones.get("torso")).translate(mesh.bodyPivot[0] - tp[0], mesh.bodyPivot[1] - tp[1], mesh.bodyPivot[2] - tp[2]));
        hat.copyFrom(head);
    }

    /** Her bones in a pose (the viewer's degrees per bone), legs drawn up by lift, the whole of her down by drop. */
    private void layOut(Map<String, float[]> pose, float[] lift, float drop) {
        for (String b : WomanMesh.BONES) {
            float[] p = mesh.pivot.get(b);
            String parent = WomanMesh.PARENT.get(b);
            Matrix4f m;
            if (parent == null) m = new Matrix4f().translate(0, 24 + drop, 0);
            else {
                float[] pp = mesh.pivot.get(parent);
                m = new Matrix4f(bones.get(parent)).translate(p[0] - pp[0], p[1] - pp[1], p[2] - pp[2]);
            }
            if (b.equals("right_leg")) m.translate(0, -lift[0], 0);
            if (b.equals("left_leg")) m.translate(0, -lift[1], 0);
            float[] r = pose.get(b);
            // the viewer turns x and y the other way round from the game
            if (r != null) m.rotateZYX(r[2] * Mth.DEG_TO_RAD, -r[1] * Mth.DEG_TO_RAD, -r[0] * Mth.DEG_TO_RAD);
            bones.put(b, m);
        }
    }

    /** The lowest point of her soles (model units: larger is lower). */
    private float lowest() {
        float low = -Float.MAX_VALUE;
        Vector3f v = new Vector3f();
        for (var e : mesh.soles.entrySet()) {
            Matrix4f m = bones.get(e.getKey());
            for (float[] c : e.getValue()) low = Math.max(low, m.transformPosition(c[0], c[1], c[2], v).y);
        }
        return low;
    }

    private static void toPart(ModelPart part, Matrix4f m) {
        Vector3f t = m.getTranslation(new Vector3f()), r = m.getEulerAnglesZYX(new Vector3f());
        part.x = t.x;
        part.y = t.y;
        part.z = t.z;
        part.xRot = r.x;
        part.yRot = r.y;
        part.zRot = r.z;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        draw(pose, buffer, light, overlay, color, mesh.quads);
    }

    /** The straight outdoor legs she wears under a dress (their own texture). */
    public void renderDressLegs(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        draw(pose, buffer, light, overlay, color, mesh.dressQuads);
    }

    private void draw(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color, Map<String, float[]> quads) {
        Matrix4f base = new Matrix4f(pose.last().pose()).scale(1 / 16F);
        Matrix3f normals = pose.last().normal();
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (var e : quads.entrySet()) {
            Matrix4f bone = bones.get(e.getKey());
            if (bone == null) continue;
            Matrix4f m = new Matrix4f(base).mul(bone);
            Matrix3f nm = new Matrix3f(normals).mul(new Matrix3f(bone));
            float[] q = e.getValue();
            for (int i = 0; i < q.length; i += 23) {
                nm.transform(q[i + 20], q[i + 21], q[i + 22], n);
                for (int k = 0; k < 4; k++) {
                    int o = i + k * 5;
                    m.transformPosition(q[o], q[o + 1], q[o + 2], p);
                    buffer.addVertex(p.x, p.y, p.z, color, q[o + 3], q[o + 4], overlay, light, n.x, n.y, n.z);
                }
            }
        }
    }
}

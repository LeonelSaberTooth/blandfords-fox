package net.maidens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.maidens.MaidensMod;
import net.maidens.maiden.Maiden;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Men, old men and boys: the vanilla player's body (wide arms, with the outer layer of the skin: jacket, sleeves,
 * trousers), moving as a player does, in one of the vanilla player skins (picked for him, always the same).
 */
public class ManModel extends MaidenModel {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "maiden"), "man");
    /** The vanilla player skins (the wide-armed ones). */
    private static final List<String> SKINS = List.of("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri");
    /** The player is 32 px tall; this makes him 1.8 blocks, as the player renderer does. */
    public static final float SCALE = 0.9375F;

    private final ModelPart jacket, leftSleeve, rightSleeve, leftPants, rightPants;

    public static LayerDefinition create() {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
    }

    public ManModel(ModelPart root) {
        super(root);
        jacket = root.getChild("jacket");
        leftSleeve = root.getChild("left_sleeve");
        rightSleeve = root.getChild("right_sleeve");
        leftPants = root.getChild("left_pants");
        rightPants = root.getChild("right_pants");
    }

    /** His skin. */
    public static ResourceLocation skin(Maiden m) {
        String s = SKINS.get(Math.floorMod(m.getUUID().hashCode() >>> 5, SKINS.size()));
        return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + s + ".png");
    }

    @Override
    public void setupAnim(Maiden maiden, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        young = false;
        humanoidSetupAnim(maiden, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        jacket.copyFrom(body);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        for (ModelPart p : headParts()) p.render(pose, buffer, light, overlay, color);
        for (ModelPart p : bodyParts()) p.render(pose, buffer, light, overlay, color);
        for (ModelPart p : List.of(jacket, leftSleeve, rightSleeve, leftPants, rightPants)) p.render(pose, buffer, light, overlay, color);
    }

    @Override
    public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);
        jacket.visible = leftSleeve.visible = rightSleeve.visible = leftPants.visible = rightPants.visible = visible;
    }
}

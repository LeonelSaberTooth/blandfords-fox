package net.maidens.client;

import net.maidens.MaidensMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The maiden's armor: the vanilla player armor pieces, same sizes and texture layout (helmet 8x8x8, chestplate
 * 8x12x4, sleeves and leggings 4x12x4), each placed over her own head, body, arms and legs. The parts and pivots
 * match {@link MaidenGeometry}, whose poses are copied onto it each frame.
 */
public final class MaidenArmorGeometry {
    public static final ModelLayerLocation INNER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "maiden"), "inner_armor");
    public static final ModelLayerLocation OUTER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "maiden"), "outer_armor");

    private MaidenArmorGeometry() {}

    /** Leggings (and the body part of them): vanilla's inner layer. */
    public static LayerDefinition inner() {
        return create(new CubeDeformation(0.5F), 0F);
    }

    /** Helmet, chestplate, boots: vanilla's outer layer. */
    public static LayerDefinition outer() {
        return create(new CubeDeformation(1.0F), 3F);
    }

    /**
     * @param legDrop how far down her 15-tall leg the 12-tall armor leg sits: leggings start at her hips (0),
     *                boots reach her feet (3)
     */
    private static LayerDefinition create(CubeDeformation d, float legDrop) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, d), pose(MaidenGeometry.HEAD));
        root.addOrReplaceChild("hat", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, d.extend(0.5F)), pose(MaidenGeometry.HEAD));
        // her body runs from her neck (0) to her hips (13); the plate covers 0-12, a little forward for her chest
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 16).addBox(-4F, 0F, -2.3F, 8F, 12F, 4F, d), pose(MaidenGeometry.BODY));
        // sleeves over her tilted arms: same tilt, centred on her 2-wide arm
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), pose(MaidenGeometry.RIGHT_ARM));
        rightArm.addOrReplaceChild("right_sleeve", CubeListBuilder.create()
                .texOffs(40, 16).addBox(-3F, 0F, -2F, 4F, 12F, 4F, d),
                PartPose.offsetAndRotation(1F, -1F, 0F, 0F, 0F, 0.08727F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), pose(MaidenGeometry.LEFT_ARM));
        leftArm.addOrReplaceChild("left_sleeve", CubeListBuilder.create()
                .texOffs(40, 16).mirror().addBox(-1F, 0F, -2F, 4F, 12F, 4F, d),
                PartPose.offsetAndRotation(-1F, -1F, 0F, 0F, 0F, -0.08727F));
        // her legs are 3 wide, a little apart and splayed 4 degrees: the armor leg sits centred on each, splayed alike
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), pose(MaidenGeometry.RIGHT_LEG));
        rightLeg.addOrReplaceChild("right_legging", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-2.5F, legDrop, -2.5F, 4F, 12F, 4F, d),
                PartPose.offsetAndRotation(0F, 0F, 0.5F, 0F, 0F, -0.06981F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), pose(MaidenGeometry.LEFT_LEG));
        leftLeg.addOrReplaceChild("left_legging", CubeListBuilder.create()
                .texOffs(0, 16).mirror().addBox(-1.5F, legDrop, -2.5F, 4F, 12F, 4F, d),
                PartPose.offsetAndRotation(0F, 0F, 0.5F, 0F, 0F, 0.06981F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static PartPose pose(float[] p) {
        return PartPose.offset(p[0], p[1], p[2]);
    }
}

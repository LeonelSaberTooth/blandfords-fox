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
 * Armor shaped to the maiden instead of the player: a helmet over her 8x8x7 head, a chestplate following her
 * chest, breast, waist and hips, and shoulder plates on her thin arms (leggings and boots on her legs too, in
 * case a datapack gives her some). Its pieces read the vanilla armor textures (64x32) at the vanilla spots, so
 * every material, colour and trim looks as it does on a player. The parts and pivots match {@link MaidenGeometry},
 * whose poses are copied onto it each frame.
 */
public final class MaidenArmorGeometry {
    public static final ModelLayerLocation INNER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "maiden"), "inner_armor");
    public static final ModelLayerLocation OUTER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "maiden"), "outer_armor");

    private MaidenArmorGeometry() {}

    public static LayerDefinition inner() {
        return create(0.25F);
    }

    public static LayerDefinition outer() {
        return create(0.45F);
    }

    /** How far the armor stands off her body, in pixels (the arms sit close to the chest, so it's thin). */
    private static LayerDefinition create(float off) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation d = new CubeDeformation(off);
        // helmet: vanilla head at (0,0) 8x8x8; hers is 7 deep, so start one pixel in to keep the face on the front
        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-4F, -8F, -3.5F, 8F, 8F, 7F, new CubeDeformation(off + 0.35F)), pose(MaidenGeometry.HEAD));
        root.addOrReplaceChild("hat", CubeListBuilder.create()
                .texOffs(33, 1).addBox(-4F, -8F, -3.5F, 8F, 8F, 7F, new CubeDeformation(off + 0.6F)), pose(MaidenGeometry.HEAD));
        // chestplate: vanilla body at (16,16) 8x12x4, its front at (20,20)
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 16).addBox(-3.5F, 0F, -2F, 7F, 5F, 4F, d)                     // chest: the top of the plate
                .texOffs(17, 21).addBox(-3F, 5F, -1.5F, 6F, 5F, 3F, d)                     // waist: its middle
                .texOffs(16, 23).addBox(-4F, 10F, -2F, 8F, 3F, 4F, new CubeDeformation(off + 0.05F)), // hips: its bottom rows
                pose(MaidenGeometry.BODY));
        body.addOrReplaceChild("breast", CubeListBuilder.create()
                .texOffs(19, 19).addBox(-3.5F, -5F, -1F, 7F, 5F, 1F, d),
                PartPose.offsetAndRotation(0F, 5F, -2F, -0.1974F, 0F, 0F));
        // shoulder plates: vanilla arm at (40,16) 4x12x4, front at (44,20); hers are 2x12x2
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), pose(MaidenGeometry.RIGHT_ARM));
        rightArm.addOrReplaceChild("right_arm_plate", CubeListBuilder.create()
                .texOffs(43, 18).addBox(-2F, 0F, -1F, 2F, 12F, 2F, d),
                PartPose.offsetAndRotation(1F, -1F, 0F, 0F, 0F, 0.08727F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), pose(MaidenGeometry.LEFT_ARM));
        leftArm.addOrReplaceChild("left_arm_plate", CubeListBuilder.create()
                .texOffs(43, 18).mirror().addBox(0F, 0F, -1F, 2F, 12F, 2F, d),
                PartPose.offsetAndRotation(-1F, -1F, 0F, 0F, 0F, -0.08727F));
        // leggings and boots: vanilla leg at (0,16) 4x12x4; hers are 14 tall, so the top 12 rows are used
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-2F, 0F, -2F, 4F, 12F, 4F, d)
                .texOffs(0, 26).addBox(-2F, 12F, -2F, 4F, 2F, 4F, d), pose(MaidenGeometry.RIGHT_LEG));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 16).mirror().addBox(-2F, 0F, -2F, 4F, 12F, 4F, d)
                .texOffs(0, 26).mirror().addBox(-2F, 12F, -2F, 4F, 2F, 4F, d), pose(MaidenGeometry.LEFT_LEG));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static PartPose pose(float[] p) {
        return PartPose.offset(p[0], p[1], p[2]);
    }
}

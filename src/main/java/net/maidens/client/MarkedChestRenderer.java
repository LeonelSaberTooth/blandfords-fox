package net.maidens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.maidens.MaidensMod;
import net.maidens.block.MarkedChestBlockEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The marked chest: the vanilla chest (its own model and texture), with a gold lock
 * (textures/entity/chest/marked_lock.png, in the chest atlas). Also draws it as an item.
 */
public class MarkedChestRenderer implements BlockEntityRenderer<MarkedChestBlockEntity> {
    private static final Material GOLD_LOCK = new Material(Sheets.CHEST_SHEET, ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "entity/chest/marked_lock"));
    private final ModelPart lid, bottom, lock;

    public MarkedChestRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(ModelLayers.CHEST);
        bottom = root.getChild("bottom");
        lid = root.getChild("lid");
        lock = root.getChild("lock");
    }

    @Override
    public void render(MarkedChestBlockEntity chest, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = chest.getBlockState();
        float facing = state.hasProperty(ChestBlock.FACING) ? state.getValue(ChestBlock.FACING).toYRot() : Direction.SOUTH.toYRot();
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        // the lid opens the way a vanilla chest's does
        float open = 1.0F - chest.getOpenNess(partialTick);
        open = 1.0F - open * open * open;
        lid.xRot = -(open * ((float) Math.PI / 2F));
        lock.xRot = lid.xRot;
        VertexConsumer wood = Sheets.CHEST_LOCATION.buffer(buffers, RenderType::entityCutout);
        lid.render(poseStack, wood, light, overlay);
        bottom.render(poseStack, wood, light, overlay);
        lock.render(poseStack, GOLD_LOCK.buffer(buffers, RenderType::entityCutout), light, overlay);
        poseStack.popPose();
    }
}

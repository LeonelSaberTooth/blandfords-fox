package net.maidens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.maidens.block.MarkedChestBlockEntity;
import net.maidens.core.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The marked chest as an item (in hand, in inventories): drawn by {@link MarkedChestRenderer}, like a vanilla chest item. */
public class MarkedChestItemRenderer extends BlockEntityWithoutLevelRenderer {
    private MarkedChestBlockEntity chest;

    public MarkedChestItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (chest == null) chest = new MarkedChestBlockEntity(BlockPos.ZERO, ModBlocks.MARKED_CHEST.get().defaultBlockState());
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(chest, poseStack, buffers, light, overlay);
    }
}

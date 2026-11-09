package net.maidens.client;

import net.maidens.MaidensMod;
import net.maidens.block.MarkedChestBlockEntity;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Draws the marked chest like a chest, with its own texture (textures/entity/chest/marked.png, in the chest atlas). */
public class MarkedChestRenderer extends ChestRenderer<MarkedChestBlockEntity> {
    private static final Material MARKED = new Material(Sheets.CHEST_SHEET, ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "entity/chest/marked"));

    public MarkedChestRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected Material getMaterial(MarkedChestBlockEntity chest, ChestType type) {
        return MARKED;
    }
}

package net.maidens.client;

import net.maidens.core.ModBlocks;
import net.maidens.core.ModEntities;
import net.maidens.MaidensMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = MaidensMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MAIDEN.get(), MaidenRenderer::new);
        event.registerEntityRenderer(ModEntities.BOBBER.get(), BobberRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.MARKED_CHEST_ENTITY.get(), MarkedChestRenderer::new);
    }

    @SubscribeEvent
    public static void registerItemRenderers(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private MarkedChestItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new MarkedChestItemRenderer();     // made once the game is up
                return renderer;
            }
        }, ModBlocks.MARKED_CHEST_ITEM.get());
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MaidenModel.LAYER, MaidenGeometry::create);
        event.registerLayerDefinition(MaidenArmorGeometry.INNER, MaidenArmorGeometry::inner);
        event.registerLayerDefinition(MaidenArmorGeometry.OUTER, MaidenArmorGeometry::outer);
        event.registerLayerDefinition(CrownLayer.LAYER, CrownLayer::create);
    }
}

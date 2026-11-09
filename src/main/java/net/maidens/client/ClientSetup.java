package net.maidens.client;

import net.maidens.core.ModBlocks;
import net.maidens.core.ModEntities;
import net.maidens.MaidensMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

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
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MaidenModel.LAYER, MaidenGeometry::create);
        event.registerLayerDefinition(MaidenArmorGeometry.INNER, MaidenArmorGeometry::inner);
        event.registerLayerDefinition(MaidenArmorGeometry.OUTER, MaidenArmorGeometry::outer);
    }
}

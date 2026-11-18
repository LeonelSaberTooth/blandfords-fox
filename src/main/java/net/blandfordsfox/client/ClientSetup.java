package net.blandfordsfox.client;

import net.blandfordsfox.BlandfordsFoxMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = BlandfordsFoxMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BlandfordsFoxMod.BLANDFORDS_FOX.get(), BlandfordsFoxRenderer::new);
        event.registerEntityRenderer(BlandfordsFoxMod.CARACAL.get(), CaracalRenderer::new);
        event.registerEntityRenderer(BlandfordsFoxMod.LYNX.get(), ctx -> new CaracalRenderer(ctx, "lynx", 0.45F));
        event.registerEntityRenderer(BlandfordsFoxMod.HYENA.get(), HyenaRenderer::new);
        event.registerEntityRenderer(BlandfordsFoxMod.ONAGER.get(), OnagerRenderer::new);
        event.registerEntityRenderer(BlandfordsFoxMod.PARTRIDGE.get(), PartridgeRenderer::new);
        // Far East's tiger with the animations made for it (registered after Far East's own renderer)
        if (net.neoforged.fml.ModList.get().isLoaded(net.blandfordsfox.compat.FeTiger.MOD_ID))
            net.blandfordsfox.client.fareast.FeTigerClient.renderers(event);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(OnagerRenderer.LAYER, OnagerModel::createBodyLayer);
        event.registerLayerDefinition(OnagerRenderer.TACK_LAYER, OnagerModel::createBodyLayer);
        if (net.neoforged.fml.ModList.get().isLoaded(net.blandfordsfox.compat.FeTiger.MOD_ID))
            net.blandfordsfox.client.fareast.FeTigerClient.layers(event);
    }
}

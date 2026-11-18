package net.blandfordsfox.client.fareast;

import com.chickendesigner.fareast.FarEastMod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Far East compatibility: only called when Far East is installed (it names Far East's classes). */
public final class FeTigerClient {
    private FeTigerClient() {}

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FeTigerRenderer.LAYER, FeTigerData::layer);
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        FeTigerData.load();
        event.registerEntityRenderer(FarEastMod.TIGER.get(), FeTigerRenderer::new);
    }
}

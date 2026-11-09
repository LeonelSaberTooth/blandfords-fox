package net.maidens.client;

import net.maidens.network.MenuPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points the network code calls. */
public final class ClientAccess {
    private ClientAccess() {}

    /** Village screens: the village screen itself, a building's blocks, or the world map. */
    public static void villageView(net.maidens.network.VillageViewPayload view) {
        Minecraft mc = Minecraft.getInstance();
        switch (view.kind()) {
            case net.maidens.network.VillageViewPayload.TEMPLATE -> net.maidens.client.village.TemplateCache.receive(view.data());
            case net.maidens.network.VillageViewPayload.WORLD -> {
                if (mc.screen instanceof net.maidens.client.village.WorldMapScreen w) w.update(view.data());
                else mc.setScreen(new net.maidens.client.village.WorldMapScreen(mc.screen, view.data()));
            }
            default -> {
                if (mc.screen instanceof net.maidens.client.village.VillageScreen s && s.villageId() == view.data().getLong("Id")) s.update(view.data());
                else if (mc.screen instanceof net.maidens.client.village.WorldMapScreen) {
                    // a stance was changed from the world map: the village screen behind it refreshes when we go back
                } else mc.setScreen(new net.maidens.client.village.VillageScreen(view.data()));
            }
        }
    }

    public static void openMenu(MenuPayload menu) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MaidenScreen s && s.entityId() == menu.entityId()) s.update(menu);
        else mc.setScreen(new MaidenScreen(menu));
    }
}

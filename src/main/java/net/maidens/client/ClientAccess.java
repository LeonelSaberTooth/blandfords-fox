package net.maidens.client;

import net.maidens.network.MaidenMenu;
import net.minecraft.client.Minecraft;

/** Client-only entry points the network code calls. */
public final class ClientAccess {
    private ClientAccess() {}

    public static void openMenu(MaidenMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MaidenScreen s && s.entityId() == menu.entityId()) s.update(menu);
        else mc.setScreen(new MaidenScreen(menu));
    }
}

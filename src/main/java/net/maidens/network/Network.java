package net.maidens.network;

import net.maidens.MaidensMod;
import net.maidens.entity.Maiden;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = MaidensMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class Network {
    private Network() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(MaidenMenu.TYPE, MaidenMenu.STREAM_CODEC, (menu, ctx) -> ctx.enqueueWork(() -> {
            // only touches client classes on the client
            if (FMLEnvironment.dist.isClient()) net.maidens.client.ClientAccess.openMenu(menu);
        }));
        registrar.playToServer(MaidenAction.TYPE, MaidenAction.STREAM_CODEC, (action, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player
                    && player.level().getEntity(action.entityId()) instanceof Maiden maiden) {
                maiden.handleAction(player, action.action(), action.arg());
            }
        }));
    }
}

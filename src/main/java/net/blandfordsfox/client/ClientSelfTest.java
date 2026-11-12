package net.blandfordsfox.client;

import net.blandfordsfox.BlandfordsFoxMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Only with -Dblandfordsfox.clienttest: in a loaded world, builds a little scene in a desert,
 * a snowy plains and a plains (rabbits standing and running, birch log columns), takes
 * screenshots of each, and quits. Used by CI to look at the resource pack in a real client.
 */
@EventBusSubscriber(modid = BlandfordsFoxMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSelfTest {
    private ClientSelfTest() {}

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final boolean ON = System.getProperty("blandfordsfox.clienttest") != null;
    /** Landscapes to photograph: a biome, or "dunes" (an open dune field in the desert). */
    private static final List<String> SCENES = List.of("dunes", "dunes2", "taiga", "snowy_taiga", "ice_spikes", "snowy_plains", "savanna");

    private static int tick = -1, scene = 0, sceneStart;
    private static BlockPos spot;
    private static final List<Rabbit> runners = new ArrayList<>();
    private static double runX;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        tick++;
        var server = mc.getSingleplayerServer();
        if (tick == 1) {
            mc.options.hideGui = true;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.setDayTime(6000);
                level.setWeatherParameters(100000, 0, false, false);
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.setGameMode(GameType.SPECTATOR);
            });
        }
        if (tick < 40) return;
        if (tick == 40) { sceneStart = tick; build(server, SCENES.get(0)); return; }
        if (scene >= SCENES.size()) {
            if (tick >= sceneStart + 10) mc.stop();
            return;
        }
        int t = tick - sceneStart;
        if (t == 280) shot(mc, SCENES.get(scene));
        if (t == 290) {
            scene++;
            sceneStart = tick;
            if (scene < SCENES.size()) build(server, SCENES.get(scene));
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> LOG.info("[clienttest] {}", msg.getString()));
    }

    private static void build(net.minecraft.server.MinecraftServer server, String what) {
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            BlockPos c = null;
            if (what.startsWith("dunes")) {
                var columns = net.blandfordsfox.world.NegevFeatures.columnsFor(level);
                var desert = level.findClosestBiome3d(h -> h.is(Biomes.DESERT), p.blockPosition().atY(64), 12000, 32, 64);
                if (columns != null && desert != null) {
                    BlockPos d = desert.getFirst();
                    int skip = what.equals("dunes2") ? 3 : 0;
                    search:
                    for (int r = 0; r <= 4000; r += 48)
                        for (int dx = -r; dx <= r; dx += 48) for (int dz = -r; dz <= r; dz += 48) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                            boolean ok = true;
                            for (int ox = -64; ox <= 64 && ok; ox += 32) for (int oz = -64; oz <= 64 && ok; oz += 32) {
                                var col = columns.get(d.getX() + dx + ox, d.getZ() + dz + oz);
                                ok = col.dune > 0.98 && col.kind == net.blandfordsfox.world.NegevShape.Kind.VANILLA;
                            }
                            if (ok && skip-- <= 0) { c = new BlockPos(d.getX() + dx, 0, d.getZ() + dz); break search; }
                        }
                }
            } else {
                ResourceKey<Biome> key = ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace(what));
                var found = level.findClosestBiome3d(h -> h.is(key), p.blockPosition().atY(64), 12000, 32, 64);
                if (found != null) c = found.getFirst();
            }
            if (c == null) { LOG.info("[clienttest] {} not found", what); c = p.blockPosition(); }
            for (int cx = (c.getX() >> 4) - 3; cx <= (c.getX() >> 4) + 3; cx++)
                for (int cz = (c.getZ() >> 4) - 3; cz <= (c.getZ() >> 4) + 3; cz++) level.getChunk(cx, cz);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
            spot = new BlockPos(c.getX(), y, c.getZ());
            // stand back and up, looking over the landscape
            p.teleportTo(level, c.getX() + 0.5, y + 14, c.getZ() - 30.5, 0, 22);
            LOG.info("[clienttest] scene {} at {}", what, spot);
        });
    }
}

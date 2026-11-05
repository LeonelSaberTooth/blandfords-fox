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
    private static final List<ResourceKey<Biome>> SCENES = List.of(Biomes.DESERT, Biomes.SNOWY_PLAINS, Biomes.PLAINS);

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
        // move the runners along a line, 4 blocks a second, so they animate
        if (!runners.isEmpty() && t > 200) {
            server.execute(() -> {
                runX += 0.2;
                if (runX > 8) runX = -8;
                for (int i = 0; i < runners.size(); i++) {
                    Rabbit r = runners.get(i);
                    r.moveTo(spot.getX() + runX, r.getY(), spot.getZ() + 2 + 2 * i, -90, 0);
                }
            });
        }
        String name = SCENES.get(scene).location().getPath();
        if (t == 300 || t == 302 || t == 304 || t == 306) shot(mc, name + "_" + t);
        if (t == 310) {
            scene++;
            sceneStart = tick;
            if (scene < SCENES.size()) build(server, SCENES.get(scene));
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> LOG.info("[clienttest] {}", msg.getString()));
    }

    private static void build(net.minecraft.server.MinecraftServer server, ResourceKey<Biome> biome) {
        if (biome == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            var found = level.findClosestBiome3d(h -> h.is(biome), p.blockPosition().atY(64), 12000, 32, 64);
            BlockPos c = found != null ? found.getFirst() : p.blockPosition();
            for (int cx = (c.getX() >> 4) - 2; cx <= (c.getX() >> 4) + 2; cx++)
                for (int cz = (c.getZ() >> 4) - 2; cz <= (c.getZ() >> 4) + 2; cz++) level.getChunk(cx, cz);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
            spot = new BlockPos(c.getX(), y, c.getZ());
            // a flat stage, cleared above
            for (int dx = -10; dx <= 10; dx++)
                for (int dz = -4; dz <= 10; dz++) {
                    level.setBlock(spot.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    for (int dy = 0; dy < 8; dy++) level.setBlock(spot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            // birch log columns (the slid bark)
            for (int i = 0; i < 4; i++)
                for (int dy = 0; dy < 6; dy++) level.setBlock(spot.offset(-6 + i, dy, 8), Blocks.BIRCH_LOG.defaultBlockState(), 3);
            runners.forEach(r -> r.discard());
            runners.clear();
            runX = -8;
            // standing rabbits, side on and facing the camera, and two runners
            float[] yaws = {-90, 180};
            for (int i = 0; i < 2; i++) {
                Rabbit r = EntityType.RABBIT.create(level);
                r.moveTo(spot.getX() - 1.5 + 3 * i + 0.5, y, spot.getZ() + 0.5, yaws[i], 0);
                r.setNoAi(true);
                r.setYHeadRot(yaws[i]);
                r.setYBodyRot(yaws[i]);
                level.addFreshEntity(r);
            }
            for (int i = 0; i < 2; i++) {
                Rabbit r = EntityType.RABBIT.create(level);
                r.moveTo(spot.getX() - 8, y, spot.getZ() + 2 + 2 * i, -90, 0);
                r.setNoAi(true);
                level.addFreshEntity(r);
                runners.add(r);
            }
            p.teleportTo(level, spot.getX() + 0.5, y + 1.6, spot.getZ() - 4.5, 0, 12);
            LOG.info("[clienttest] scene {} at {}", biome.location(), spot);
        });
    }
}

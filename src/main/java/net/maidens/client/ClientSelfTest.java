package net.maidens.client;

import net.maidens.core.ModEntities;
import com.mojang.logging.LogUtils;
import net.maidens.MaidensMod;
import net.maidens.maiden.Maiden;
import net.maidens.network.MenuPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Only with -Dmaidens.clienttest: in a loaded world, lines up the six skins and photographs them from the front,
 * side and back, walking, looking up and down; photographs the house the server test found; and opens her menu
 * for a screenshot. Then quits. Used by CI to look at the model in a real client.
 */
@EventBusSubscriber(modid = MaidensMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = System.getProperty("maidens.clienttest") != null;

    private static int tick = -1;
    private static BlockPos spot;
    private static final List<Maiden> line = new ArrayList<>();
    private static double walkX;

    private ClientSelfTest() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        tick++;
        MinecraftServer server = mc.getSingleplayerServer();
        try {
            step(mc, server);
        } catch (Throwable t) {
            LOG.error("[clienttest] failed", t);
            mc.stop();
        }
    }

    private static void step(Minecraft mc, MinecraftServer server) throws Exception {
        if (tick == 1) {
            mc.options.hideGui = true;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.setDayTime(6000);
                level.setWeatherParameters(100000, 0, false, false);
                player(server).setGameMode(GameType.SPECTATOR);
            });
            return;
        }
        if (tick == 40) { stage(server); return; }
        int t = tick - 40;
        // the line-up, all facing the camera, then turned to the side, the back and three-quarters
        if (t == 120) shot(mc, "lineup_front");
        if (t == 122) turn(server, 90);
        if (t == 130) shot(mc, "lineup_side");
        if (t == 132) turn(server, 0);
        if (t == 140) shot(mc, "lineup_back");
        if (t == 142) turn(server, 145);
        if (t == 150) shot(mc, "lineup_quarter");
        // looking up and down (the hair shouldn't swing far out in front)
        if (t == 152) server.execute(() -> {
            turn(server, 90);
            for (int i = 0; i < line.size(); i++) line.get(i).setXRot(i % 2 == 0 ? -60 : 50);
        });
        if (t == 160) shot(mc, "look_up_down");
        if (t == 162) server.execute(() -> { for (Maiden m : line) m.setXRot(0); });
        // walking: the second maiden walks sideways across the stage at walking speed
        if (t > 170 && t < 260) server.execute(() -> {
            walkX += 0.11;
            Maiden m = line.get(1);
            m.moveTo(spot.getX() - 6 + walkX, m.getY(), spot.getZ() + 1, -90, 0);
            m.setYHeadRot(-90);
            m.setYBodyRot(-90);
        });
        if (t == 200 || t == 204 || t == 208 || t == 212) shot(mc, "walk_" + t);
        // the house the server test found
        if (t == 262) {
            Path f = mc.gameDirectory.toPath().resolve("house.txt");
            if (Files.exists(f)) {
                String[] p = Files.readString(f).trim().split(" ");
                int x = Integer.parseInt(p[0]), z = Integer.parseInt(p[2]);
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    for (int cx = (x >> 4) - 2; cx <= (x >> 4) + 2; cx++)
                        for (int cz = (z >> 4) - 2; cz <= (z >> 4) + 2; cz++) level.getChunk(cx, cz);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    player(server).teleportTo(level, x - 9.5, y + 6, z - 12.5, -35, 22);
                });
            }
        }
        if (t == 400) shot(mc, "house");
        // her menu, over the line-up
        if (t == 402) server.execute(() -> player(server).teleportTo(server.overworld(), spot.getX() + 0.5, spot.getY() + 1.6, spot.getZ() - 6.5, 0, 8));
        if (t == 460) {
            mc.options.hideGui = false;
            Maiden m = line.get(0);
            ClientAccess.openMenu(new MenuPayload(m.getId(), "Elowen", "Sweet", 63, 0, 0, 0, 0, "", false, false, 5,
                    "You think so? That's so kind of you, Tester. I've always dreamed someone would bake me a whole cake, just for me..."));
        }
        if (t == 470) shot(mc, "menu");
        if (t == 472 && mc.screen instanceof MaidenScreen s) s.showWorkPage();
        if (t == 480) shot(mc, "menu_work");
        // the house the maidens built in the village (if the server test got that far)
        if (t == 482) {
            mc.options.hideGui = true;
            if (mc.screen != null) mc.setScreen(null);
            Path f = mc.gameDirectory.toPath().resolve("village.txt");
            if (Files.exists(f)) {
                String[] p = Files.readString(f).trim().split(" ");
                int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                server.execute(() -> player(server).teleportTo(server.overworld(), x - 11.5, y + 7, z - 11.5, -45, 25));
            }
        }
        if (t == 640) shot(mc, "village_house");
        if (t == 642) server.execute(() -> {
            ServerPlayer pl = player(server);
            pl.teleportTo(server.overworld(), pl.getX() - 20, pl.getY() + 20, pl.getZ() - 20, -45, 35);
        });
        if (t == 760) shot(mc, "village_wide");
        if (t == 770) mc.stop();
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> LOG.info("[clienttest] {}", msg.getString()));
    }

    private static void turn(MinecraftServer server, float yaw) {
        server.execute(() -> {
            for (Maiden m : line) {
                m.setYRot(yaw);
                m.setYHeadRot(yaw);
                m.setYBodyRot(yaw);
            }
        });
    }

    private static void stage(MinecraftServer server) {
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer p = player(server);
            BlockPos c = p.blockPosition();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
            spot = new BlockPos(c.getX(), y, c.getZ());
            for (int dx = -10; dx <= 10; dx++)
                for (int dz = -8; dz <= 8; dz++) {
                    level.setBlock(spot.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    for (int dy = 0; dy < 10; dy++) level.setBlock(spot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            for (int i = 0; i < Maiden.SKINS; i++) {
                Maiden m = ModEntities.MAIDEN.get().create(level);
                m.moveTo(spot.getX() - 6.25 + i * 2.5, y, spot.getZ() + 3.5, 180, 0);
                m.setNoAi(true);
                m.setYHeadRot(180);
                m.setYBodyRot(180);
                level.addFreshEntity(m);
                m.ensureInitialised();
                m.setSkin(i);
                line.add(m);
            }
            line.get(5).setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            line.get(5).setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            line.get(5).setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            line.get(5).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            line.get(4).setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
            line.get(3).setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            line.get(3).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            line.get(2).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
            line.get(1).setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            line.get(1).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            level.setBlock(spot.offset(-1, 0, 0), net.maidens.core.ModBlocks.MARKED_CHEST.get().defaultBlockState(), 3);
            level.setBlock(spot.offset(1, 0, 0), Blocks.CHEST.defaultBlockState(), 3);
            p.teleportTo(level, spot.getX() + 0.5, y + 1.4, spot.getZ() - 6.5, 0, 6);
            LOG.info("[clienttest] stage at {}", spot);
        });
    }
}

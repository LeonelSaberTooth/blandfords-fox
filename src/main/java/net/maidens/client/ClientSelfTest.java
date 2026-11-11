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
    private static final List<Maiden> line = new ArrayList<>(), row2 = new ArrayList<>(), variants = new ArrayList<>();
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
        // the bell screen of that village (as a citizen who defended it), then the world map
        if (t == 762) {
            mc.options.hideGui = false;
            Path f = mc.gameDirectory.toPath().resolve("village.txt");
            BlockPos near = null;
            if (Files.exists(f)) {
                String[] p = Files.readString(f).trim().split(" ");
                near = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
            BlockPos at = near;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                net.maidens.village.Villages all = net.maidens.village.Villages.get(level);
                net.maidens.village.VillageRecord v = at == null ? null : all.at(at);
                if (v == null) for (net.maidens.village.VillageRecord r : all.all()) if (v == null) v = r;
                if (v == null) {
                    LOG.info("[clienttest] no village for the village screen");
                    return;
                }
                ServerPlayer pl = player(server);
                net.maidens.village.VillageSocial.makeCitizen(level, v, pl);
                v.addDefender(pl.getUUID());
                net.maidens.village.VillageView.open(level, v, pl);
            });
        }
        if (t == 840) shot(mc, "village_screen");
        if (t == 842 && mc.screen instanceof net.maidens.client.village.VillageScreen s) s.showTab(3);
        if (t == 845) shot(mc, "village_store");
        if (t == 846 && mc.screen instanceof net.maidens.client.village.VillageScreen s) s.showTab(1);
        if (t == 850) shot(mc, "village_diplomacy");
        if (t == 852 && mc.screen instanceof net.maidens.client.village.VillageScreen s) s.openWorldMap();
        if (t == 1000) shot(mc, "world_map");
        if (t == 1002 && mc.screen instanceof net.maidens.client.village.WorldMapScreen w) w.selectForTest();
        if (t == 1010) shot(mc, "world_map_village");
        // the castle, and the walls from high above
        if (t == 1012) {
            mc.options.hideGui = true;
            if (mc.screen != null) mc.setScreen(null);
            Path f = mc.gameDirectory.toPath().resolve("castle.txt");
            if (Files.exists(f)) {
                String[] p = Files.readString(f).trim().split(" ");
                int cx = Integer.parseInt(p[0]), cy = Integer.parseInt(p[1]), cz = Integer.parseInt(p[2]);
                server.execute(() -> player(server).teleportTo(server.overworld(), cx - 26.5, cy + 24, cz - 26.5, -45, 30));
            } else LOG.info("[clienttest] no castle.txt");
        }
        if (t == 1160) shot(mc, "castle");
        if (t == 1162) {
            Path f = mc.gameDirectory.toPath().resolve("castle.txt");
            if (Files.exists(f)) {
                String[] p = Files.readString(f).trim().split(" ");
                int vx = Integer.parseInt(p[3]), vy = Integer.parseInt(p[4]), vz = Integer.parseInt(p[5]);
                server.execute(() -> player(server).teleportTo(server.overworld(), vx - 110.5, vy + 100, vz - 110.5, -45, 38));
            }
        }
        if (t == 1320) shot(mc, "walls_wide");
        if (t == 1322) {
            Path f = mc.gameDirectory.toPath().resolve("castle.txt");
            if (Files.exists(f)) {
                String[] p = Files.readString(f).trim().split(" ");
                int vx = Integer.parseInt(p[3]), vy = Integer.parseInt(p[4]), vz = Integer.parseInt(p[5]);
                server.execute(() -> player(server).teleportTo(server.overworld(), vx + 0.5, vy + 140, vz + 0.5, 0, 90));
            }
        }
        if (t == 1460) shot(mc, "walls_top");
        // the second row up close, from behind it (they turn round): men, the old, children, casual clothes
        if (t == 1462) server.execute(() -> {
            for (Maiden m : row2) {
                m.setYRot(0);
                m.setYHeadRot(0);
                m.setYBodyRot(0);
            }
            player(server).teleportTo(server.overworld(), spot.getX() + 0.5, spot.getY() + 0.6, spot.getZ() + 12.5, 180, 12);
        });
        if (t == 1490) shot(mc, "people");
        if (t == 1491) dumpVillagers(mc);
        // ten maidens put together from random parts (skin tone, clothes, eyes, hair): at home in casual clothes
        // (under the roof), out in their usual clothes, and at work (with their job's hat and tool)
        if (t == 1492) server.execute(() -> {
            for (Maiden m : line) m.discard();
            for (Maiden m : row2) m.discard();
            ServerLevel level = server.overworld();
            net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(42);
            for (int i = 0; i < 10; i++) {
                Maiden m = ModEntities.MAIDEN.get().create(level);
                m.moveTo(spot.getX() - 6.25 + i * 1.4, spot.getY(), spot.getZ() + 7.0, 180, 0);
                m.setNoAi(true);
                m.setYHeadRot(180);
                m.setYBodyRot(180);
                level.addFreshEntity(m);
                m.ensureInitialised();
                m.setLooks(r.nextInt(Maiden.TONES), r.nextInt(Maiden.OUTFITS), r.nextInt(Maiden.EYE_COLOURS), r.nextInt(Maiden.HAIRS));
                variants.add(m);
            }
            player(server).teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 4);
        });
        if (t == 1530) shot(mc, "variants_casual");
        if (t == 1532) server.execute(() -> {
            for (int i = 0; i < variants.size(); i++) {
                Maiden m = variants.get(i);
                m.moveTo(spot.getX() - 6.25 + i * 1.4, spot.getY(), spot.getZ() - 1.0, 180, 0);
                m.setYHeadRot(180);
                m.setYBodyRot(180);
            }
            player(server).teleportTo(server.overworld(), spot.getX() + 0.5, spot.getY(), spot.getZ() - 7.5, 0, 4);
        });
        if (t == 1560) shot(mc, "variants_normal");
        if (t == 1562) server.execute(() -> {
            net.maidens.maiden.work.Job[] jobs = {net.maidens.maiden.work.Job.FARM, net.maidens.maiden.work.Job.WOOD, net.maidens.maiden.work.Job.MINE, net.maidens.maiden.work.Job.FISH};
            for (int i = 0; i < variants.size(); i++) variants.get(i).employment().hireForTest(jobs[i % jobs.length], null);
        });
        if (t == 1590) shot(mc, "variants_work");
        // the "model" maiden (the viewer's saved proportions) walking the runway walk across the stage, from the side
        // and the front; then faces up close: freckles, blushing, smiling
        if (t == 1592) server.execute(() -> {
            Maiden m = variants.get(0);
            m.setModelPose(true);
            walkX = 0;
            player(server).teleportTo(server.overworld(), spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() - 6.5, 0, 6);
        });
        if (t > 1600 && t < 1700) server.execute(() -> {
            walkX += 0.11;
            Maiden m = variants.get(0);
            m.moveTo(spot.getX() - 5 + walkX, spot.getY(), spot.getZ() + 1, -90, 0);
            m.setYHeadRot(-90);
            m.setYBodyRot(-90);
        });
        if (t == 1630 || t == 1634 || t == 1638 || t == 1642) shot(mc, "model_walk_" + t);
        if (t == 1644) server.execute(() -> player(server).teleportTo(server.overworld(), spot.getX() + 6.5, spot.getY() + 0.3, spot.getZ() + 1.5, 90, 6));
        if (t == 1660 || t == 1664 || t == 1668) shot(mc, "model_walk_front_" + t);
        if (t == 1700) server.execute(() -> {
            for (int i = 1; i < 4; i++) {
                Maiden m = variants.get(i);
                m.setFreckles(i == 1);
                m.moveTo(spot.getX() - 1.5 + (i - 1) * 1.5, spot.getY(), spot.getZ() + 3, 180, 0);
                m.setYHeadRot(180);
                m.setYBodyRot(180);
                m.setXRot(0);
            }
            player(server).teleportTo(server.overworld(), spot.getX() + 0.5, spot.getY() + 0.9, spot.getZ() + 0.6, 0, 8);
        });
        if (t == 1720) {
            for (int i = 2; i < 4; i++) {
                var e = mc.level.getEntity(variants.get(i).getId());
                if (e instanceof Maiden c) c.handleEntityEvent(i == 2 ? Maiden.EVENT_BLUSH : Maiden.EVENT_SMILE);
            }
        }
        if (t == 1721) shot(mc, "faces_freckles_blush_smile");
        if (t == 1730) mc.stop();
    }

    private static void bag(Maiden m, net.minecraft.world.item.Item... items) {
        for (var i : items) m.employment().bag().addItem(new ItemStack(i));
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    /** The vanilla villager textures put together (body, type robe, trade), for the model viewer. */
    private static void dumpVillagers(Minecraft mc) {
        String[] types = {"plains", "desert", "savanna", "taiga", "snow", "jungle", "swamp"};
        String[] trades = {"none", "farmer", "fisherman", "fletcher", "toolsmith", "armorer", "butcher", "cartographer", "cleric",
                "leatherworker", "librarian", "mason", "shepherd", "weaponsmith"};
        java.io.File dir = new java.io.File(mc.gameDirectory, "screenshots");
        dir.mkdirs();
        for (String type : types)
            for (String trade : trades) {
                try (com.mojang.blaze3d.platform.NativeImage out = new com.mojang.blaze3d.platform.NativeImage(64, 64, true)) {
                    for (String path : new String[]{"villager/villager", "villager/type/" + type, trade.equals("none") ? null : "villager/profession/" + trade}) {
                        if (path == null) continue;
                        var loc = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/" + path + ".png");
                        try (var in = mc.getResourceManager().getResource(loc).orElseThrow().open();
                             var img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                            for (int y = 0; y < 64; y++)
                                for (int x = 0; x < 64; x++) {
                                    int c = img.getPixelRGBA(x, y);
                                    if (c >>> 24 > 0) out.setPixelRGBA(x, y, c);
                                }
                        }
                    }
                    out.writeToFile(new java.io.File(dir, "villager_" + type + "_" + trade + ".png"));
                } catch (Exception e) {
                    LOG.warn("[clienttest] villager texture {} {}: {}", type, trade, e.toString());
                }
            }
        LOG.info("[clienttest] villager textures written");
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
                for (int dz = -8; dz <= 14; dz++) {
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
                m.setLooks(i, i, i, i);
                line.add(m);
            }
            // a second row behind, under a roof: a man, an old woman, an old man, two children, and a woman in her
            // casual clothes (she's indoors); and the farmer in the front row wears her hat
            for (int dx = -9; dx <= 9; dx++)
                for (int dz = 5; dz <= 10; dz++) level.setBlock(spot.offset(dx, 3, dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            Maiden.Kind[] kinds = {Maiden.Kind.MAN, Maiden.Kind.OLD_WOMAN, Maiden.Kind.OLD_MAN, Maiden.Kind.WOMAN, Maiden.Kind.MAN, Maiden.Kind.WOMAN};
            for (int i = 0; i < kinds.length; i++) {
                Maiden m = ModEntities.MAIDEN.get().create(level);
                m.moveTo(spot.getX() - 5.0 + i * 2.5, y, spot.getZ() + 7.0, 180, 0);
                m.setNoAi(true);
                m.setYHeadRot(180);
                m.setYBodyRot(180);
                level.addFreshEntity(m);
                m.ensureInitialised();
                m.setKind(kinds[i]);
                if (i == 3 || i == 4) m.makeChild();
                m.setLooks(i, i, i, i);
                row2.add(m);
            }
            line.get(4).employment().hireForTest(net.maidens.maiden.work.Job.FARM, null);
            // armor goes in their bags (they wear the best of it); the guard holds her shield
            bag(line.get(5), Items.IRON_SWORD, Items.SHIELD, Items.IRON_HELMET, Items.IRON_CHESTPLATE);
            line.get(5).employment().hireForTest(net.maidens.maiden.work.Job.GUARD, null);
            line.get(4).setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
            line.get(0).setRoyal(true);
            bag(line.get(3), Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE);
            bag(line.get(2), Items.GOLDEN_CHESTPLATE);
            bag(line.get(1), Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE);
            level.setBlock(spot.offset(-1, 0, 0), net.maidens.core.ModBlocks.MARKED_CHEST.get().defaultBlockState(), 3);
            level.setBlock(spot.offset(1, 0, 0), Blocks.CHEST.defaultBlockState(), 3);
            p.teleportTo(level, spot.getX() + 0.5, y + 1.4, spot.getZ() - 6.5, 0, 6);
            LOG.info("[clienttest] stage at {}", spot);
        });
    }
}

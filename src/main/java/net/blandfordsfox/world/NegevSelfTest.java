package net.blandfordsfox.world;

import com.mojang.datafixers.util.Pair;
import net.blandfordsfox.BlandfordsFoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Development check, off unless the server is started with -Dblandfordsfox.selftest=&lt;folder&gt;.
 * Finds desert highland valleys, a dune wadi and a dry river in a fresh world, generates them
 * fully, writes the blocks to text files for inspection, then stops the server.
 */
@EventBusSubscriber(modid = BlandfordsFoxMod.MOD_ID)
public final class NegevSelfTest {
    private static final Logger LOG = LoggerFactory.getLogger("blandfordsfox/selftest");

    private NegevSelfTest() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        String out = System.getProperty("blandfordsfox.selftest");
        if (out == null) return;
        MinecraftServer server = event.getServer();
        try {
            run(server, Path.of(out));
        } catch (Throwable t) {
            LOG.error("Self test failed", t);
            try {
                Files.createDirectories(Path.of(out));
                Files.writeString(Path.of(out, "error.txt"), t.toString());
            } catch (IOException ignored) { }
        }
        server.halt(false);
    }

    private static void run(MinecraftServer server, Path out) throws IOException {
        Files.createDirectories(out);
        ServerLevel level = server.overworld();
        NegevColumns columns = NegevFeatures.columnsFor(level);
        StringBuilder report = new StringBuilder();
        report.append("negev columns wired: ").append(columns != null)
                .append(", final density class: ").append(level.getChunkSource().randomState().router().finalDensity().getClass().getName()).append('\n');
        Pair<BlockPos, Holder<Biome>> desert = level.findClosestBiome3d(h -> h.is(Biomes.DESERT), BlockPos.ZERO.atY(64), 12000, 32, 64);
        report.append("nearest desert: ").append(desert == null ? "none" : desert.getFirst().toShortString()).append('\n');
        if (columns == null || desert == null) {
            Files.writeString(out.resolve("report.txt"), report);
            return;
        }
        BlockPos d = desert.getFirst();
        int n = 0;
        n += dump(level, columns, d, c -> c.kind == NegevShape.Kind.SLOPE && c.high >= 0.99 && c.plateau >= 16, "highland_tall", out, report);
        n += dump(level, columns, d, c -> c.kind == NegevShape.Kind.FLOOR && c.high >= 0.99 && c.plateau <= 10, "highland_low", out, report);
        n += dump(level, columns, d, c -> c.kind == NegevShape.Kind.WADI_FLOOR, "dune_wadi", out, report);
        n += dump(level, columns, d, c -> c.high > 0.2 && c.high < 0.8, "highland_edge", out, report);
        n += dump(level, columns, d, c -> c.kind == NegevShape.Kind.RIVER_FLOOR, "dry_river", out, report);
        report.append("areas written: ").append(n).append('\n');
        // animals spawned with the generated chunks, around the desert
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(d).inflate(1500, 200, 1500);
        hyenaTest(level, d, report);
        animalTest(level, d, box, report);
        partridgeTest(level, d, report);
        report.append("caracals: ").append(level.getEntities(net.blandfordsfox.BlandfordsFoxMod.CARACAL.get(), box, e -> true).size())
                .append(", blandford's foxes: ").append(level.getEntities(net.blandfordsfox.BlandfordsFoxMod.BLANDFORDS_FOX.get(), box, e -> true).size())
                .append('\n');
        Files.writeString(out.resolve("report.txt"), report);
    }

    /** Finds a column matching want near the desert, generates 160x160 blocks around it and writes them out. */
    private static int dump(ServerLevel level, NegevColumns columns, BlockPos near, Predicate<NegevShape.Column> want,
                            String name, Path out, StringBuilder report) throws IOException {
        return dump(level, columns, near, (x, z) -> want.test(columns.get(x, z)), name, out, report);
    }

    interface At {
        boolean test(int x, int z);
    }

    private static int dump(ServerLevel level, NegevColumns columns, BlockPos near, At want,
                            String name, Path out, StringBuilder report) throws IOException {
        BlockPos found = null;
        search:
        for (int r = 0; r <= 3000; r += 32) {
            for (int dx = -r; dx <= r; dx += 32) for (int dz = -r; dz <= r; dz += 32) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                int x = near.getX() + dx, z = near.getZ() + dz;
                if (want.test(x, z)) { found = new BlockPos(x, 0, z); break search; }
            }
        }
        if (found == null) {
            report.append(name).append(": not found\n");
            return 0;
        }
        int size = 160, x0 = found.getX() - size / 2, z0 = found.getZ() - size / 2;
        // generate the chunks fully (features included)
        for (int cx = (x0 >> 4) - 1; cx <= ((x0 + size) >> 4) + 1; cx++)
            for (int cz = (z0 >> 4) - 1; cz <= ((z0 + size) >> 4) + 1; cz++) level.getChunk(cx, cz);
        int deserts = 0, painted = 0, shapedHere = 0, verified = 0;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve(name + ".txt")))) {
            w.println(size + " " + x0 + " " + z0);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int z = z0; z < z0 + size; z++) for (int x = x0; x < x0 + size; x++) {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                StringBuilder sb = new StringBuilder().append(top).append(' ');
                for (int y = top; y > top - 45; y--) {
                    p.set(x, y, z);
                    sb.append(code(level.getBlockState(p)));
                }
                NegevShape.Column c = columns.get(x, z);
                sb.append(' ').append(c.kind.ordinal());
                sb.append(' ').append(c.surfExact).append(' ').append(Math.round(c.high * 100)).append(' ').append(c.floor)
                        .append(' ').append(c.wadiExact).append(' ').append(Math.round(c.wadi * 100));
                if (c.kind != NegevShape.Kind.VANILLA) {
                    shapedHere++;
                    int solidTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (NegevFeatures.verifiedKind(c, solidTop) == c.kind) verified++;
                }
                if (level.getBiome(p.set(x, top, z)).is(Biomes.DESERT)) { sb.append(" d"); deserts++; } else sb.append(" -");
                if (c.kind != NegevShape.Kind.VANILLA) painted++;
                w.println(sb);
            }
        }
        report.append(name).append(": centre ").append(found.getX()).append(' ').append(found.getZ())
                .append(", desert columns ").append(deserts).append(", shaped columns ").append(painted)
                .append(", ground where expected ").append(verified).append('/').append(shapedHere).append('\n');
        return 1;
    }

    /** Feeds a hyena bones and rotten flesh as a player would, and checks the results. */
    private static void hyenaTest(ServerLevel level, BlockPos near, StringBuilder report) {
        try {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ());
            net.blandfordsfox.entity.Hyena h = net.blandfordsfox.BlandfordsFoxMod.HYENA.get().create(level);
            h.moveTo(near.getX() + 0.5, y, near.getZ() + 0.5, 0, 0);
            level.addFreshEntity(h);
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            player.moveTo(near.getX() + 2.5, y, near.getZ() + 0.5, 0, 0);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 10));
            int ok = 0;
            for (int i = 0; i < 7; i++) {
                if (h.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction()) ok++;
            }
            int bonesLeft = player.getMainHandItem().getCount();
            for (int i = 0; i < 600; i++) h.tick();
            int blocks = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, h.getBoundingBox().inflate(8),
                    e -> e.getItem().is(net.minecraft.world.item.Items.BONE_BLOCK)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 64));
            int tries = 0;
            while (!h.isTame() && tries < 60) { h.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND); tries++; }
            long hurtGoals = h.hyenaHurtGoalsForTest();
            report.append("hyena: bone feeds accepted ").append(ok).append("/7, bones left ").append(bonesLeft)
                    .append(", bone blocks dropped ").append(blocks)
                    .append(", tamed with rotten flesh after ").append(tries).append(" tries (tame=").append(h.isTame()).append(")")
                    .append(", hurt-by-target goals ").append(hurtGoals).append('\n');
            h.discard();

            // a wild hyena should fall asleep by day and wake at night
            net.blandfordsfox.entity.Hyena w = net.blandfordsfox.BlandfordsFoxMod.HYENA.get().create(level);
            w.moveTo(near.getX() + 30.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX() + 30, near.getZ()), near.getZ() + 0.5, 0, 0);
            level.addFreshEntity(w);
            level.setDayTime(6000);
            level.updateSkyBrightness();
            boolean sleptByDay = false;
            for (int i = 0; i < 1200 && !sleptByDay; i++) { w.tick(); sleptByDay = w.isHyenaSleeping(); }
            level.setDayTime(18000);
            level.updateSkyBrightness();
            for (int i = 0; i < 100; i++) w.tick();
            report.append("hyena: fell asleep by day ").append(sleptByDay).append(", awake at night ").append(!w.isHyenaSleeping()).append('\n');
            w.discard();
        } catch (Throwable t) {
            report.append("hyena test failed: ").append(t).append('\n');
        }
    }

    /** Where hares, onagers and rabbits ended up, how a hare moves, and what onagers breed. */
    private static void animalTest(ServerLevel level, BlockPos near, net.minecraft.world.phys.AABB box, StringBuilder report) {
        try {
            // snowy plains: generate an area so its animals spawn
            Pair<BlockPos, Holder<Biome>> snow = level.findClosestBiome3d(h -> h.is(Biomes.SNOWY_PLAINS), near.atY(64), 12000, 32, 64);
            net.minecraft.world.phys.AABB snowBox = null;
            if (snow != null) {
                BlockPos c = snow.getFirst();
                for (int cx = (c.getX() >> 4) - 6; cx <= (c.getX() >> 4) + 6; cx++)
                    for (int cz = (c.getZ() >> 4) - 6; cz <= (c.getZ() >> 4) + 6; cz++) level.getChunk(cx, cz);
                snowBox = new net.minecraft.world.phys.AABB(c).inflate(100, 200, 100);
            }
            for (var b : snowBox == null ? java.util.List.of(box) : java.util.List.of(box, snowBox)) {
                java.util.Map<String, Integer> count = new java.util.TreeMap<>();
                for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, b, e -> true)) {
                    String biome = level.getBiome(e.blockPosition()).unwrapKey().map(k -> k.location().getPath()).orElse("?");
                    String what = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
                    if (e instanceof net.blandfordsfox.entity.Hare hare) what += "/" + hare.getCoat().name().toLowerCase();
                    if (!what.startsWith("hare") && !what.equals("rabbit") && !what.equals("onager")) continue;
                    count.merge(what + " in " + biome, 1, Integer::sum);
                }
                report.append(b == box ? "animals around the desert: " : "animals around snowy plains: ").append(count).append('\n');
            }

            // a hare asked to run 24 blocks: ground speed, and whether it hops or runs
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ());
            net.blandfordsfox.entity.Hare hare = BlandfordsFoxMod.HARE.get().create(level);
            hare.moveTo(near.getX() + 0.5, y, near.getZ() + 0.5, 0, 0);
            level.addFreshEntity(hare);
            for (int i = 0; i < 20; i++) { hare.tickCount++; hare.tick(); }
            for (double mod : new double[] {0.6, 2.2}) {
                net.minecraft.world.phys.Vec3 start = hare.position();
                hare.getNavigation().moveTo(start.x + 24, start.y, start.z, mod);
                int airborne = 0, ticks = 0, movingTicks = 0, bounds = 0;
                double moved = 0, movingDist = 0;
                boolean wasMoving = false;
                for (int i = 0; i < 200 && !hare.getNavigation().isDone(); i++) {
                    double x0 = hare.getX(), z0 = hare.getZ();
                    hare.tickCount++;   // the level does this when it ticks an entity
                    hare.tick();
                    double step = Math.hypot(hare.getX() - x0, hare.getZ() - z0);
                    boolean moving = step > 0.03;
                    if (i >= 10) {
                        moved += step; ticks++;
                        if (!hare.onGround()) airborne++;
                        if (moving) { movingTicks++; movingDist += step; if (!wasMoving) bounds++; }
                    }
                    wasMoving = moving;
                }
                report.append(String.format(java.util.Locale.ROOT,
                        "hare run (speed x%.1f): average %.2f blocks/s, %.2f blocks/s while moving, moving %d of %d ticks in %d bounds, airborne %d, got %.1f blocks%n",
                        mod, ticks > 0 ? moved / ticks * 20 : 0, movingTicks > 0 ? movingDist / movingTicks * 20 : 0,
                        movingTicks, ticks, bounds, airborne, hare.position().distanceTo(start)));
            }
            hare.discard();

            // onager foals
            var a = BlandfordsFoxMod.ONAGER.get().create(level);
            var b = BlandfordsFoxMod.ONAGER.get().create(level);
            var donkey = net.minecraft.world.entity.EntityType.DONKEY.create(level);
            var horse = net.minecraft.world.entity.EntityType.HORSE.create(level);
            report.append("onager x onager -> ").append(name(a.getBreedOffspring(level, b)))
                    .append(", onager x donkey -> ").append(name(a.getBreedOffspring(level, donkey)))
                    .append(", onager x horse -> ").append(name(a.getBreedOffspring(level, horse)))
                    .append('\n');
        } catch (Throwable t) {
            report.append("animal test failed: ").append(t).append('\n');
        }
    }

    /** Walks a player up to a partridge and watches it flush: when, how fast, how high, how far. */
    private static void partridgeTest(ServerLevel level, BlockPos near, StringBuilder report) {
        net.minecraft.server.level.ServerPlayer player = null;
        boolean added = false;
        try {
            report.append("partridge: drops ").append(BlandfordsFoxMod.PARTRIDGE.get().create(level).getLootTable().location())
                    .append(", spawned nearby ").append(level.getEntitiesOfClass(net.blandfordsfox.entity.Partridge.class,
                            new net.minecraft.world.phys.AABB(near).inflate(200, 200, 200), e -> true).size()).append('\n');
            player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            BlockPos spot = drySpot(level, near, 0);
            report.append("partridge test spot ").append(spot.toShortString()).append(" on ").append(level.getBlockState(spot.below()).getBlock()).append('\n');
            net.blandfordsfox.entity.Partridge bird = BlandfordsFoxMod.PARTRIDGE.get().create(level);
            bird.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
            level.addFreshEntity(bird);
            for (int i = 0; i < 20; i++) { bird.tickCount++; bird.tick(); }
            player.moveTo(bird.getX() - 4, bird.getY(), bird.getZ(), -90, 0);
            level.addNewPlayer(player);
            added = true;
            report.append("partridge, player 4 blocks away: ").append(watchFlight(level, bird, 300)).append('\n');

            // with seeds in hand it should stay put
            for (int i = 0; i < 80; i++) { bird.tickCount++; bird.tick(); }
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS));
            player.moveTo(bird.getX() - 2, bird.getY(), bird.getZ(), -90, 0);
            boolean flew = false;
            for (int i = 0; i < 60 && !flew; i++) { bird.tickCount++; bird.tick(); flew = bird.getFlight() != 0; }
            report.append("partridge, player with seeds 2 blocks away: flew ").append(flew).append('\n');
            bird.discard();
        } catch (Throwable t) {
            report.append("partridge test failed: ").append(t).append('\n');
        } finally {
            if (added) try { level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); } catch (Throwable ignored) {}
        }
        try {
            // hit by a zombie: it should fly off too
            BlockPos spot = drySpot(level, near, 40);
            net.blandfordsfox.entity.Partridge bird = BlandfordsFoxMod.PARTRIDGE.get().create(level);
            bird.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
            level.addFreshEntity(bird);
            for (int i = 0; i < 20; i++) { bird.tickCount++; bird.tick(); }
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            zombie.moveTo(bird.getX() + 1.5, bird.getY(), bird.getZ(), 0, 0);
            bird.hurt(level.damageSources().mobAttack(zombie), 0.5F);
            report.append("partridge, hit by a zombie: ").append(watchFlight(level, bird, 300)).append('\n');
            bird.discard();
        } catch (Throwable t) {
            report.append("partridge hit test failed: ").append(t).append('\n');
        }
    }

    /** Open dry ground near a place: no fluid at or under the surface, sky above. */
    private static BlockPos drySpot(ServerLevel level, BlockPos near, int skip) {
        int found = 0;
        for (int r = 8; r < 400; r += 4)
            for (int a = 0; a < 16; a++) {
                int x = near.getX() + (int) (r * Math.cos(a * Math.PI / 8)), z = near.getZ() + (int) (r * Math.sin(a * Math.PI / 8));
                BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                if (!level.getFluidState(p).isEmpty() || !level.getFluidState(p.below()).isEmpty()) continue;
                if (!level.getBlockState(p.below()).isSolid()) continue;
                if (found++ >= skip) return p;
            }
        return near;
    }

    private static String watchFlight(ServerLevel level, net.blandfordsfox.entity.Partridge bird, int max) {
        int takeoff = -1, flying = 0, landing = 0, t = 0;
        double flyDist = 0, maxH = 0, sumH = 0;
        net.minecraft.world.phys.Vec3 start = null;
        for (; t < max; t++) {
            double x0 = bird.getX(), z0 = bird.getZ();
            bird.tickCount++;
            bird.tick();
            int f = bird.getFlight();
            if (f != 0 && takeoff < 0) { takeoff = t; start = new net.minecraft.world.phys.Vec3(x0, bird.getY(), z0); }
            double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, net.minecraft.util.Mth.floor(bird.getX()), net.minecraft.util.Mth.floor(bird.getZ()));
            if (f == 1) {
                flying++;
                flyDist += Math.hypot(bird.getX() - x0, bird.getZ() - z0);
                double h = bird.getY() - ground;
                maxH = Math.max(maxH, h);
                sumH += h;
            } else if (f == 2) landing++;
            if (takeoff >= 0 && f == 0) break;
        }
        if (takeoff < 0) return "did not fly in " + max + " ticks";
        return String.format(java.util.Locale.ROOT,
                "took off after %d ticks, flew %d ticks at %.1f blocks/s, %.1f blocks above the ground on average (max %.1f), glided in %d ticks, landed %s %.1f blocks away",
                takeoff, flying, flying > 0 ? flyDist / flying * 20 : 0, flying > 0 ? sumH / flying : 0, maxH, landing,
                bird.getFlight() == 0 && bird.onGround() ? "on the ground" : "NOT YET", start == null ? 0 : start.distanceTo(bird.position()));
    }

    private static String name(net.minecraft.world.entity.Entity e) {
        return e == null ? "none" : BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
    }

    private static char code(BlockState s) {
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return switch (id) {
            case "air", "cave_air", "void_air" -> '.';
            case "sand" -> 'a';
            case "sandstone" -> 'S';
            case "smooth_sandstone", "cut_sandstone", "chiseled_sandstone" -> 's';
            case "granite" -> 'G';
            case "packed_mud" -> 'P';
            case "stripped_oak_wood", "limestone" -> 'o';
            case "limestone_gravel" -> 'P';
            case "dirt", "coarse_dirt" -> 'D';
            case "grass_block" -> 'R';
            case "stone", "deepslate", "andesite", "diorite", "tuff" -> 'X';
            case "water" -> 'W';
            case "lava" -> 'V';
            case "acacia_leaves" -> 'L';
            case "acacia_log" -> 'T';
            case "dead_bush" -> 'b';
            case "short_grass", "tall_grass" -> 'g';
            case "gravel" -> 'v';
            case "red_sand" -> 'r';
            case "terracotta" -> 't';
            default -> s.isAir() ? '.' : (s.getFluidState().isEmpty() ? '?' : 'W');
        };
    }
}

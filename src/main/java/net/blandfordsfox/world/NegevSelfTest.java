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
        } catch (Throwable t) {
            report.append("hyena test failed: ").append(t).append('\n');
        }
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

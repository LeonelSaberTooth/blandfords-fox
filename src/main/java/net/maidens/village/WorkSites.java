package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a village's workers go: a wood with the most trees near the village, a spot outside it to dig a mine
 * from, the shore of water nearby. Found by looking at a few hundred places around it (loaded land only).
 */
public final class WorkSites {
    private static final int SAMPLES = 300;

    private WorkSites() {}

    private static BoundingBox outside(VillageRecord v) {
        return v.wallBox != null ? v.wallBox : v.box;
    }

    private static int half(VillageRecord v) {
        BoundingBox b = outside(v);
        return Math.max(b.getXSpan(), b.getZSpan()) / 2;
    }

    /** A random spot from just outside the village to this far beyond it. */
    private static int[] around(VillageRecord v, RandomSource r, int beyond) {
        BlockPos c = v.centre();
        double a = r.nextDouble() * Math.PI * 2, d = half(v) + 4 + r.nextDouble() * beyond;
        return new int[]{c.getX() + (int) (Math.cos(a) * d), c.getZ() + (int) (Math.sin(a) * d)};
    }

    private static boolean taken(VillageRecord v, int x, int z) {
        BoundingBox b = outside(v);
        boolean in = x >= b.minX() - 2 && x <= b.maxX() + 2 && z >= b.minZ() - 2 && z <= b.maxZ() + 2;
        return in || v.blueprint.overlaps(new BoundingBox(x - 1, -64, z - 1, x + 1, 320, z + 1));
    }

    /**
     * The heights the village's people walk at: its bell, its gates, its fields (which may be down a hill from the
     * houses). A work spot has to be near one of them: not up a mountain or down a ravine.
     */
    private static List<Integer> heights(ServerLevel level, VillageRecord v) {
        List<Integer> ys = new ArrayList<>();
        BlockPos b = v.bell();
        if (b != null) ys.add(b.getY());
        else {
            BlockPos c = v.centre();
            ys.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()));
        }
        for (VillageRecord.Gate g : v.gates) ys.add(g.pos.getY());
        for (Blueprint.PieceInfo p : v.blueprint.pieces()) if (p.name().equals(Farms.NAME)) ys.add(p.box().maxY());
        return ys;
    }

    private static boolean reachable(List<Integer> ys, int y) {
        for (int h : ys) if (Math.abs(h - y) <= CLIMB) return true;
        return false;
    }

    private static final int CLIMB = 10, FAR = 100;

    /** Dry, walkable ground at a column: the block above it, or null. */
    @Nullable
    private static BlockPos ground(ServerLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos below = new BlockPos(x, y - 1, z);
        BlockState s = level.getBlockState(below);
        if (!level.getFluidState(below).isEmpty() || s.is(BlockTags.LOGS) || !s.isSolid()) return null;
        return below.above();
    }

    /**
     * The wood with the most trees around the village (within 60 blocks of it): every other column is looked at for
     * a trunk, counted in 16-block squares, and she works from open ground in the best one (two trees at least).
     */
    @Nullable
    public static BlockPos forest(ServerLevel level, VillageRecord v, RandomSource r) {
        BoundingBox b = outside(v);
        int reach = 60;
        java.util.Map<Long, Integer> squares = new java.util.HashMap<>();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int x = b.minX() - reach; x <= b.maxX() + reach; x += 2)
            for (int z = b.minZ() - reach; z <= b.maxZ() + reach; z += 2) {
                if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) continue;
                if (!level.hasChunkAt(q.set(x, 0, z))) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockState top = level.getBlockState(q.set(x, y, z));
                if (top.is(BlockTags.LOGS) && !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(top.getBlock()).getPath().startsWith("stripped_")
                        && v.blueprint.expected(q) == null) squares.merge(net.minecraft.world.level.ChunkPos.asLong(x >> 4, z >> 4), 1, Integer::sum);
            }
        List<java.util.Map.Entry<Long, Integer>> best = new ArrayList<>(squares.entrySet());
        best.removeIf(e -> e.getValue() < 1);
        best.sort((a, c) -> c.getValue() - a.getValue());
        List<Integer> y0 = heights(level, v);
        BlockPos from = v.bell() != null ? v.bell() : v.centre();
        for (var e : best) {
            int cx = net.minecraft.world.level.ChunkPos.getX(e.getKey()) * 16 + 8, cz = net.minecraft.world.level.ChunkPos.getZ(e.getKey()) * 16 + 8;
            double dx = cx - from.getX(), dz = cz - from.getZ();
            if (dx * dx + dz * dz > FAR * FAR) continue;            // not a long day's walk away
            for (int i = 0; i < 40; i++) {
                int x = cx + r.nextInt(17) - 8, z = cz + r.nextInt(17) - 8;
                if (taken(v, x, z)) continue;
                BlockPos g = ground(level, x, z);
                if (g != null && reachable(y0, g.getY())) return g;
            }
        }
        return null;
    }

    /** Dry ground a little way outside the village, by a gate if it has walls (she digs away from the village). */
    @Nullable
    public static BlockPos mine(ServerLevel level, VillageRecord v, RandomSource r) {
        List<Integer> y0 = heights(level, v);
        for (int i = 0; i < 60; i++) {
            int x, z;
            if (!v.gates.isEmpty()) {
                VillageRecord.Gate g = v.gates.get(r.nextInt(v.gates.size()));
                double len = Math.max(1, Math.sqrt(g.dx * g.dx + g.dz * g.dz));
                double out = 10 + r.nextInt(12), side = r.nextInt(17) - 8;
                x = (int) (g.pos.getX() + g.dx / len * out - g.dz / len * side);
                z = (int) (g.pos.getZ() + g.dz / len * out + g.dx / len * side);
            } else {
                int[] p = around(v, r, 16);
                x = p[0];
                z = p[1];
            }
            if (taken(v, x, z)) continue;
            BlockPos g = ground(level, x, z);
            if (g != null && reachable(y0, g.getY())) return g;
        }
        return null;
    }

    /** Dry ground on the shore of water near the village (not the fields' channels). */
    @Nullable
    public static BlockPos water(ServerLevel level, VillageRecord v, RandomSource r) {
        List<Integer> y0 = heights(level, v);
        for (int i = 0; i < SAMPLES; i++) {
            int[] p = around(v, r, 48);
            if (!level.hasChunkAt(new BlockPos(p[0], 0, p[1])) || taken(v, p[0], p[1])) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, p[0], p[1]) - 1;
            BlockPos w = new BlockPos(p[0], y, p[1]);
            if (!reachable(y0, y + 1)) continue;
            if (!level.getFluidState(w).is(net.minecraft.tags.FluidTags.WATER)) continue;
            // open water (not a puddle): water a few blocks each way
            int wet = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) if (level.getFluidState(w.relative(d, 3)).is(net.minecraft.tags.FluidTags.WATER)) wet++;
            if (wet < 3) continue;
            for (int rad = 1; rad <= 8; rad++)
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos q = w.relative(d, rad);
                    BlockPos g = ground(level, q.getX(), q.getZ());
                    if (g != null && Math.abs(g.getY() - y) <= 3) return g;
                }
        }
        return null;
    }

    /** Which way is away from the village, from a spot (the way a miner digs). */
    public static Direction away(VillageRecord v, BlockPos at) {
        BlockPos c = v.centre();
        int dx = at.getX() - c.getX(), dz = at.getZ() - c.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }
}

package net.maidens.village;

import net.maidens.politics.KnownVillage;
import net.maidens.politics.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stone walls around a village, planned from its buildings: a line {@link #DISTANCE} blocks out from every
 * building and street, rounded off (no narrow nooks), two blocks thick and 5 high over the ground, with
 * battlements, a tower every so often, and 5-wide gates north, south, east and west and towards each neighbouring
 * village (where the roads between villages come in), each joined to the village's streets.
 * <p>
 * Worked out on a flat grid over the village: the distance of each column to the nearest building (a chamfer
 * distance transform, in thirds of a block), grown out, holes filled, and shrunk back, leaving a smooth outline.
 */
public final class Walls {
    public static final String NAME = "walls";
    /** How far the walls stand from the buildings. */
    static final int DISTANCE = 9;
    private static final int ROUNDING = 16, MARGIN = DISTANCE + ROUNDING + 6, HEIGHT = 5, TOWER_EVERY = 28, GATE_HALF = 2, SEGMENT = 8;
    private static final int INF = Integer.MAX_VALUE / 4;

    private Walls() {}

    /** What was planned, for the self test and the village info. */
    public record Result(int columns, int blocks, int gates, int towers, int pieces, BoundingBox enclosed) {}

    /** Plans the walls and adds them to the village. Returns null if its land isn't all loaded yet. */
    @Nullable
    static Result plan(ServerLevel level, VillageRecord v, boolean instant) {
        // the area: every building and street, with room around them
        List<BoundingBox> boxes = new ArrayList<>();
        for (Blueprint.PieceInfo p : v.blueprint.pieces()) if (!p.name().equals(NAME)) boxes.add(p.box());
        if (boxes.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BoundingBox b : boxes) {
            minX = Math.min(minX, b.minX());
            minZ = Math.min(minZ, b.minZ());
            maxX = Math.max(maxX, b.maxX());
            maxZ = Math.max(maxZ, b.maxZ());
        }
        int x0 = minX - MARGIN, z0 = minZ - MARGIN, w = maxX - minX + 1 + 2 * MARGIN, h = maxZ - minZ + 1 + 2 * MARGIN;
        if (!level.hasChunksAt(x0, z0, x0 + w - 1, z0 + h - 1)) return null;

        // 1. distance to the nearest building, grown out by DISTANCE + ROUNDING
        int[] d = new int[w * h];
        java.util.Arrays.fill(d, INF);
        for (BoundingBox b : boxes)
            for (int x = b.minX(); x <= b.maxX(); x++)
                for (int z = b.minZ(); z <= b.maxZ(); z++) d[(z - z0) * w + (x - x0)] = 0;
        chamfer(d, w, h);
        boolean[] in = new boolean[w * h];
        for (int i = 0; i < in.length; i++) in[i] = d[i] <= 3 * (DISTANCE + ROUNDING);
        // 2. holes filled: whatever can't be reached from the edge of the grid is inside
        boolean[] outside = outside(in, w, h);
        // 3. shrunk back by ROUNDING: a smooth outline DISTANCE out
        int[] e = new int[w * h];
        for (int i = 0; i < e.length; i++) e[i] = outside[i] ? 0 : INF;
        chamfer(e, w, h);
        boolean[] keep = new boolean[w * h];
        for (int i = 0; i < keep.length; i++) keep[i] = e[i] > 3 * ROUNDING;
        // 4. the wall: the outer two blocks of what's left (the outer one gets the battlements)
        int[] f = new int[w * h];
        for (int i = 0; i < f.length; i++) f[i] = keep[i] ? INF : 0;
        chamfer(f, w, h);
        List<Integer> wall = new ArrayList<>();
        for (int i = 0; i < f.length; i++) if (keep[i] && f[i] <= 6) wall.add(i);
        if (wall.isEmpty()) return null;

        // the ground under each wall column (through trees and plants, and down through water)
        Map<Integer, Integer> ground = new HashMap<>();
        Map<Integer, Integer> water = new HashMap<>();
        for (int i : wall) {
            int x = x0 + i % w, z = z0 + i / w;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
            int surface = y;
            for (int n = 0; n < 24; n++) {
                BlockState s = level.getBlockState(p);
                boolean soft = s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced() || !s.getFluidState().isEmpty();
                if (!soft) break;
                p.move(0, -1, 0);
            }
            ground.put(i, p.getY());
            if (!level.getFluidState(new BlockPos(x, surface, z)).isEmpty()) water.put(i, surface);
        }
        // the top follows the ground, evened out over its neighbours
        Map<Integer, Integer> top = new HashMap<>();
        for (int i : wall) {
            int x = i % w, z = i / w;
            long sum = 0;
            int n = 0;
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    int nx = x + dx, nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= w || nz >= h) continue;
                    Integer g = ground.get(nz * w + nx);
                    if (g == null) continue;
                    sum += Math.max(g, water.getOrDefault(nz * w + nx, g));
                    n++;
                }
            int own = Math.max(ground.get(i), water.getOrDefault(i, ground.get(i)));
            top.put(i, Math.max(own + 3, (int) Math.round((double) sum / n) + HEIGHT));
        }

        // gates: where rays from the village go out through the wall (the four directions, and every neighbour)
        Set<Integer> wallSet = new HashSet<>(wall);
        List<int[]> gates = new ArrayList<>();
        BlockPos c = v.centre();
        double[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<double[]> rays = new ArrayList<>();
        for (double[] dir : dirs) rays.add(new double[]{c.getX(), c.getZ(), dir[0], dir[1]});
        Network network = Network.get(level);
        KnownVillage self = network.know(level, v);
        for (KnownVillage o : network.neighbours(self)) {
            double dx = o.x - self.x, dz = o.z - self.z, len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1) rays.add(new double[]{self.x, self.z, dx / len, dz / len});
        }
        for (double[] ray : rays) {
            int[] hit = null;
            for (double t = 0; t < w + h; t += 0.5) {
                int x = (int) Math.floor(ray[0] + ray[2] * t) - x0, z = (int) Math.floor(ray[1] + ray[3] * t) - z0;
                if (x < 0 || z < 0 || x >= w || z >= h) break;
                if (wallSet.contains(z * w + x)) hit = new int[]{x, z};      // keep going: the outermost crossing
            }
            if (hit == null) continue;
            boolean near = false;
            for (int[] g : gates) if (Math.abs(g[0] - hit[0]) + Math.abs(g[1] - hit[1]) < 14) near = true;
            if (!near) gates.add(new int[]{hit[0], hit[1], (int) Math.signum(ray[2] * 4), (int) Math.signum(ray[3] * 4)});
        }
        Set<Integer> gate = new HashSet<>();
        for (int[] g : gates)
            for (int dx = -GATE_HALF - 1; dx <= GATE_HALF + 1; dx++)
                for (int dz = -GATE_HALF - 1; dz <= GATE_HALF + 1; dz++) {
                    int i = (g[1] + dz) * w + (g[0] + dx);
                    if (wallSet.contains(i)) gate.add(i);
                }

        // towers: one per stretch of wall, not by a gate
        List<int[]> towers = new ArrayList<>();
        Map<Long, int[]> perCell = new HashMap<>();
        for (int i : wall) {
            if (f[i] > 4) continue;
            int x = i % w, z = i / w;
            long cell = ((long) (x / TOWER_EVERY) << 32) | (z / TOWER_EVERY);
            int cx = (x / TOWER_EVERY) * TOWER_EVERY + TOWER_EVERY / 2, cz = (z / TOWER_EVERY) * TOWER_EVERY + TOWER_EVERY / 2;
            int[] best = perCell.get(cell);
            int dist = Math.abs(x - cx) + Math.abs(z - cz);
            if (best == null || dist < best[2]) perCell.put(cell, new int[]{x, z, dist});
        }
        for (int[] t : perCell.values()) {
            boolean byGate = false;
            for (int[] g : gates) if (Math.abs(g[0] - t[0]) + Math.abs(g[1] - t[1]) < 10) byGate = true;
            boolean crowded = false;
            for (int[] o : towers) if (Math.abs(o[0] - t[0]) + Math.abs(o[1] - t[1]) < TOWER_EVERY / 2) crowded = true;
            if (!byGate && !crowded) towers.add(t);
        }

        // the blocks
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Set<BlockPos> clear = new HashSet<>();
        for (int i : wall) {
            int x = x0 + i % w, z = z0 + i / w;
            int g = ground.get(i), t = top.get(i);
            boolean isGate = gate.contains(i);
            if (isGate) t = Math.max(t, g + 7);
            for (int y = g + 1; y <= t; y++) {
                BlockPos p = new BlockPos(x, y, z);
                if (isGate && y <= g + 4) {
                    clear.add(p);
                    continue;
                }
                blocks.put(p, Castle.stone(x, y, z));
            }
            if (f[i] <= 4 && (x + z) % 2 == 0) blocks.put(new BlockPos(x, t + 1, z), Blocks.STONE_BRICKS.defaultBlockState());
            if (isGate) for (int y = g + 1; y <= g + 4; y++) clear.add(new BlockPos(x, y, z));
        }
        int towerBlocks = 0;
        for (int[] tw : towers) {
            int i = tw[1] * w + tw[0];
            int g = ground.get(i), t = top.get(i) + 4;
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    int x = x0 + tw[0] + dx, z = z0 + tw[1] + dz;
                    int col = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    int from = Math.min(g, col) + 1;
                    for (int y = from; y <= t; y++) {
                        blocks.put(new BlockPos(x, y, z), Castle.stone(x, y, z));
                        towerBlocks++;
                    }
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (edge && (dx + dz) % 2 == 0) blocks.put(new BlockPos(x, t + 1, z), Blocks.STONE_BRICKS.defaultBlockState());
                    if (!edge && dx == 0 && dz == 0) blocks.put(new BlockPos(x, t + 1, z), Blocks.LANTERN.defaultBlockState());
                }
        }
        for (BlockPos p : blocks.keySet()) clear.remove(p);

        // added in segments (so a lookup or a change near one stretch only touches that stretch)
        Map<Long, Map<BlockPos, BlockState>> segBlocks = new HashMap<>();
        Map<Long, Set<BlockPos>> segClear = new HashMap<>();
        blocks.forEach((p, s) -> segBlocks.computeIfAbsent(segment(p), k -> new LinkedHashMap<>()).put(p, s));
        for (BlockPos p : clear) segClear.computeIfAbsent(segment(p), k -> new HashSet<>()).add(p);
        Set<Long> segs = new HashSet<>(segBlocks.keySet());
        segs.addAll(segClear.keySet());
        int pieces = 0;
        for (long seg : segs) {
            Map<BlockPos, BlockState> sb = segBlocks.getOrDefault(seg, Map.of());
            Set<BlockPos> sc = segClear.getOrDefault(seg, Set.of());
            List<BlockPos> all = new ArrayList<>(sb.keySet());
            all.addAll(sc);
            BoundingBox box = BoundingBox.encapsulatingPositions(all).orElse(null);
            if (box == null) continue;
            v.blueprint.addBuilding(box, sb, sc, NAME, instant);
            pieces++;
        }
        // the gates joined to the streets inside
        for (int[] g : gates) {
            int ix = x0 + g[0] - g[2] * 3, iz = z0 + g[1] - g[3] * 3;
            BlockPos inner = new BlockPos(ix, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz) - 1, iz);
            BoundingBox gateBox = BoundingBox.fromCorners(new BlockPos(x0 + g[0] - 3, inner.getY(), z0 + g[1] - 3), new BlockPos(x0 + g[0] + 3, inner.getY() + 5, z0 + g[1] + 3));
            Buildings.Plan pseudo = new Buildings.Plan(Castle.ID, gateBox, Map.of(), Set.of(), 0, inner);
            Buildings.Plan road = Buildings.road(level, v, pseudo);
            if (road != null) v.blueprint.addBuilding(road.box(), road.blocks(), road.clear(), "road", instant);
        }
        int minWx = Integer.MAX_VALUE, minWz = Integer.MAX_VALUE, maxWx = Integer.MIN_VALUE, maxWz = Integer.MIN_VALUE;
        for (int i : wall) {
            minWx = Math.min(minWx, x0 + i % w);
            maxWx = Math.max(maxWx, x0 + i % w);
            minWz = Math.min(minWz, z0 + i / w);
            maxWz = Math.max(maxWz, z0 + i / w);
        }
        BoundingBox enclosed = new BoundingBox(minWx, level.getMinBuildHeight(), minWz, maxWx, level.getMaxBuildHeight(), maxWz);
        v.wallBox = enclosed;
        Villages.get(level).setDirty();
        return new Result(wall.size(), blocks.size(), gates.size(), towers.size(), pieces, enclosed);
    }

    private static long segment(BlockPos p) {
        return ((long) Math.floorDiv(p.getX(), SEGMENT) << 32) | (Math.floorDiv(p.getZ(), SEGMENT) & 0xFFFFFFFFL);
    }

    /** Two-pass chamfer distance (3 straight, 4 diagonal) from the cells that start at 0. */
    private static void chamfer(int[] d, int w, int h) {
        for (int z = 0; z < h; z++)
            for (int x = 0; x < w; x++) {
                int i = z * w + x, v = d[i];
                if (x > 0) v = Math.min(v, d[i - 1] + 3);
                if (z > 0) {
                    v = Math.min(v, d[i - w] + 3);
                    if (x > 0) v = Math.min(v, d[i - w - 1] + 4);
                    if (x < w - 1) v = Math.min(v, d[i - w + 1] + 4);
                }
                d[i] = v;
            }
        for (int z = h - 1; z >= 0; z--)
            for (int x = w - 1; x >= 0; x--) {
                int i = z * w + x, v = d[i];
                if (x < w - 1) v = Math.min(v, d[i + 1] + 3);
                if (z < h - 1) {
                    v = Math.min(v, d[i + w] + 3);
                    if (x < w - 1) v = Math.min(v, d[i + w + 1] + 4);
                    if (x > 0) v = Math.min(v, d[i + w - 1] + 4);
                }
                d[i] = v;
            }
    }

    /** The cells reachable from the edge of the grid without crossing the area. */
    private static boolean[] outside(boolean[] in, int w, int h) {
        boolean[] out = new boolean[w * h];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            seed(in, out, q, x);
            seed(in, out, q, (h - 1) * w + x);
        }
        for (int z = 0; z < h; z++) {
            seed(in, out, q, z * w);
            seed(in, out, q, z * w + w - 1);
        }
        while (!q.isEmpty()) {
            int i = q.poll(), x = i % w, z = i / w;
            if (x > 0) seed(in, out, q, i - 1);
            if (x < w - 1) seed(in, out, q, i + 1);
            if (z > 0) seed(in, out, q, i - w);
            if (z < h - 1) seed(in, out, q, i + w);
        }
        return out;
    }

    private static void seed(boolean[] in, boolean[] out, ArrayDeque<Integer> q, int i) {
        if (in[i] || out[i]) return;
        out[i] = true;
        q.add(i);
    }

    /** A stretch of wall with a tower, for the picture on its card in the bell screen (its own frame). */
    static Map<BlockPos, BlockState> preview() {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (int x = 0; x < 15; x++)
            for (int z = 4; z <= 5; z++) {
                for (int y = 0; y < HEIGHT; y++) out.put(new BlockPos(x, y, z), Castle.stone(x, y, z));
                if (z == 5 && x % 2 == 0) out.put(new BlockPos(x, HEIGHT, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        for (int x = 5; x <= 9; x++)
            for (int z = 2; z <= 6; z++) {
                for (int y = 0; y < HEIGHT + 4; y++) out.put(new BlockPos(x, y, z), Castle.stone(x, y, z));
                boolean edge = x == 5 || x == 9 || z == 2 || z == 6;
                if (edge && (x + z) % 2 == 0) out.put(new BlockPos(x, HEIGHT + 4, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        return out;
    }
}

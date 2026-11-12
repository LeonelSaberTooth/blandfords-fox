package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The village's field: a stretch of open land outside its walls (by one of its gates) turned into wheat. It isn't a
 * building: the land itself is changed, the way the world makes its own land. Where the field goes, the ground is
 * farmland with ripe wheat on it, following the lie of the land; its outline is ragged, stopping where the land
 * does (water, cliffs, rock, the walls, the village's buildings) and in lobes where it doesn't; the trees on it are
 * gone. Waterways wind across it about every 12 blocks. A path runs from inside the village, out of the gate, to
 * the field and through it, and the village's farmers work along it.
 *
 * <p>Wheat is set down after the farmland, both without the game re-checking blocks around them (so a crop is
 * never knocked off while the light round it is still being worked out).
 */
public final class Farms {
    /** Fields from before (laid out as pieces of the village). */
    public static final String NAME = "field";
    /** Waterways this far apart: farmland is watered up to 4 blocks from water (the middle of each band is dry). */
    public static final int SPACING = 12;
    private static final int MAX = 3200, MIN = 200;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** How the last search went (for the self test). */
    public static String lastSearch = "";

    private Farms() {}

    /** Every 5 s: a village without a field gets one once its walls are planned and the land round it is loaded. */
    static void tick(ServerLevel level, Villages villages) {
        for (VillageRecord v : villages.all()) {
            if (v.farmsPlanned || v.blueprint.pieceCount() == 0) continue;
            if (v.fortified && v.wallBox == null || Fortify.rolls(level, v.id) && !v.fortified) continue;   // walls first
            BlockPos c = v.centre();
            int r = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 100;
            if (!level.hasChunksAt(c.getX() - r, c.getZ() - r, c.getX() + r, c.getZ() + r)) continue;
            plan(level, v);
        }
    }

    /** A field worked out: its farmland (the ground block of each spot), its water, its path, its gate. */
    public static final class Field {
        final Map<Long, Integer> ground = new HashMap<>();   // x,z -> the ground's height
        final Set<Long> water = new HashSet<>(), path = new HashSet<>();
        final Map<Long, Integer> outerPath = new HashMap<>();
        @Nullable VillageRecord.Gate gate;

        public int size() {
            return ground.size();
        }
    }

    /** Lays out the village's field. Returns how many blocks of farmland it has (0 if there was no room). */
    public static int plan(ServerLevel level, VillageRecord v) {
        v.farmsPlanned = true;
        Villages.get(level).setDirty();
        RandomSource r = RandomSource.create(v.id ^ 0xF1E1D5L);
        Field best = null;
        double bestScore = 0;
        int tried = 0;
        for (Seed s : seeds(v)) {
            Field f = grow(level, v, s.x, s.z, r);
            tried++;
            if (f == null || f.size() < MIN) continue;
            f.gate = s.gate;
            double score = f.size() - 8 * s.out;
            if (best == null || score > bestScore) {
                best = f;
                bestScore = score;
            }
        }
        if (best == null) {
            lastSearch = "no field: " + tried + " spots tried, none with room";
            return 0;
        }
        finish(level, v, best, r);
        apply(level, v, best);
        record(v, best);
        lastSearch = "a field of " + best.ground.size() + " farmland, " + best.water.size() + " water, " + best.path.size() + " path in it, "
                + best.outerPath.size() + " path to it (" + tried + " spots tried)";
        return best.ground.size();
    }

    /** For the self test: a field grown from a spot, with a path from a gate (if one's given); the village's if asked. */
    public static Field fieldAt(ServerLevel level, VillageRecord v, int x, int z, @Nullable VillageRecord.Gate gate, boolean theirs) {
        RandomSource r = RandomSource.create(v.id ^ x);
        Field f = grow(level, v, x, z, r);
        if (f == null) return new Field();
        f.gate = gate;
        finish(level, v, f, r);
        apply(level, v, f);
        if (theirs) {
            record(v, f);
            v.farmsPlanned = true;
        }
        return f;
    }

    // ------------------------------------------------------------------ where

    private record Seed(int x, int z, int out, @Nullable VillageRecord.Gate gate) {}

    /** Spots to grow a field from: out from each gate (ahead and to the sides), or round the village. */
    private static List<Seed> seeds(VillageRecord v) {
        List<Seed> out = new ArrayList<>();
        for (VillageRecord.Gate g : v.gates) {
            int dx = Integer.signum(g.dx), dz = Integer.signum(g.dz);
            for (int o : new int[]{10, 20, 32, 46})
                for (int side : new int[]{0, -18, 18}) out.add(new Seed(g.pos.getX() + dx * o - dz * side, g.pos.getZ() + dz * o + dx * side, o, g));
        }
        if (out.isEmpty()) {
            BlockPos c = v.centre();
            double half = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2.0;
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                for (int o : new int[]{12, 28}) out.add(new Seed((int) (c.getX() + Math.cos(a) * (half + o)), (int) (c.getZ() + Math.sin(a) * (half + o)), o, null));
            }
        }
        return out;
    }

    /**
     * The land a field can take from a spot: spreading over open farmable ground that doesn't step more than a block,
     * outside the walls and the village's buildings, out to a ragged edge (further in some directions than others).
     */
    @Nullable
    private static Field grow(ServerLevel level, VillageRecord v, int sx, int sz, RandomSource r) {
        BoundingBox keepOut = (v.wallBox != null ? v.wallBox : v.box).inflatedBy(v.wallBox != null ? 4 : 8);
        // from the spot, or the nearest farmable ground to it
        Integer h0 = null;
        search:
        for (int d = 0; d <= 8; d++)
            for (int dx = -d; dx <= d; dx++)
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                    h0 = farmable(level, v, keepOut, sx + dx, sz + dz);
                    if (h0 != null) {
                        sx += dx;
                        sz += dz;
                        break search;
                    }
                }
        if (h0 == null) return null;
        // the edge: further out in some directions (a few waves round the circle)
        double radius = Math.sqrt(MAX / Math.PI) * 1.15;
        double[] ph = {r.nextDouble() * 6.3, r.nextDouble() * 6.3, r.nextDouble() * 6.3};
        Field f = new Field();
        PriorityQueue<double[]> todo = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        Set<Long> seen = new HashSet<>();
        todo.add(new double[]{0, sx, sz, h0});
        seen.add(key(sx, sz));
        while (!todo.isEmpty() && f.ground.size() < MAX) {
            double[] c = todo.poll();
            int x = (int) c[1], z = (int) c[2], h = (int) c[3];
            f.ground.put(key(x, z), h);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + d[0], nz = z + d[1];
                if (!seen.add(key(nx, nz))) continue;
                Integer nh = farmable(level, v, keepOut, nx, nz);
                if (nh == null || Math.abs(nh - h) > 1) continue;
                double dx = nx - sx, dz = nz - sz, a = Math.atan2(dz, dx);
                double reach = radius * (1 + 0.28 * Math.sin(2 * a + ph[0]) + 0.18 * Math.sin(3 * a + ph[1]) + 0.1 * Math.sin(5 * a + ph[2]));
                double d2 = Math.sqrt(dx * dx + dz * dz) / reach;
                if (d2 > 1) continue;
                todo.add(new double[]{d2, nx, nz, nh});
            }
        }
        return f;
    }

    /** The height of farmable ground at a spot (grass or dirt, dry, open to the sky but for trees), or null. */
    @Nullable
    private static Integer farmable(ServerLevel level, VillageRecord v, BoundingBox keepOut, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        if (x >= keepOut.minX() && x <= keepOut.maxX() && z >= keepOut.minZ() && z <= keepOut.maxZ()) return null;
        int y = ground(level, x, z);
        if (y == Integer.MIN_VALUE) return null;
        BlockPos g = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(g);
        if (!(s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.ROOTED_DIRT))) return null;
        if (!level.getFluidState(g.above()).isEmpty()) return null;
        if (v.blueprint.overlaps(new BoundingBox(x - 1, -64, z - 1, x + 1, 320, z + 1))) return null;
        return y;
    }

    /** The top of the ground at a spot: under trees, plants and snow. MIN_VALUE if it's under something else. */
    static int ground(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        for (int i = 0; i < 40; i++, p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.LOGS) || s.getBlock() instanceof LeavesBlock || s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()
                    || s.is(Blocks.SNOW) || s.is(BlockTags.FLOWERS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.MOSS_CARPET)) continue;
            return p.getY();
        }
        return Integer.MIN_VALUE;
    }

    // ------------------------------------------------------------------ shaping

    /** Tidies the edge, then works out the waterways and the path. */
    private static void finish(ServerLevel level, VillageRecord v, Field f, RandomSource r) {
        // no one-block spurs or slivers along the edge
        for (int pass = 0; pass < 2; pass++) {
            List<Long> thin = new ArrayList<>();
            for (long k : f.ground.keySet()) {
                int n = 0;
                for (long o : around(k)) if (f.ground.containsKey(o)) n++;
                if (n < 2) thin.add(k);
            }
            thin.forEach(f.ground::remove);
        }
        // its long way: waterways run across it, one every SPACING blocks along it, winding a little
        double cx = 0, cz = 0;
        for (long k : f.ground.keySet()) {
            cx += x(k);
            cz += z(k);
        }
        cx /= f.size();
        cz /= f.size();
        double sxx = 0, szz = 0, sxz = 0;
        for (long k : f.ground.keySet()) {
            double dx = x(k) - cx, dz = z(k) - cz;
            sxx += dx * dx;
            szz += dz * dz;
            sxz += dx * dz;
        }
        double th = 0.5 * Math.atan2(2 * sxz, sxx - szz), cos = Math.cos(th), sin = Math.sin(th);
        double phase = r.nextDouble() * 6.3, shift = r.nextDouble() * SPACING;
        for (long k : f.ground.keySet()) {
            double dx = x(k) - cx, dz = z(k) - cz;
            double u = dx * cos + dz * sin + shift, w = -dx * sin + dz * cos;
            long line = Math.round(u / SPACING);
            double wind = 2.2 * Math.sin(w / 7.0 + phase + line * 1.7) + 0.8 * Math.sin(w / 2.9 + line * 2.3);
            if (Math.abs(u - line * SPACING - wind) < 0.75) f.water.add(k);
        }
        // only water that stays where it's put: every side held in at its level
        f.water.removeIf(k -> !holds(level, f, k));
        // the path: from inside the gate, out through it, to the field, and through it the long way
        if (f.gate != null) {
            VillageRecord.Gate g = f.gate;
            int dx = Integer.signum(g.dx), dz = Integer.signum(g.dz);
            int[] inside = {g.pos.getX() - dx * 6, g.pos.getZ() - dz * 6}, out = {g.pos.getX() + dx * 3, g.pos.getZ() + dz * 3};
            long entry = nearest(f, out[0], out[1]);
            // the far end: the field's furthest point the long way from where the path comes in
            double eu = (x(entry) - cx) * cos + (z(entry) - cz) * sin;
            long far = entry;
            double farU = eu;
            for (long k : f.ground.keySet()) {
                double u = (x(k) - cx) * cos + (z(k) - cz) * sin;
                if (eu <= 0 ? u > farU : u < farU) {
                    farU = u;
                    far = k;
                }
            }
            long mid = nearest(f, (int) Math.round(cx), (int) Math.round(cz));
            List<long[]> legs = List.of(new long[]{key(inside[0], inside[1]), key(out[0], out[1])}, new long[]{key(out[0], out[1]), entry},
                    new long[]{entry, mid}, new long[]{mid, far});
            for (long[] leg : legs)
                for (long k : line(leg[0], leg[1])) {
                    if (f.ground.containsKey(k)) {
                        f.path.add(k);
                        f.water.remove(k);
                    } else {
                        int y = ground(level, x(k), z(k));
                        BlockPos p = new BlockPos(x(k), y, z(k));
                        if (y == Integer.MIN_VALUE || v.blueprint.expected(p) != null || v.blueprint.expected(p.above()) != null) continue;
                        BlockState s = level.getBlockState(p);
                        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.ROOTED_DIRT)
                                || s.is(Blocks.MYCELIUM)) f.outerPath.put(k, y);
                    }
                }
        }
    }

    /** Water at a spot stays put: each side has something solid at its level (the field's ground, or the land's). */
    private static boolean holds(ServerLevel level, Field f, long k) {
        int h = f.ground.get(k);
        if (!level.getBlockState(new BlockPos(x(k), h - 1, z(k))).isSolid()) return false;
        for (long o : around(k)) {
            Integer oh = f.ground.get(o);
            if (oh != null) {
                if (oh < h) return false;
                continue;
            }
            BlockPos p = new BlockPos(x(o), h, z(o));
            BlockState s = level.getBlockState(p);
            if (!s.isSolid() || !s.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    /** A line of spots from one to another, with no diagonal gaps (and a second spot alongside: two wide). */
    private static List<Long> line(long from, long to) {
        int x0 = x(from), z0 = z(from), x1 = x(to), z1 = z(to);
        List<Long> out = new ArrayList<>();
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        int px = x0, pz = z0;
        boolean alongX = Math.abs(x1 - x0) >= Math.abs(z1 - z0);
        for (int i = 0; i <= n; i++) {
            int x = n == 0 ? x0 : (int) Math.round(x0 + (x1 - x0) * (double) i / n), z = n == 0 ? z0 : (int) Math.round(z0 + (z1 - z0) * (double) i / n);
            if (x != px && z != pz) out.add(key(x, pz));          // fill the corner
            out.add(key(x, z));
            out.add(alongX ? key(x, z + 1) : key(x + 1, z));
            px = x;
            pz = z;
        }
        return out;
    }

    private static long nearest(Field f, int x, int z) {
        long best = 0;
        double bd = Double.MAX_VALUE;
        for (long k : f.ground.keySet()) {
            double d = Math.pow(x(k) - x, 2) + Math.pow(z(k) - z, 2);
            if (d < bd) {
                bd = d;
                best = k;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ changing the land

    /** Turns the land into the field: trees gone, farmland, water, the path, then the wheat. */
    private static void apply(ServerLevel level, VillageRecord v, Field f) {
        // the trees on it (and just round it)
        Set<Long> near = new HashSet<>();
        for (long k : f.ground.keySet())
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) near.add(key(x(k) + dx, z(k) + dz));
        for (long k : near) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x(k), z(k)) - 1;
            BlockPos p = new BlockPos(x(k), top, z(k));
            if (level.getBlockState(p).is(BlockTags.LOGS)) Trees.fellAt(level, v, p);
        }
        // what grows on the ground (grass, flowers, snow, leaves left over) is cleared off it
        for (Map.Entry<Long, Integer> e : f.ground.entrySet()) {
            BlockPos g = new BlockPos(x(e.getKey()), e.getValue(), z(e.getKey()));
            for (int y = 1; y <= 4; y++) {
                BlockState s = level.getBlockState(g.above(y));
                if (s.isAir()) continue;
                if (s.canBeReplaced() || s.getBlock() instanceof LeavesBlock || s.is(BlockTags.FLOWERS) || s.is(Blocks.SNOW))
                    level.setBlock(g.above(y), Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
        for (Map.Entry<Long, Integer> e : f.ground.entrySet()) {
            long k = e.getKey();
            BlockPos g = new BlockPos(x(k), e.getValue(), z(k));
            if (f.path.contains(k)) level.setBlock(g, Blocks.DIRT_PATH.defaultBlockState(), FLAGS);
            else if (!f.water.contains(k) && level.getBlockState(g.above()).isAir()) level.setBlock(g, farmland, FLAGS);
        }
        for (long k : f.water) level.setBlock(new BlockPos(x(k), f.ground.get(k), z(k)), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (Map.Entry<Long, Integer> e : f.outerPath.entrySet()) {
            BlockPos g = new BlockPos(x(e.getKey()), e.getValue(), z(e.getKey()));
            BlockState above = level.getBlockState(g.above());
            if (!above.isAir() && (above.canBeReplaced() || above.is(Blocks.SNOW))) level.setBlock(g.above(), Blocks.AIR.defaultBlockState(), FLAGS);
            if (level.getBlockState(g.above()).isAir()) level.setBlock(g, Blocks.DIRT_PATH.defaultBlockState(), FLAGS);
        }
        // the wheat, last, ripe
        BlockState wheat = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        for (Map.Entry<Long, Integer> e : f.ground.entrySet()) {
            BlockPos g = new BlockPos(x(e.getKey()), e.getValue(), z(e.getKey()));
            if (level.getBlockState(g).is(Blocks.FARMLAND) && level.getBlockState(g.above()).isAir()) level.setBlock(g.above(), wheat, FLAGS);
        }
    }

    /** The village remembers its field (where it is, its path for the farmers). */
    private static void record(VillageRecord v, Field f) {
        v.fieldCells = f.ground.entrySet().stream().mapToLong(e -> BlockPos.asLong(x(e.getKey()), e.getValue(), z(e.getKey()))).toArray();
        v.fieldPath = f.path.stream().mapToLong(k -> BlockPos.asLong(x(k), f.ground.get(k), z(k))).toArray();
    }

    // ------------------------------------------------------------------ for the farmers

    /** Somewhere for a farmer to work: on the path through the field (or the edge of an old field), or null. */
    @Nullable
    public static BlockPos spot(ServerLevel level, VillageRecord v, RandomSource r) {
        if (v.fieldPath.length > 0) return BlockPos.of(v.fieldPath[r.nextInt(v.fieldPath.length)]).above();
        List<BoundingBox> plots = new ArrayList<>();
        for (Blueprint.PieceInfo p : v.blueprint.pieces()) if (p.name().equals(NAME)) plots.add(p.box());
        if (plots.isEmpty()) return null;
        BoundingBox b = plots.get(r.nextInt(plots.size()));
        int x = r.nextBoolean() ? b.minX() : b.maxX(), z = b.minZ() + r.nextInt(b.getZSpan());
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return null;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** For the self test: {farmland, with ripe wheat, with no crop at all, water, path} over the field's spots. */
    public static int[] count(ServerLevel level, long[] cells) {
        int[] n = new int[5];
        for (long c : cells) {
            BlockPos g = BlockPos.of(c);
            BlockState s = level.getBlockState(g), up = level.getBlockState(g.above());
            if (s.is(Blocks.FARMLAND)) {
                n[0]++;
                if (up.getBlock() instanceof CropBlock crop && crop.isMaxAge(up)) n[1]++;
                if (!(up.getBlock() instanceof CropBlock)) n[2]++;
            } else if (s.is(Blocks.WATER)) n[3]++;
            else if (s.is(Blocks.DIRT_PATH)) n[4]++;
        }
        return n;
    }

    /** The same, for a field the test laid out. */
    public static long[] cells(Field f) {
        return f.ground.entrySet().stream().mapToLong(e -> BlockPos.asLong(x(e.getKey()), e.getValue(), z(e.getKey()))).toArray();
    }

    // ------------------------------------------------------------------ x, z in one

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int x(long k) {
        return (int) (k >> 32);
    }

    private static int z(long k) {
        return (int) k;
    }

    private static long[] around(long k) {
        int x = x(k), z = z(k);
        return new long[]{key(x + 1, z), key(x - 1, z), key(x, z + 1), key(x, z - 1)};
    }
}

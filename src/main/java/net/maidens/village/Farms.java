package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The village's fields: a few big stretches of farmland outside the houses (outside the walls, by a gate, if it has
 * walls), each a patchwork of plots of different sizes with paths between them, its outline ragged where plots were
 * left out (the corners, rough or wet ground, something already there). Mostly wheat, some carrots, potatoes and
 * beetroot, one crop to a plot, each plot watered by a channel down its middle and levelled to its own height
 * (fields on a slope step down it). Fully grown when they're laid out. Like any part of the village, trampled
 * farmland or a crop gone missing is put back by the maidens (a crop as a seedling).
 */
public final class Farms {
    public static final String NAME = "field";
    private static final int TRIES = 160, MIN_PLOTS = 3, ROUGH = 6;
    /** Why plots were left out in the last search: not loaded, taken, wet, rough (for the self test). */
    private static final int[] why = new int[4];
    /** How the last search went (for the self test). */
    public static String lastSearch = "";

    private Farms() {}

    /** Every 5 s: villages without fields get them once the land around them is loaded. */
    static void tick(ServerLevel level, Villages villages) {
        for (VillageRecord v : villages.all()) {
            if (v.farmsPlanned || v.blueprint.pieceCount() == 0) continue;
            if (v.fortified && v.wallBox == null || Fortify.rolls(level, v.id) && !v.fortified) continue;   // walls first
            BlockPos c = v.centre();
            int r = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 90;
            if (!level.hasChunksAt(c.getX() - r, c.getZ() - r, c.getX() + r, c.getZ() + r)) continue;
            plan(level, v);
        }
    }

    /** One plot of a field: where, at what height, which way its channel runs, its crop. */
    private record Plot(BoundingBox box, int base, boolean alongX, Block crop) {}

    /** Lays out the fields. Returns how many plots there are. */
    public static int plan(ServerLevel level, VillageRecord v) {
        v.farmsPlanned = true;
        RandomSource r = RandomSource.create(v.id ^ 0xF1E1D5L);
        BlockPos c = v.centre();
        BoundingBox outside = v.wallBox != null ? v.wallBox : v.box;
        double half = Math.max(outside.getXSpan(), outside.getZSpan()) / 2.0;
        int areas = Math.min(4, 2 + Math.max(1, v.gates.size()));
        List<double[]> ways = new ArrayList<>();
        for (VillageRecord.Gate g : v.gates) ways.add(new double[]{g.pos.getX(), g.pos.getZ(), g.dx, g.dz});
        // fields already laid out (and the village's other pieces) are kept clear of
        List<BoundingBox> taken = new ArrayList<>();
        int made = 0, plots = 0, small = 0;
        java.util.Arrays.fill(why, 0);
        for (int i = 0; i < TRIES && made < areas; i++) {
            // the near edge of the field: out from a gate if there are walls (so the farmers get there), else round the edge
            double x, z, ox, oz;
            if (!ways.isEmpty()) {
                double[] g = ways.get(i % ways.size());
                double len = Math.max(1, Math.sqrt(g[2] * g[2] + g[3] * g[3]));
                ox = g[2] / len;
                oz = g[3] / len;
                double out = 8 + r.nextInt(32), side = r.nextInt(81) - 40;
                x = g[0] + ox * out - oz * side;
                z = g[1] + oz * out + ox * side;
            } else {
                double a = r.nextDouble() * Math.PI * 2, d = half + 6 + r.nextInt(32);
                ox = Math.cos(a);
                oz = Math.sin(a);
                x = c.getX() + ox * d;
                z = c.getZ() + oz * d;
            }
            List<Plot> field = field(level, v, r, (int) x, (int) z, ox, oz, outside, taken);
            if (field.size() < MIN_PLOTS) {
                small++;
                continue;
            }
            add(level, v, field);
            for (Plot p : field) taken.add(p.box.inflatedBy(1));
            made++;
            plots += field.size();
        }
        lastSearch = made + " fields, " + plots + " plots (" + small + " spots too small; plots left out: " + why[0] + " not loaded, "
                + why[1] + " taken, " + why[2] + " wet, " + why[3] + " rough)";
        Villages.get(level).setDirty();
        return plots;
    }

    /**
     * A patchwork from a spot on its near edge, growing away from the village: columns and rows of plots of
     * different sizes, a path between each, the ones that don't fit (or are taken by chance, mostly at the edges)
     * left out.
     */
    private static List<Plot> field(ServerLevel level, VillageRecord v, RandomSource r, int x, int z, double ox, double oz,
                                    BoundingBox village, List<BoundingBox> taken) {
        boolean alongX = r.nextBoolean();          // the plots' long side
        int cols = 3 + r.nextInt(3), rows = 2 + r.nextInt(3);
        int[] across = new int[cols], along = new int[rows];
        for (int i = 0; i < cols; i++) across[i] = 5 + 2 * r.nextInt(3);          // 5, 7 or 9: a channel in the middle
        for (int j = 0; j < rows; j++) along[j] = 9 + r.nextInt(13);
        int wx = 0, wz = 0;
        // in x and z: the plots' sizes, with a block of path between them
        int[] sx = alongX ? along : across, sz = alongX ? across : along;
        for (int s : sx) wx += s + 1;
        for (int s : sz) wz += s + 1;
        // the field lies beyond the spot, away from the village (centred on it across)
        int x0 = Math.abs(ox) > Math.abs(oz) ? (ox > 0 ? x : x - wx) : x - wx / 2;
        int z0 = Math.abs(oz) >= Math.abs(ox) ? (oz > 0 ? z : z - wz) : z - wz / 2;
        int nx = sx.length, nz = sz.length;
        List<Plot> out = new ArrayList<>();
        int px = x0;
        for (int i = 0; i < nx; i++) {
            int pz = z0;
            for (int j = 0; j < nz; j++) {
                BoundingBox b = new BoundingBox(px, 0, pz, px + sx[i] - 1, 0, pz + sz[j] - 1);
                pz += sz[j] + 1;
                // a ragged edge: corners and sides are often left out
                boolean edgeX = i == 0 || i == nx - 1, edgeZ = j == 0 || j == nz - 1;
                if (r.nextFloat() < (edgeX && edgeZ ? 0.4F : edgeX || edgeZ ? 0.2F : 0.04F)) continue;
                if (!level.hasChunksAt(b.minX() - 1, b.minZ() - 1, b.maxX() + 1, b.maxZ() + 1)) { why[0]++; continue; }
                BoundingBox column = new BoundingBox(b.minX(), -64, b.minZ(), b.maxX(), 320, b.maxZ());
                boolean clash = v.blueprint.overlaps(column.inflatedBy(2)) || column.intersects(village.inflatedBy(2));
                for (BoundingBox t : taken) clash |= t.intersects(b.minX() - 1, b.minZ() - 1, b.maxX() + 1, b.maxZ() + 1);
                if (clash) { why[1]++; continue; }
                int[] lie = lie(level, b);
                if (lie == null) { why[2]++; continue; }
                if (lie[1] > ROUGH) { why[3]++; continue; }
                float roll = r.nextFloat();
                Block crop = roll < 0.7F ? Blocks.WHEAT : roll < 0.8F ? Blocks.CARROTS : roll < 0.9F ? Blocks.POTATOES : Blocks.BEETROOTS;
                out.add(new Plot(b, lie[0], alongX, crop));
            }
            px += sx[i] + 1;
        }
        // plots left alone, away from the rest, don't make a field: keep the biggest patch that joins up
        return joined(out);
    }

    /** The biggest group of plots that touch (across a path). */
    private static List<Plot> joined(List<Plot> plots) {
        List<Plot> best = new ArrayList<>();
        Set<Plot> seen = new HashSet<>();
        for (Plot p : plots) {
            if (seen.contains(p)) continue;
            List<Plot> group = new ArrayList<>();
            List<Plot> todo = new ArrayList<>(List.of(p));
            seen.add(p);
            while (!todo.isEmpty()) {
                Plot q = todo.remove(todo.size() - 1);
                group.add(q);
                for (Plot o : plots)
                    if (!seen.contains(o) && o.box.intersects(q.box.minX() - 1, q.box.minZ() - 1, q.box.maxX() + 1, q.box.maxZ() + 1)) {
                        seen.add(o);
                        todo.add(o);
                    }
            }
            if (group.size() > best.size()) best = group;
        }
        return best;
    }

    /** Dry ground: {its height, how uneven it is}, or null if there's water. */
    @Nullable
    private static int[] lie(ServerLevel level, BoundingBox fp) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        long sum = 0;
        int n = 0;
        for (int x = fp.minX(); x <= fp.maxX(); x++)
            for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
                int h = ground(level, x, z);
                if (!level.getFluidState(new BlockPos(x, h, z)).isEmpty()) return null;
                min = Math.min(min, h);
                max = Math.max(max, h);
                sum += h;
                n++;
            }
        return new int[]{(int) Math.round((double) sum / n), max - min};
    }

    /** The top of the ground (not trees). */
    private static int ground(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /** Puts the field in the blueprint (laid out at once): each plot, and the path round it, a piece of its own. */
    private static void add(ServerLevel level, VillageRecord v, List<Plot> field) {
        Set<Long> claimed = new HashSet<>();
        for (Plot p : field)
            for (int x = p.box.minX(); x <= p.box.maxX(); x++)
                for (int z = p.box.minZ(); z <= p.box.maxZ(); z++) claimed.add(BlockPos.asLong(x, 0, z));
        for (Plot p : field) {
            Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
            Set<BlockPos> clear = new HashSet<>();
            BoundingBox fp = p.box;
            int base = p.base;
            int mid = p.alongX ? (fp.minZ() + fp.maxZ()) / 2 : (fp.minX() + fp.maxX()) / 2;
            CropBlock crop = (CropBlock) p.crop;
            for (int x = fp.minX(); x <= fp.maxX(); x++)
                for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
                    BlockPos g = new BlockPos(x, base, z);
                    fill(level, g, blocks);
                    // the channel stops a block short of each end, so the water stays in the plot
                    boolean end = p.alongX ? x == fp.minX() || x == fp.maxX() : z == fp.minZ() || z == fp.maxZ();
                    boolean channel = (p.alongX ? z : x) == mid && !end;
                    if (channel) blocks.put(g, Blocks.WATER.defaultBlockState());
                    else {
                        blocks.put(g, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
                        blocks.put(g.above(), crop.getStateForAge(crop.getMaxAge()));
                    }
                    for (int y = 1; y <= 8; y++) if (channel || y > 1) clear.add(g.above(y));
                }
            // the path round it (where no other plot is), stepping between its height and the ground's
            for (int x = fp.minX() - 1; x <= fp.maxX() + 1; x++)
                for (int z = fp.minZ() - 1; z <= fp.maxZ() + 1; z++) {
                    if (!claimed.add(BlockPos.asLong(x, 0, z))) continue;
                    int y = Math.max(base - 1, Math.min(base + 1, ground(level, x, z)));
                    BlockPos g = new BlockPos(x, y, z);
                    if (v.blueprint.expected(g) != null || !level.getFluidState(g).isEmpty()) continue;
                    fill(level, g, blocks);
                    blocks.put(g, Blocks.DIRT_PATH.defaultBlockState());
                    for (int dy = 1; dy <= 3; dy++) clear.add(g.above(dy));
                }
            v.blueprint.addBuilding(BoundingBox.encapsulatingPositions(new ArrayList<>(blocks.keySet())).orElse(fp), blocks, clear, NAME, true);
        }
    }

    /** The ground under a spot, filled in with dirt where it dips. */
    private static void fill(ServerLevel level, BlockPos g, Map<BlockPos, BlockState> blocks) {
        for (int y = g.getY() - 1; y >= g.getY() - 8; y--) {
            BlockPos b = new BlockPos(g.getX(), y, g.getZ());
            BlockState s = level.getBlockState(b);
            if (!s.canBeReplaced() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) break;
            blocks.put(b, Blocks.DIRT.defaultBlockState());
        }
    }

    /** Somewhere for a farmer to work: on the path at the edge of one of the plots, or null if there are no fields. */
    @Nullable
    public static BlockPos spot(ServerLevel level, VillageRecord v, RandomSource r) {
        List<BoundingBox> plots = new ArrayList<>();
        for (Blueprint.PieceInfo p : v.blueprint.pieces()) if (p.name().equals(NAME)) plots.add(p.box());
        if (plots.isEmpty()) return null;
        for (int i = 0; i < 10; i++) {
            BoundingBox b = plots.get(r.nextInt(plots.size()));
            boolean side = r.nextBoolean();
            int x = side ? b.minX() + r.nextInt(b.getXSpan()) : r.nextBoolean() ? b.minX() : b.maxX();
            int z = side ? (r.nextBoolean() ? b.minZ() : b.maxZ()) : b.minZ() + r.nextInt(b.getZSpan());
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            BlockPos g = new BlockPos(x, ground(level, x, z), z);
            if (level.getFluidState(g).isEmpty()) return g.above();
        }
        return null;
    }

    /** For the self test: crops in the fields, and how many fully grown. */
    public static int[] crops(ServerLevel level, VillageRecord v) {
        int all = 0, ripe = 0;
        for (Blueprint.PieceInfo p : v.blueprint.pieces()) {
            if (!p.name().equals(NAME)) continue;
            BoundingBox b = p.box();
            if (!level.hasChunksAt(b.minX(), b.minZ(), b.maxX(), b.maxZ())) continue;
            for (BlockPos q : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                BlockState s = level.getBlockState(q);
                if (!(s.getBlock() instanceof CropBlock crop)) continue;
                all++;
                if (crop.isMaxAge(s)) ripe++;
            }
        }
        return new int[]{all, ripe};
    }
}

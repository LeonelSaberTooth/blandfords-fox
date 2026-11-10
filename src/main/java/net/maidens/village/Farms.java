package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The village's fields: open farmland outside the houses (outside the walls, by a gate, if it has walls), a few
 * strips of one crop each (wheat, carrots, potatoes or beetroot), watered by a channel down the middle. Put there
 * when the land around the village is loaded; its farmers tend them during work hours ({@link #ripe}), and like
 * any part of the village, trampled farmland or a crop gone missing is put back by the maidens.
 */
public final class Farms {
    public static final String NAME = "field";
    /** One field: 9 across (a channel in the middle, four rows either side), this long. */
    private static final int WIDTH = 9, LENGTH = 15, FIELDS = 3, TRIES = 120;
    /** How the last search went (for the self test). */
    public static String lastSearch = "";
    private static final Block[] CROPS = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};

    private Farms() {}

    /** Every 5 s: villages without fields get them once the land around them is loaded. */
    static void tick(ServerLevel level, Villages villages) {
        for (VillageRecord v : villages.all()) {
            if (v.farmsPlanned || v.blueprint.pieceCount() == 0) continue;
            if (v.fortified && v.wallBox == null || Fortify.rolls(level, v.id) && !v.fortified) continue;   // walls first
            BlockPos c = v.centre();
            int r = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 60;
            if (!level.hasChunksAt(c.getX() - r, c.getZ() - r, c.getX() + r, c.getZ() + r)) continue;
            plan(level, v);
        }
    }

    /** Lays out the fields. Returns how many there are. */
    public static int plan(ServerLevel level, VillageRecord v) {
        v.farmsPlanned = true;
        RandomSource r = RandomSource.create(v.id ^ 0xF1E1D5L);
        BlockPos c = v.centre();
        BoundingBox outside = v.wallBox != null ? v.wallBox : v.box;
        double half = Math.max(outside.getXSpan(), outside.getZSpan()) / 2.0;
        // near a gate if there are walls (so the farmers get there), else anywhere round the edge
        List<double[]> ways = new ArrayList<>();
        for (VillageRecord.Gate g : v.gates) ways.add(new double[]{g.pos.getX(), g.pos.getZ(), g.dx, g.dz});
        int made = 0, unloaded = 0, taken = 0, rough = 0;
        List<Object[]> rougher = new ArrayList<>();
        for (int i = 0; i < TRIES && made < FIELDS; i++) {
            int x, z;
            if (!ways.isEmpty()) {
                double[] g = ways.get(i % ways.size());
                double len = Math.sqrt(g[2] * g[2] + g[3] * g[3]);
                double ox = g[2] / Math.max(1, len), oz = g[3] / Math.max(1, len);
                double out = 16 + r.nextInt(28), side = r.nextInt(41) - 20;
                x = (int) (g[0] + ox * out - oz * side);
                z = (int) (g[1] + oz * out + ox * side);
            } else {
                double a = r.nextDouble() * Math.PI * 2, d = half + 8 + r.nextInt(20);
                x = c.getX() + (int) (Math.cos(a) * d);
                z = c.getZ() + (int) (Math.sin(a) * d);
            }
            boolean alongX = r.nextBoolean();
            int w = alongX ? LENGTH : WIDTH, l = alongX ? WIDTH : LENGTH;
            BoundingBox fp = new BoundingBox(x - w / 2, 0, z - l / 2, x - w / 2 + w - 1, 0, z - l / 2 + l - 1);
            if (!level.hasChunksAt(fp.minX() - 1, fp.minZ() - 1, fp.maxX() + 1, fp.maxZ() + 1)) { unloaded++; continue; }
            BoundingBox column = new BoundingBox(fp.minX(), -64, fp.minZ(), fp.maxX(), 320, fp.maxZ());
            if (v.blueprint.overlaps(column.inflatedBy(2))) { taken++; continue; }
            int[] lie = level(level, fp);
            if (lie == null) { rough++; continue; }
            if (lie[1] > 3) {
                // hilly: kept in case nothing flat turns up (it's levelled: filled in and dug out)
                if (lie[1] <= 8) rougher.add(new Object[]{fp, lie[0], alongX, lie[1]});
                rough++;
                continue;
            }
            add(level, v, fp, lie[0], alongX, CROPS[r.nextInt(CROPS.length)], r);
            made++;
        }
        rougher.sort(java.util.Comparator.comparingInt(o -> (int) o[3]));
        for (Object[] o : rougher) {
            if (made >= FIELDS) break;
            BoundingBox fp = (BoundingBox) o[0];
            if (v.blueprint.overlaps(new BoundingBox(fp.minX(), -64, fp.minZ(), fp.maxX(), 320, fp.maxZ()).inflatedBy(2))) continue;
            add(level, v, fp, (int) o[1], (boolean) o[2], CROPS[r.nextInt(CROPS.length)], r);
            made++;
        }
        lastSearch = made + " fields (" + taken + " spots taken, " + rough + " too rough or wet, " + unloaded + " not loaded)";
        Villages.get(level).setDirty();
        return made;
    }

    /** Dry ground: {its height, how uneven it is}, or null if there's water. */
    private static int[] level(ServerLevel level, BoundingBox fp) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        long sum = 0;
        int n = 0;
        for (int x = fp.minX(); x <= fp.maxX(); x++)
            for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (!level.getFluidState(new BlockPos(x, h, z)).isEmpty()) return null;
                min = Math.min(min, h);
                max = Math.max(max, h);
                sum += h;
                n++;
            }
        return new int[]{(int) Math.round((double) sum / n), max - min};
    }

    private static void add(ServerLevel level, VillageRecord v, BoundingBox fp, int base, boolean alongX, Block crop, RandomSource r) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Set<BlockPos> clear = new HashSet<>();
        int mid = alongX ? (fp.minZ() + fp.maxZ()) / 2 : (fp.minX() + fp.maxX()) / 2;
        for (int x = fp.minX(); x <= fp.maxX(); x++)
            for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
                BlockPos ground = new BlockPos(x, base, z);
                // the ground under the field, filled in where it dips
                for (int y = base - 1; y >= base - 8; y--) {
                    BlockPos b = new BlockPos(x, y, z);
                    if (!level.getBlockState(b).canBeReplaced()) break;
                    blocks.put(b, Blocks.DIRT.defaultBlockState());
                }
                // the channel stops a block short of each end, so the water stays in the field
                boolean end = alongX ? x == fp.minX() || x == fp.maxX() : z == fp.minZ() || z == fp.maxZ();
                boolean channel = (alongX ? z : x) == mid && !end;
                if (channel) blocks.put(ground, Blocks.WATER.defaultBlockState());
                else {
                    blocks.put(ground, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
                    CropBlock cb = (CropBlock) crop;
                    blocks.put(ground.above(), cb.getStateForAge(r.nextInt(cb.getMaxAge() + 1)));
                }
                for (int y = 1; y <= 8; y++) if (channel || y > 1) clear.add(ground.above(y));
            }
        v.blueprint.addBuilding(BoundingBox.encapsulatingPositions(new ArrayList<>(blocks.keySet())).orElse(fp), blocks, clear, NAME, true);
    }

    /** A ripe crop in the village's fields nearest a spot (within 64 blocks), or null. */
    public static BlockPos ripe(ServerLevel level, VillageRecord v, BlockPos near) {
        BlockPos best = null;
        double bestD = 64 * 64;
        for (Blueprint.PieceInfo p : v.blueprint.pieces()) {
            if (!p.name().equals(NAME)) continue;
            BoundingBox b = p.box();
            if (b.getCenter().distSqr(near) > 80 * 80 || !level.hasChunksAt(b.minX(), b.minZ(), b.maxX(), b.maxZ())) continue;
            for (BlockPos q : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                BlockState s = level.getBlockState(q);
                if (!(s.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(s)) continue;
                double d = q.distSqr(near);
                if (d < bestD) {
                    bestD = d;
                    best = q.immutable();
                }
            }
        }
        return best;
    }
}

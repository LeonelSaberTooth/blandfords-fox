package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nine in ten of the trees growing inside a village (inside its walls, or among its houses if it has none) are taken
 * out once, when the village and the land round it are loaded: each tree's trunk and branches and the leaves that
 * hang from them. Only natural trees: a log in a house (or anything the village's blueprint holds) is never touched.
 */
public final class Trees {
    private static final float KEEP = 0.1F;
    private static final int MAX_LOGS = 400, MAX_LEAVES = 3000;
    /** How the last clearing went (for the self test). */
    public static String lastClearing = "";

    private Trees() {}

    /** Every 5 s: villages whose trees haven't been cleared yet, once their walls (if any) are planned and the land is loaded. */
    static void tick(ServerLevel level, Villages villages) {
        for (VillageRecord v : villages.all()) {
            if (v.treesCleared || v.blueprint.pieceCount() == 0) continue;
            if (v.fortified && v.wallBox == null || Fortify.rolls(level, v.id) && !v.fortified) continue;   // walls first
            BoundingBox b = area(v);
            if (!level.hasChunksAt(b.minX() - 8, b.minZ() - 8, b.maxX() + 8, b.maxZ() + 8)) continue;
            clear(level, v);
        }
    }

    private static BoundingBox area(VillageRecord v) {
        return v.wallBox != null ? v.wallBox : v.box;
    }

    /** Takes out nine in ten of the village's trees. Returns {trees found, taken out}. */
    public static int[] clear(ServerLevel level, VillageRecord v) {
        v.treesCleared = true;
        Villages.get(level).setDirty();
        BoundingBox b = area(v);
        RandomSource r = RandomSource.create(v.id ^ 0x7EE5L);
        Set<BlockPos> seen = new HashSet<>();
        int found = 0, felled = 0;
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                BlockPos top = treeTop(level, x, z);
                if (top == null || seen.contains(top)) continue;
                List<BlockPos> logs = trunk(level, top, seen);
                if (logs == null) continue;
                found++;
                if (r.nextFloat() < KEEP) continue;
                fell(level, v, logs);
                felled++;
            }
        lastClearing = found + " trees in the village, " + felled + " taken out";
        return new int[]{found, felled};
    }

    /** For the self test: how many natural trees stand in a village now. */
    public static int count(ServerLevel level, VillageRecord v) {
        BoundingBox b = area(v);
        Set<BlockPos> seen = new HashSet<>();
        int n = 0;
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                BlockPos top = treeTop(level, x, z);
                if (top != null && !seen.contains(top) && trunk(level, top, seen) != null) n++;
            }
        return n;
    }

    /** The highest log in a column, under any leaves, snow and vines on top of it; null if there's none. */
    @org.jetbrains.annotations.Nullable
    static BlockPos treeTop(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        for (int i = 0; i < 48; i++, p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.LOGS)) return p.immutable();
            if (s.isAir() || s.getBlock() instanceof LeavesBlock || s.canBeReplaced() || s.is(Blocks.SNOW) || !s.isSolid()) continue;
            return null;
        }
        return null;
    }

    /** Takes out the natural tree a log belongs to (not a house's beam). Returns whether there was one. */
    public static boolean fellAt(ServerLevel level, VillageRecord v, BlockPos log) {
        List<BlockPos> logs = trunk(level, log.immutable(), new HashSet<>());
        if (logs == null) return false;
        fell(level, v, logs);
        return true;
    }

    /**
     * The logs of a natural tree from one of its logs, or null if it isn't one: a natural tree stands on the ground
     * (dirt, grass...) and has natural leaves on it (a house's beams have neither). Too big ones are left alone.
     */
    @org.jetbrains.annotations.Nullable
    private static List<BlockPos> trunk(ServerLevel level, BlockPos start, Set<BlockPos> seen) {
        List<BlockPos> logs = new ArrayList<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        seen.add(start);
        boolean leaves = false, rooted = false;
        while (!todo.isEmpty()) {
            BlockPos p = todo.poll();
            logs.add(p);
            if (logs.size() > MAX_LOGS) return null;
            BlockState below = level.getBlockState(p.below());
            if (below.is(BlockTags.DIRT) || below.is(Blocks.GRASS_BLOCK) || below.is(BlockTags.SAND) || below.is(Blocks.SNOW_BLOCK)) rooted = true;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = p.offset(dx, dy, dz);
                        BlockState s = level.getBlockState(n);
                        if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) leaves = true;
                        if (s.is(BlockTags.LOGS) && seen.add(n)) todo.add(n);
                    }
        }
        return leaves && rooted ? logs : null;
    }

    /**
     * Takes out a tree: its logs, then the natural leaves no log holds up any more (as the game would let them
     * decay), and with them the snow on them, the vines and the cocoa on it and whatever else hung from it, all
     * without anything dropping. The village no longer expects any of it (a tree that grew inside one of its pieces).
     */
    private static void fell(ServerLevel level, VillageRecord v, List<BlockPos> logs) {
        Set<BlockPos> gone = new HashSet<>(logs);
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : logs) {
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        for (BlockPos p : logs) {
            if (v.blueprint.expected(p) != null) v.blueprint.set(p, Blocks.AIR.defaultBlockState());
            Place.remove(level, p, q -> v.blueprint.expected(q) != null);
            // a bee nest on it goes too
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values())
                if (level.getBlockState(p.relative(d)).is(Blocks.BEE_NEST)) Place.remove(level, p.relative(d), q -> v.blueprint.expected(q) != null);
        }
        // leaves still held up: within 6 steps through leaves of a log that's still there (looked for a bit further out)
        BoundingBox reach = new BoundingBox(x0 - 7, y0 - 3, z0 - 7, x1 + 7, y1 + 8, z1 + 7);
        BoundingBox look = reach.inflatedBy(7);
        java.util.Map<BlockPos, Integer> held = new java.util.HashMap<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        for (BlockPos p : BlockPos.betweenClosed(look.minX(), look.minY(), look.minZ(), look.maxX(), look.maxY(), look.maxZ()))
            if (level.getBlockState(p).is(BlockTags.LOGS)) {
                BlockPos q = p.immutable();
                held.put(q, 0);
                todo.add(q);
            }
        while (!todo.isEmpty()) {
            BlockPos p = todo.poll();
            int d = held.get(p);
            if (d >= 6) continue;
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos q = p.relative(dir);
                if (held.containsKey(q) || !look.isInside(q)) continue;
                if (!(level.getBlockState(q).getBlock() instanceof LeavesBlock)) continue;
                held.put(q, d + 1);
                todo.add(q);
            }
        }
        for (BlockPos p : BlockPos.betweenClosed(reach.minX(), reach.minY(), reach.minZ(), reach.maxX(), reach.maxY(), reach.maxZ())) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT) && !held.containsKey(p)) Place.remove(level, p.immutable(), q -> v.blueprint.expected(q) != null);
        }
        // what was on the ground under it (the dirt a trunk stood on stays; grass grows back)
        for (BlockPos p : logs) {
            BlockPos below = p.below();
            if (!gone.contains(below) && level.getBlockState(below).is(Blocks.DIRT) && level.getBlockState(p).isAir())
                level.setBlock(below, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}

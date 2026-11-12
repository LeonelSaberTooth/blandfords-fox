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
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                // the top of a trunk under the leaves (the ground's top, not counting leaves)
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockState top = level.getBlockState(q.set(x, y, z));
                if (!top.is(BlockTags.LOGS) || seen.contains(q)) continue;
                List<BlockPos> logs = trunk(level, v, q.immutable(), seen);
                if (logs == null) continue;
                found++;
                if (r.nextFloat() < KEEP) continue;
                fell(level, v, logs);
                felled++;
            }
        lastClearing = found + " trees in the village, " + felled + " taken out";
        return new int[]{found, felled};
    }

    /** The logs of a natural tree from one of its logs, or null if it isn't one (a house's, the blueprint's, too big). */
    private static List<BlockPos> trunk(ServerLevel level, VillageRecord v, BlockPos start, Set<BlockPos> seen) {
        List<BlockPos> logs = new ArrayList<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        seen.add(start);
        boolean leaves = false, ours = false;
        while (!todo.isEmpty()) {
            BlockPos p = todo.poll();
            logs.add(p);
            if (v.blueprint.expected(p) != null || logs.size() > MAX_LOGS) ours = true;
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
        return leaves && !ours ? logs : null;
    }

    /** Takes out a tree: its logs, then the natural leaves within reach of them that no other log holds up. */
    private static void fell(ServerLevel level, VillageRecord v, List<BlockPos> logs) {
        Set<BlockPos> gone = new HashSet<>(logs);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (BlockPos p : logs) level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
        // the leaves: from the logs outwards, as far as leaves reach (6), unless another tree's log is close by
        ArrayDeque<BlockPos> todo = new ArrayDeque<>(logs);
        Set<BlockPos> seen = new HashSet<>(logs);
        java.util.Map<BlockPos, Integer> depth = new java.util.HashMap<>();
        for (BlockPos p : logs) depth.put(p, 0);
        int n = 0;
        while (!todo.isEmpty() && n < MAX_LEAVES) {
            BlockPos p = todo.poll();
            int d = depth.get(p);
            if (d >= 6) continue;
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos q = p.relative(dir);
                if (!seen.add(q)) continue;
                BlockState s = level.getBlockState(q);
                if (!(s.getBlock() instanceof LeavesBlock) || s.getValue(LeavesBlock.PERSISTENT) || v.blueprint.expected(q) != null) continue;
                if (heldUp(level, q, gone)) continue;
                level.setBlock(q, Blocks.AIR.defaultBlockState(), flags);
                n++;
                depth.put(q, d + 1);
                todo.add(q);
            }
        }
        // what was on the ground under it (the dirt a trunk stood on stays; grass grows back)
        for (BlockPos p : logs) {
            BlockPos below = p.below();
            if (!gone.contains(below) && level.getBlockState(below).is(Blocks.DIRT) && level.getBlockState(p).isAir())
                level.setBlock(below, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** A log of another tree within two blocks of a leaf. */
    private static boolean heldUp(ServerLevel level, BlockPos leaf, Set<BlockPos> gone) {
        for (BlockPos p : BlockPos.betweenClosed(leaf.offset(-2, -2, -2), leaf.offset(2, 2, 2)))
            if (!gone.contains(p) && level.getBlockState(p).is(BlockTags.LOGS)) return true;
        return false;
    }
}

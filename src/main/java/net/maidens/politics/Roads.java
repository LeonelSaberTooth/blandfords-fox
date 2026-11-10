package net.maidens.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Roads between maiden villages: each village is joined to its two nearest maiden neighbours, unless either is
 * hostile to the other. A road grows by {@link #PER_DAY} blocks a day from each end (its ends joined to each
 * village's streets), but is only laid where the
 * land is loaded (the rest is laid when someone comes by): a 3-wide dirt path over natural ground, plank bridges
 * over water, and a lantern post every 32 blocks. Player-built blocks are never replaced.
 */
public final class Roads {
    private static final int PER_DAY = 160, LAID_PER_TICK = 64, LANTERN_EVERY = 32, NEAREST = 2;

    private Roads() {}

    /** Once a day: new roads between neighbours, and every road grows. */
    static void daily(ServerLevel level, Network n) {
        for (KnownVillage v : n.all()) {
            if (!v.maiden || !n.isVisited(v.id)) continue;
            List<KnownVillage> nb = n.neighbours(v);
            for (int i = 0; i < Math.min(NEAREST, nb.size()); i++) {
                KnownVillage o = nb.get(i);
                if (n.stance(v, o) == Stance.AGGRESSIVE || n.stance(o, v) == Stance.AGGRESSIVE) continue;
                Road r = n.road(v, o);
                if (r == null) {
                    r = n.addRoad(v, o);
                    n.news(level, v.id, "Work began on a road to " + o.name + ".");
                    n.news(level, o.id, "Work began on a road to " + v.name + ".");
                }
            }
        }
        for (Road r : n.roads()) {
            if (r.finished()) continue;
            KnownVillage va = n.village(r.a), vb = n.village(r.b);
            if (va == null || vb == null || n.stance(va, vb) == Stance.AGGRESSIVE || n.stance(vb, va) == Stance.AGGRESSIVE) continue;
            r.fromA += PER_DAY;
            r.fromB += PER_DAY;
            if (r.finished()) {
                n.news(level, r.a, "The road to " + vb.name + " is finished.");
                n.news(level, r.b, "The road to " + va.name + " is finished.");
            }
            n.setDirty();
        }
    }

    /** Every 5 s: lays built stretches that are loaded now. */
    static void tick(ServerLevel level, Network n) {
        int budget = LAID_PER_TICK;
        for (Road r : n.roads()) {
            int len = r.length();
            for (int i = 0; i < len && budget > 0; i++) {
                boolean built = i < r.fromA || i >= len - r.fromB;
                if (!built || r.laid.get(i)) continue;
                int[] xz = r.step(i);
                if (!level.hasChunkAt(new BlockPos(xz[0], 0, xz[1]))) continue;
                lay(level, r, xz[0], xz[1], i);
                r.laid.set(i);
                // its first and last steps are joined to the village's own streets
                if (i == 0 || i == len - 1) {
                    net.maidens.village.VillageRecord v = net.maidens.village.Villages.get(level).byId(i == 0 ? r.a : r.b);
                    if (v != null) net.maidens.village.Buildings.joinToStreets(level, v, xz[0], xz[1]);
                }
                budget--;
                n.setDirty();
            }
        }
    }

    /** One step of road: the middle and one block either side. */
    private static void lay(ServerLevel level, Road r, int x, int z, int i) {
        boolean alongX = Math.abs(r.bx - r.ax) >= Math.abs(r.bz - r.az);
        for (int side = -1; side <= 1; side++) {
            int px = alongX ? x : x + side, pz = alongX ? z + side : z;
            if (!level.hasChunkAt(new BlockPos(px, 0, pz))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz) - 1;
            BlockPos ground = new BlockPos(px, top, pz);
            BlockState g = level.getBlockState(ground);
            if (!g.getFluidState().isEmpty()) {
                level.setBlock(ground, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);      // a bridge
            } else if (g.is(BlockTags.DIRT) || g.is(BlockTags.SAND) || g.is(Blocks.GRAVEL) || g.is(BlockTags.SNOW)) {
                clearAbove(level, ground);
                level.setBlock(ground, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        if (i % LANTERN_EVERY == LANTERN_EVERY / 2) {
            int px = alongX ? x : x + 2, pz = alongX ? z + 2 : z;
            if (!level.hasChunkAt(new BlockPos(px, 0, pz))) return;
            BlockPos post = new BlockPos(px, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz), pz);
            BlockState below = level.getBlockState(post.below());
            if (below.isFaceSturdy(level, post.below(), Direction.UP) && below.getFluidState().isEmpty()
                    && level.getBlockState(post).canBeReplaced() && level.getBlockState(post.above()).canBeReplaced()) {
                level.setBlock(post, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(post.above(), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Plants and snow on the path go (so the path block stays a path). */
    private static void clearAbove(ServerLevel level, BlockPos ground) {
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos p = ground.above(dy);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.canBeReplaced() && s.getFluidState().isEmpty()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}

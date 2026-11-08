package net.maidens.maiden.work;

import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cuts down whole trees within 14 blocks of her work spot, lowest log first, and replants a sapling from her bag. */
final class WoodTask extends WorkTask {
    private static final ItemStack AXE = new ItemStack(Items.IRON_AXE);
    private final List<BlockPos> tree = new ArrayList<>();
    private final Set<BlockPos> unreachable = new HashSet<>();
    private BlockPos base;

    WoodTask(Maiden m) {
        super(m);
    }

    @Override
    public void reset() {
        super.reset();
        tree.clear();
        unreachable.clear();
        base = null;
    }

    @Override
    public void tick(ServerLevel level) {
        if (busy > 0) { busy--; return; }
        if (tree.isEmpty()) {
            if (base != null) replant(level, base);
            base = null;
            if (--scan > 0) return;
            scan = 40;
            BlockPos found = find(level);
            if (found == null) { idleNear(job().workPos); return; }
            base = found;
            collect(level, found);
            stuck = 0;
        }
        if (stuck > 300) {                       // can't get there: try another tree
            unreachable.add(base);
            tree.clear();
            base = null;
            return;
        }
        BlockPos log = tree.get(0);
        if (!level.getBlockState(log).is(BlockTags.LOGS)) { tree.remove(0); return; }
        double dx = m.getX() - (base.getX() + 0.5), dz = m.getZ() - (base.getZ() + 0.5);
        boolean atTrunk = dx * dx + dz * dz <= 2.5 * 2.5 && log.getY() - m.getY() <= 7;
        if (!atTrunk && m.distanceToSqr(Vec3.atCenterOf(log)) > 4.5 * 4.5) {
            reach(base, 2.2);
            return;
        }
        m.getNavigation().stop();
        m.getLookControl().setLookAt(Vec3.atCenterOf(log));
        harvest(level, log, AXE, false);
        tree.remove(0);
        busy = 12;
    }

    private BlockPos find(ServerLevel level) {
        BlockPos c = centre();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-14, -3, -14), c.offset(14, 6, 14))) {
            if (!level.getBlockState(p).is(BlockTags.LOGS) || unreachable.contains(p)) continue;
            BlockState below = level.getBlockState(p.below());
            if (!below.is(BlockTags.DIRT)) continue;                      // a trunk standing on the ground, not a house beam
            if (!level.getBlockState(p.above()).is(BlockTags.LOGS)) continue;
            double d = p.distSqr(m.blockPosition());
            if (d < bestD) { bestD = d; best = p.immutable(); }
        }
        return best;
    }

    /** Every log joined to the trunk (diagonals too), lowest first, up to a big tree's worth. */
    private void collect(ServerLevel level, BlockPos start) {
        tree.clear();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(start);
        seen.add(start);
        while (!open.isEmpty() && tree.size() < 96) {
            BlockPos p = open.poll();
            tree.add(p);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 0; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos q = p.offset(dx, dy, dz);
                        if (Math.abs(q.getX() - start.getX()) > 6 || Math.abs(q.getZ() - start.getZ()) > 6) continue;
                        if (seen.add(q) && level.getBlockState(q).is(BlockTags.LOGS)) open.add(q);
                    }
        }
        tree.sort(Comparator.comparingInt(BlockPos::getY));
    }

    private void replant(ServerLevel level, BlockPos at) {
        if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).is(BlockTags.DIRT)) return;
        ItemStack sapling = job().takeFromBag(st -> st.is(ItemTags.SAPLINGS));
        if (sapling.getItem() instanceof BlockItem bi) {
            level.setBlock(at, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
            m.swing(InteractionHand.MAIN_HAND);
        }
    }

    @Override
    public String debug() {
        return "tree " + tree.size() + " base " + (base == null ? "-" : base.toShortString()) + " " + super.debug();
    }
}

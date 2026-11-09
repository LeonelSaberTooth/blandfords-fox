package net.maidens.maiden.work;

import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cuts down whole trees within 24 blocks of where she was hired: every log, lowest first, then the leaves (for their
 * saplings, sticks and apples), and replants a sapling where the tree stood. Then the next tree.
 */
final class WoodTask extends WorkTask {
    private static final ItemStack AXE = new ItemStack(Items.IRON_AXE);
    private static final int RANGE = 24;
    private final List<BlockPos> tree = new ArrayList<>();
    private final List<BlockPos> leaves = new ArrayList<>();
    private final Set<BlockPos> unreachable = new HashSet<>();
    private BlockPos base;
    /** The kind of log the tree was made of, to replant the same tree if she has no sapling. */
    private Block wood = Blocks.OAK_LOG;

    WoodTask(Maiden m) {
        super(m);
    }

    @Override
    public void reset() {
        super.reset();
        tree.clear();
        leaves.clear();
        unreachable.clear();
        base = null;
    }

    @Override
    public void tick(ServerLevel level) {
        if (busy > 0) { busy--; return; }
        if (checkBag()) return;
        if (tree.isEmpty() && !leaves.isEmpty()) {
            // the leaves of the felled tree: all of them, quickly, from wherever she stands
            for (int i = 0; i < 6 && !leaves.isEmpty(); i++) {
                BlockPos leaf = leaves.remove(leaves.size() - 1);
                if (level.getBlockState(leaf).is(BlockTags.LEAVES)) harvest(level, leaf, AXE, false);
            }
            busy = 2;
            return;
        }
        if (tree.isEmpty()) {
            if (base != null) replant(level, base);
            base = null;
            if (--scan > 0) return;
            scan = 40;
            BlockPos found = find(level);
            if (found == null) { idleNear(job().workPos); return; }
            base = found;
            wood = level.getBlockState(found).getBlock();
            collect(level, found);
            stuck = 0;
        }
        if (stuck > 400) {                       // can't get there: try another tree
            unreachable.add(base);
            tree.clear();
            leaves.clear();
            base = null;
            return;
        }
        BlockPos log = tree.get(0);
        if (!level.getBlockState(log).is(BlockTags.LOGS)) { tree.remove(0); return; }
        // standing at the trunk she can fell the whole tree, however tall (she climbs it, as far as anyone can tell)
        double dx = m.getX() - (base.getX() + 0.5), dz = m.getZ() - (base.getZ() + 0.5);
        boolean atTrunk = dx * dx + dz * dz <= 3.5 * 3.5 && Math.abs(m.getY() - base.getY()) <= 3;
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
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-RANGE, -4, -RANGE), c.offset(RANGE, 8, RANGE))) {
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
        // the leaves around those logs (natural ones only, not a player's hedge)
        leaves.clear();
        Set<BlockPos> logs = new HashSet<>(tree);
        Set<BlockPos> seenLeaves = new HashSet<>();
        for (BlockPos l : logs) {
            for (BlockPos q : BlockPos.betweenClosed(l.offset(-3, -1, -3), l.offset(3, 3, 3))) {
                BlockState s = level.getBlockState(q);
                if (s.is(BlockTags.LEAVES) && !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT))
                        && seenLeaves.add(q.immutable())) leaves.add(q.immutable());
            }
        }
    }

    private void replant(ServerLevel level, BlockPos at) {
        if (!level.getBlockState(at).canBeReplaced() || !level.getBlockState(at.below()).is(BlockTags.DIRT)) return;
        // the same kind of tree: its own sapling from her bag, else any sapling she has, else one of her own
        ItemStack same = saplingFor(wood);
        ItemStack sapling = same.isEmpty() ? ItemStack.EMPTY : job().takeFromBag(st -> st.is(same.getItem()));
        if (sapling.isEmpty()) sapling = job().takeFromBag(st -> st.is(ItemTags.SAPLINGS));
        if (sapling.isEmpty()) sapling = same;
        if (sapling.getItem() instanceof BlockItem bi) {
            level.setBlock(at, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
            m.swing(InteractionHand.MAIN_HAND);
        }
    }

    /** oak_log -> oak_sapling and so on (mangrove -> mangrove_propagule); nothing for logs with no sapling. */
    private static ItemStack saplingFor(Block log) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(log);
        String path = id.getPath().replace("stripped_", "").replace("_log", "").replace("_wood", "");
        for (String suffix : new String[]{"_sapling", "_propagule"}) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path + suffix));
            if (item != Items.AIR) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public String debug() {
        return "tree " + tree.size() + " base " + (base == null ? "-" : base.toShortString()) + " " + super.debug();
    }
}

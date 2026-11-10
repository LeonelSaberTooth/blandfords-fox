package net.maidens.maiden.work;

import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Harvests ripe crops within 24 blocks of where she was hired and replants them; plants every bare farmland too,
 * with seeds from her bag (wheat seeds of her own if she has none).
 */
final class FarmTask extends WorkTask {
    private static final int RANGE = 24, QUEUE = 96;
    private BlockPos target;
    /** The crops and bare farmland found by the last look around, worked through nearest first (one look, many crops). */
    private final java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();

    FarmTask(Maiden m) {
        super(m);
    }

    @Override
    public void reset() {
        super.reset();
        target = null;
        queue.clear();
    }

    @Override
    public void tick(ServerLevel level) {
        if (busy > 0) { busy--; return; }
        if (checkBag()) return;
        if (target == null || stuck > 300) {
            stuck = 0;
            target = next(level);
            if (target == null) {
                if (--scan > 0) return;
                queue.addAll(find(level));
                target = next(level);
                if (target == null) { scan = IDLE_SCAN; idleNear(job().workPos); return; }
            }
        }
        if (!needsWork(level, target)) { target = null; return; }
        if (!reach(target, 2.6)) return;
        // everything in reach gets done while she's here: a whole row goes quickly
        int done = 0;
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-1, -1, -1), target.offset(1, 1, 1))) {
            if (done >= 3 || !needsWork(level, p) || m.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)) > 2.6 * 2.6 + 1) continue;
            work(level, p.immutable());
            done++;
        }
        target = null;
        busy = 4;
    }

    private static boolean needsWork(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        boolean ripe = s.getBlock() instanceof CropBlock c && c.isMaxAge(s);
        return ripe || s.isAir() && level.getBlockState(p.below()).getBlock() instanceof FarmBlock;
    }

    private void work(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof CropBlock c && c.isMaxAge(s)) {
            harvest(level, p, ItemStack.EMPTY, true);
            return;
        }
        ItemStack seed = job().takeFromBag(FarmTask::isSeed);
        if (seed.isEmpty()) seed = new ItemStack(Items.WHEAT_SEEDS);
        if (seed.getItem() instanceof BlockItem bi) {
            level.setBlock(p, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
            m.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, p, SoundEvents.CROP_PLANTED, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }

    /** The queued spot nearest to her that still needs work. */
    private BlockPos next(ServerLevel level) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (java.util.Iterator<BlockPos> it = queue.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            if (!level.isLoaded(p) || !needsWork(level, p)) { it.remove(); continue; }
            double d = p.distSqr(m.blockPosition());
            if (d < bestD) { bestD = d; best = p; }
        }
        if (best != null) queue.remove(best);
        return best;
    }

    private static boolean isSeed(ItemStack st) {
        return st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
    }

    private java.util.List<BlockPos> find(ServerLevel level) {
        BlockPos c = centre();
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        if (!loaded(level, c, RANGE)) return out;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-RANGE, -4, -RANGE), c.offset(RANGE, 4, RANGE))) {
            if (!needsWork(level, p)) continue;
            out.add(p.immutable());
            if (out.size() >= QUEUE) break;
        }
        return out;
    }

    @Override
    public String debug() {
        return "target " + (target == null ? "-" : target.toShortString()) + " " + super.debug();
    }
}

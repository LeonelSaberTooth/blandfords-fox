package net.maidens.maiden.work;

import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Harvests ripe crops within 12 blocks of her work spot (her house) and replants; plants seeds from her bag on bare farmland. */
final class FarmTask extends WorkTask {
    private BlockPos target;

    FarmTask(Maiden m) {
        super(m);
    }

    @Override
    public void reset() {
        super.reset();
        target = null;
    }

    @Override
    public void tick(ServerLevel level) {
        if (busy > 0) { busy--; return; }
        if (target == null || stuck > 200) {
            stuck = 0;
            if (--scan > 0) return;
            scan = 40;
            target = find(level);
            if (target == null) { idleNear(job().workPos); return; }
        }
        BlockState s = level.getBlockState(target);
        boolean ripe = s.getBlock() instanceof CropBlock c && c.isMaxAge(s);
        boolean bare = s.isAir() && level.getBlockState(target.below()).getBlock() instanceof FarmBlock;
        if (!ripe && !bare) { target = null; return; }
        if (!reach(target, 2.6)) return;
        if (ripe) harvest(level, target, ItemStack.EMPTY, true);
        else {
            ItemStack seed = job().takeFromBag(FarmTask::isSeed);
            if (seed.getItem() instanceof BlockItem bi) {
                level.setBlock(target, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
                m.swing(InteractionHand.MAIN_HAND);
                level.playSound(null, target, SoundEvents.CROP_PLANTED, SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }
        target = null;
        busy = 10;
    }

    private static boolean isSeed(ItemStack st) {
        return st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
    }

    private BlockPos find(ServerLevel level) {
        BlockPos c = centre();
        boolean seeds = job().bagHas(FarmTask::isSeed);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-12, -3, -12), c.offset(12, 3, 12))) {
            BlockState s = level.getBlockState(p);
            boolean ripe = s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s);
            boolean bare = seeds && s.isAir() && level.getBlockState(p.below()).getBlock() instanceof FarmBlock;
            if (!ripe && !bare) continue;
            double d = p.distSqr(m.blockPosition());
            if (d < bestD) { bestD = d; best = p.immutable(); }
        }
        return best;
    }

    @Override
    public String debug() {
        return "target " + (target == null ? "-" : target.toShortString()) + " " + super.debug();
    }
}

package net.maidens.maiden.work;

import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Digs a staircase down from her work spot in the direction her employer faced (3 high, one step at a time) to y 12
 * or 40 steps, then a 2-high tunnel 32 long. Ores touching it come out too; torches from her bag every 8 steps;
 * holes in the floor filled with blocks from her bag. Stops at water or lava, unbreakable blocks, or a full bag.
 */
final class MineTask extends WorkTask {
    private static final ItemStack PICK = new ItemStack(Items.IRON_PICKAXE);
    private static final int STAIRS = 40, TUNNEL = 32, FLOOR_Y = 12;

    MineTask(Maiden m) {
        super(m);
    }

    @Override
    public void tick(ServerLevel level) {
        if (busy > 0) { busy--; return; }
        if (job().workPos == null) { job().giveUp("I don't know where to dig."); return; }
        if (job().bagFull()) { job().giveUp("My bag's full! Come and empty it, then hire me again."); return; }
        int step = job().mineStep + 1;
        int stairs = stairsDown();
        if (step > stairs + TUNNEL) { job().giveUp("I've finished the tunnel! Come and see what I found."); return; }
        Direction d = job().workDir;
        BlockPos feet = job().workPos.relative(d, step).below(Math.min(step, stairs));
        BlockPos[] clear = step <= stairs ? new BlockPos[]{feet, feet.above(), feet.above(2)} : new BlockPos[]{feet, feet.above()};
        BlockPos stand = job().workPos.relative(d, step - 1).below(Math.min(step - 1, stairs));    // the last cleared step
        if (!reach(stand, 1.6)) {
            if (stuck > 400) job().giveUp("I can't get back into the mine.");
            return;
        }
        for (BlockPos p : clear) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) continue;
            if (nearFluid(level, p)) { job().giveUp("There's water or lava ahead, so I stopped digging."); return; }
            float hardness = s.getDestroySpeed(level, p);
            if (hardness < 0 || hardness > 30) { job().giveUp("I hit something too hard to dig through."); return; }
            m.getLookControl().setLookAt(Vec3.atCenterOf(p));
            harvest(level, p, PICK, false);
            veins(level, p, 0);
            busy = 8 + (int) (hardness * 6);
            return;
        }
        // the step is open: make sure there's a floor, light it now and then, move on
        BlockPos hole = feet.below();
        BlockState floor = level.getBlockState(hole);
        if (!floor.isSolid() || !floor.getFluidState().isEmpty()) {
            if (nearFluid(level, hole)) { job().giveUp("There's water or lava ahead, so I stopped digging."); return; }
            ItemStack block = job().takeFromBag(st -> st.getItem() instanceof BlockItem bi
                    && bi.getBlock().defaultBlockState().isCollisionShapeFullBlock(level, hole) && !(bi.getBlock() instanceof EntityBlock));
            if (block.isEmpty()) { job().giveUp("There's a hole I can't fill. Put some cobblestone or dirt in my bag."); return; }
            level.setBlock(hole, ((BlockItem) block.getItem()).getBlock().defaultBlockState(), Block.UPDATE_ALL);
        }
        if (step % 8 == 0 && level.getBlockState(feet).isAir() && level.getBlockState(hole).isSolid()) {
            if (!job().takeFromBag(st -> st.is(Items.TORCH)).isEmpty()) level.setBlock(feet, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
        }
        job().mineStep = step;
    }

    private int stairsDown() {
        return Math.max(0, Math.min(STAIRS, job().workPos.getY() - FLOOR_Y));
    }

    private static boolean nearFluid(ServerLevel level, BlockPos p) {
        if (!level.getFluidState(p).isEmpty()) return true;
        for (Direction d : Direction.values()) if (!level.getFluidState(p.relative(d)).isEmpty()) return true;
        return false;
    }

    /** Ores touching the tunnel come out too. */
    private void veins(ServerLevel level, BlockPos p, int depth) {
        if (depth > 6) return;
        for (Direction d : Direction.values()) {
            BlockPos q = p.relative(d);
            if (level.getBlockState(q).is(Tags.Blocks.ORES) && !nearFluid(level, q)) {
                harvest(level, q, PICK, false);
                veins(level, q, depth + 1);
            }
        }
    }

    @Override
    public String debug() {
        return "step " + job().mineStep + " " + super.debug();
    }
}

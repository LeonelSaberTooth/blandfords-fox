package net.maidens.maiden.work;

import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * One kind of work, ticked by {@link WorkGoal} while it's her job. Holds the shared skills:
 * walking to a block, breaking it into her bag, and loitering near her work spot.
 * A subclass keeps its own progress and resets it in {@link #reset}.
 */
public abstract class WorkTask {
    /** How long to wait before looking around again when there was nothing to do (a scan reads thousands of blocks). */
    protected static final int IDLE_SCAN = 100;

    protected final Maiden m;
    protected int busy, scan, repath, stuck;

    protected WorkTask(Maiden m) {
        this.m = m;
    }

    /** Her employment. Looked up each time: goals are made before the maiden's own fields exist. */
    protected Employment job() {
        return m.employment();
    }

    /** One tick of work (server side). */
    public abstract void tick(ServerLevel level);

    /** Starting fresh (a new job or a new shift). */
    public void reset() {
        busy = scan = repath = stuck = 0;
    }

    /** She stopped this work for now (talking, a new job...): tidy up anything left in the world. */
    public void stop() {
    }

    /** A short description of what she's doing, for the self test. */
    public String debug() {
        return "stuck " + stuck + " busy " + busy;
    }

    /** What she's walking to, and for how long: after {@link #PATIENCE} ticks she gives it up (stuck goes high). */
    private BlockPos reachTarget;
    private int reachTicks;
    protected static final int PATIENCE = 600, GIVE_UP = 1000;

    /** Walks towards a block until within reach; true once there (then she looks at it). */
    protected boolean reach(BlockPos p, double reach) {
        Vec3 c = Vec3.atCenterOf(p);
        if (!p.equals(reachTarget)) {
            reachTarget = p.immutable();
            reachTicks = 0;
        }
        if (m.distanceToSqr(c) <= reach * reach) {
            m.getNavigation().stop();
            m.getLookControl().setLookAt(c.x, c.y, c.z);
            stuck = 0;
            reachTicks = 0;
            return true;
        }
        // walking for half a minute without getting there (going round in circles, a path that never ends): give up
        if (++reachTicks > PATIENCE) {
            stuck = GIVE_UP;
            reachTicks = 0;
            m.getNavigation().stop();
            return false;
        }
        if (--repath <= 0) {
            repath = 20;
            boolean moving = m.getNavigation().moveTo(c.x, p.getY(), c.z, 0.6);
            if (!moving && m.distanceToSqr(c) <= (reach + 1.0) * (reach + 1.0)) {
                // as close as the pathfinder will take her: close enough
                m.getLookControl().setLookAt(c.x, c.y, c.z);
                stuck = 0;
                return true;
            }
            if (!moving || m.getNavigation().isStuck()) stuck += 20;
        }
        return false;
    }

    /** Breaks a block as if with the tool, puts the drops in her bag; replants a harvested crop from its own seeds. */
    protected void harvest(ServerLevel level, BlockPos p, ItemStack tool, boolean replantCrop) {
        BlockState state = level.getBlockState(p);
        List<ItemStack> drops = Block.getDrops(state, level, p, level.getBlockEntity(p), m, tool);
        level.destroyBlock(p, false, m);
        m.swing(InteractionHand.MAIN_HAND);
        if (replantCrop && state.getBlock() instanceof CropBlock crop) {
            for (ItemStack d : drops) {
                if (d.getItem() instanceof BlockItem bi && bi.getBlock() == crop) {
                    d.shrink(1);
                    level.setBlock(p, crop.getStateForAge(0), Block.UPDATE_ALL);
                    break;
                }
            }
        }
        for (ItemStack d : drops) job().store(d);
    }

    /** Whether everything within r blocks of c is loaded: scanning unloaded land would load it from disk. */
    protected static boolean loaded(ServerLevel level, BlockPos c, int r) {
        return level.hasChunksAt(c.getX() - r, c.getZ() - r, c.getX() + r, c.getZ() + r);
    }

    /** Her bag is full: off to the marked chest (or she has to stop). True if so. */
    protected boolean checkBag() {
        if (!job().bagFull()) return false;
        job().bagFullDuringWork();
        return true;
    }

    /** Wanders back towards a spot when there's nothing to do. */
    protected void idleNear(BlockPos p) {
        if (p != null && m.distanceToSqr(Vec3.atCenterOf(p)) > 36 && m.getNavigation().isDone()) {
            m.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.5);
        }
    }

    protected BlockPos centre() {
        return job().workPos != null ? job().workPos : m.blockPosition();
    }
}

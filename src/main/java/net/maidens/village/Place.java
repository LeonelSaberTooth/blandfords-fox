package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.function.Predicate;

/**
 * Putting blocks in (and taking them out) the way the world puts in a structure: nothing pops off and drops.
 * <ul>
 * <li>A block that joins up with what's round it (glass panes, iron bars, fences, walls, stairs) is shaped to fit
 * its neighbours as it goes in, and those neighbours of the kind are reshaped to fit it.</li>
 * <li>Anything natural next to it that can't stay there any more (grass and flowers on ground that's gone, snow on
 * leaves that are gone, vines on a trunk that's gone, and whatever hung from those) just goes, without dropping.</li>
 * <li>Nothing else round it is disturbed (a torch put in before its wall isn't knocked off).</li>
 * </ul>
 */
public final class Place {
    public static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int MAX_TIDY = 512;

    private Place() {}

    /** Puts a block in. Blocks the keep test says are wanted where they are (a village's own) are never tidied away. */
    public static void set(ServerLevel level, BlockPos pos, BlockState state, @Nullable Predicate<BlockPos> keep) {
        if (joins(state)) state = Block.updateFromNeighbourShapes(state, level, pos);
        level.setBlock(pos, state, QUIET);
        after(level, pos, state, keep);
    }

    /** Takes a block out (air, or water if it was in water). */
    public static void remove(ServerLevel level, BlockPos pos, @Nullable Predicate<BlockPos> keep) {
        BlockState was = level.getBlockState(pos);
        BlockState air = was.getFluidState().isSource() && !was.getFluidState().isEmpty() ? was.getFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState();
        level.setBlock(pos, air, QUIET);
        after(level, pos, air, keep);
    }

    private static void after(ServerLevel level, BlockPos pos, BlockState state, @Nullable Predicate<BlockPos> keep) {
        tidy(level, pos, keep);
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState ns = level.getBlockState(n);
            if (!joins(ns)) continue;
            BlockState fit = ns.updateShape(d.getOpposite(), state, level, n, pos);
            if (fit != ns && !fit.isAir()) level.setBlock(n, fit, QUIET);
        }
    }

    /** Natural things round a spot that can't stay any more go, quietly, and what depended on them in turn. */
    public static void tidy(ServerLevel level, BlockPos from, @Nullable Predicate<BlockPos> keep) {
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(from.immutable());
        int n = 0;
        while (!todo.isEmpty() && n < MAX_TIDY) {
            BlockPos p = todo.poll();
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                BlockState s = level.getBlockState(q);
                if (s.isAir() || s.canSurvive(level, q) || keep != null && keep.test(q)) continue;
                BlockState air = s.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : s.getFluidState().createLegacyBlock();
                level.setBlock(q, air, QUIET);
                todo.add(q.immutable());
                n++;
            }
        }
    }

    /** Blocks that change shape to join up with what's next to them. */
    public static boolean joins(BlockState s) {
        Block b = s.getBlock();
        return b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock;
    }
}

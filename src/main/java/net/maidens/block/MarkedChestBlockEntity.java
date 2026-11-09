package net.maidens.block;

import net.maidens.core.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/** The marked chest's contents: an ordinary chest's. */
public class MarkedChestBlockEntity extends ChestBlockEntity {
    public MarkedChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MARKED_CHEST_ENTITY.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.maidens.marked_chest");
    }

    /** The closest marked chest within range of a spot (in loaded chunks), or null. */
    @Nullable
    public static MarkedChestBlockEntity nearest(ServerLevel level, BlockPos from, int range) {
        MarkedChestBlockEntity best = null;
        double bestD = (double) range * range;
        for (int cx = (from.getX() - range) >> 4; cx <= (from.getX() + range) >> 4; cx++) {
            for (int cz = (from.getZ() - range) >> 4; cz <= (from.getZ() + range) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof MarkedChestBlockEntity chest) || be.isRemoved()) continue;
                    double d = be.getBlockPos().distSqr(from);
                    if (d <= bestD) {
                        bestD = d;
                        best = chest;
                    }
                }
            }
        }
        return best;
    }
}

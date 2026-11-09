package net.maidens.block;

import com.mojang.serialization.MapCodec;
import net.maidens.core.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * A chest (crafted from a chest and a gold ingot) that maidens bring what they gathered to. Otherwise just a
 * chest, except it never joins a neighbour into a double chest.
 */
public class MarkedChestBlock extends ChestBlock {
    public static final MapCodec<MarkedChestBlock> CODEC = simpleCodec(MarkedChestBlock::new);

    public MarkedChestBlock(Properties properties) {
        super(properties, ModBlocks.MARKED_CHEST_ENTITY::get);
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarkedChestBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = super.getStateForPlacement(context);
        return s == null ? null : s.setValue(TYPE, ChestType.SINGLE);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state;
    }
}

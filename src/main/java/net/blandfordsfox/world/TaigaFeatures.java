package net.blandfordsfox.world;

import com.mojang.serialization.MapCodec;
import net.blandfordsfox.BlandfordsFoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/** Taiga ground, and the weathered base of birch trunks. */
public final class TaigaFeatures {
    private TaigaFeatures() {}

    /**
     * Runs last (top layer step), after trees and plants: the top ground block of every taiga
     * column - under plants, trees and all - becomes podzol.
     */
    public static final class Podzol extends Feature<NoneFeatureConfiguration> {
        public Podzol(com.mojang.serialization.Codec<NoneFeatureConfiguration> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            WorldGenLevel level = ctx.level();
            int x0 = ctx.origin().getX() & ~15, z0 = ctx.origin().getZ() & ~15;
            ChunkAccess chunk = level.getChunk(x0 >> 4, z0 >> 4);
            BlockState podzol = Blocks.PODZOL.defaultBlockState();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            boolean any = false;
            for (int dx = 0; dx < 16; dx++)
                for (int dz = 0; dz < 16; dz++) {
                    int x = x0 + dx, z = z0 + dz;
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                    for (int y = top; y > top - 40 && y > level.getMinBuildHeight(); y--) {
                        pos.set(x, y, z);
                        BlockState s = level.getBlockState(pos);
                        // look past air, trees and plants for the ground (water stops the search)
                        if (s.is(Blocks.WATER)) break;
                        if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced() || isPlant(s)) continue;
                        if ((s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT))
                                && chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)).is(Biomes.TAIGA)) {
                            level.setBlock(pos, podzol, 2);
                            any = true;
                        }
                        break;
                    }
                }
            return any;
        }

        private static boolean isPlant(BlockState s) {
            return s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.PUMPKIN) || s.is(BlockTags.FLOWERS) || s.is(Blocks.BROWN_MUSHROOM)
                    || s.is(Blocks.RED_MUSHROOM) || s.is(BlockTags.SAPLINGS);
        }
    }

    /** Swaps the lowest log of a birch tree for the weathered trunk base. */
    public static final class BirchBase extends TreeDecorator {
        public static final BirchBase INSTANCE = new BirchBase();
        public static final MapCodec<BirchBase> CODEC = MapCodec.unit(() -> INSTANCE);

        @Override
        protected TreeDecoratorType<?> type() {
            return BlandfordsFoxMod.BIRCH_BASE_DECORATOR.get();
        }

        /** For the self-test: calls, logs seen, logs swapped. */
        public static final int[] STATS = new int[3];

        @Override
        public void place(Context ctx) {
            STATS[0]++;
            STATS[1] += ctx.logs().size();
            if (ctx.logs().isEmpty()) return;
            // the tree's log list also holds the dirt it puts under the trunk, so look for real birch logs
            java.util.function.Predicate<BlockState> birch = st -> st.is(Blocks.BIRCH_LOG);
            int low = Integer.MAX_VALUE;
            for (BlockPos p : ctx.logs()) if (ctx.level().isStateAtPosition(p, birch)) low = Math.min(low, p.getY());
            if (low == Integer.MAX_VALUE) return;
            BlockState base = BlandfordsFoxMod.BIRCH_TRUNK_BASE.get().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.Y);
            for (BlockPos p : ctx.logs())
                if (p.getY() == low && ctx.level().isStateAtPosition(p, birch)) {
                    ctx.setBlock(p, base);
                    STATS[2]++;
                }
        }
    }
}

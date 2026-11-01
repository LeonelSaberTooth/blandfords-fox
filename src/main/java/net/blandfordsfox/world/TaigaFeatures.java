package net.blandfordsfox.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Taiga ground. */
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
}

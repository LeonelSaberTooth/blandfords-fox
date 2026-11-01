package net.blandfordsfox.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.Nullable;

/** Ground of the taiga, the snowy plains and the rivers through them. */
public final class TaigaFeatures {
    private TaigaFeatures() {}

    /** Which ground rules a column follows. */
    enum Rule { TAIGA, SNOWY_PLAINS }

    /**
     * Runs last (top layer step), after trees, plants and snowfall, in taiga, snowy plains and
     * rivers:
     * <ul>
     * <li>taiga: the top ground block (under plants, trees and all) becomes podzol;</li>
     * <li>snowy plains: under a snow layer the ground becomes a snow block with podzol under
     *     it; elsewhere (under trees) podzol; no short grass or sugar cane;</li>
     * <li>a river follows the biome around it: if that is taiga or snowy plains, its sand is
     *     gravel, its banks follow those rules, there is no sugar cane, and short grass is
     *     ferns (taiga) or gone (snowy plains).</li>
     * </ul>
     * Podzol under snow (a layer or a block) is snowy, so it looks like snowy grass.
     */
    public static final class Ground extends Feature<NoneFeatureConfiguration> {
        public Ground(com.mojang.serialization.Codec<NoneFeatureConfiguration> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            WorldGenLevel level = ctx.level();
            int x0 = ctx.origin().getX() & ~15, z0 = ctx.origin().getZ() & ~15;
            ChunkAccess chunk = level.getChunk(x0 >> 4, z0 >> 4);
            Rule[] riverRule = new Rule[16];
            boolean[] riverDone = new boolean[16];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            boolean any = false;
            for (int dx = 0; dx < 16; dx++)
                for (int dz = 0; dz < 16; dz++) {
                    int x = x0 + dx, z = z0 + dz;
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                    Holder<Biome> here = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top), QuartPos.fromBlock(z));
                    boolean river = here.is(BiomeTags.IS_RIVER);
                    Rule rule;
                    if (river) {
                        int cell = (dx >> 2) * 4 + (dz >> 2);
                        if (!riverDone[cell]) { riverRule[cell] = around(level, x, z); riverDone[cell] = true; }
                        rule = riverRule[cell];
                    } else rule = here.is(Biomes.TAIGA) ? Rule.TAIGA : here.is(Biomes.SNOWY_PLAINS) ? Rule.SNOWY_PLAINS : null;
                    if (rule != null) any |= column(level, pos, x, z, top, rule, river);
                }
            return any;
        }

        private static boolean column(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, Rule rule, boolean river) {
            boolean snowLayer = false, underWater = false, changed = false;
            int y = top;
            for (; y > top - 40 && y > level.getMinBuildHeight(); y--) {
                pos.set(x, y, z);
                BlockState s = level.getBlockState(pos);
                if (s.is(Blocks.SUGAR_CANE)) { level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); changed = true; continue; }
                if (s.is(Blocks.SHORT_GRASS)) {
                    level.setBlock(pos, rule == Rule.TAIGA ? Blocks.FERN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                    changed = true;
                    continue;
                }
                if (s.is(Blocks.TALL_GRASS)) {
                    level.setBlock(pos, rule == Rule.TAIGA
                            ? Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, s.getValue(DoublePlantBlock.HALF))
                            : Blocks.AIR.defaultBlockState(), 2);
                    changed = true;
                    continue;
                }
                if (s.is(Blocks.SNOW)) { snowLayer = true; continue; }
                if (!s.getFluidState().isEmpty() || s.is(BlockTags.ICE)) {
                    if (!river) return changed;     // a pond or lake: leave its bed alone
                    underWater = true;
                    continue;
                }
                if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced() || isPlant(s)) continue;
                break;
            }
            int ground = y;
            if (river) {   // no sand: gravel, bed and banks, a few blocks down
                for (int k = 0; k < 6; k++) {
                    pos.set(x, ground - k, z);
                    if (level.getBlockState(pos).is(Blocks.SAND)) { level.setBlock(pos, Blocks.GRAVEL.defaultBlockState(), 2); changed = true; }
                }
            }
            if (underWater) return changed;
            pos.set(x, ground, z);
            BlockState s = level.getBlockState(pos);
            if (!s.is(Blocks.GRASS_BLOCK) && !s.is(Blocks.DIRT) && !s.is(Blocks.PODZOL)) return changed;
            if (rule == Rule.SNOWY_PLAINS && snowLayer) {
                level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
                pos.set(x, ground - 1, z);
                BlockState under = level.getBlockState(pos);
                if (under.is(Blocks.DIRT) || under.is(Blocks.GRASS_BLOCK)) level.setBlock(pos, podzol(true), 2);
            } else {
                BlockState above = level.getBlockState(pos.above());
                level.setBlock(pos, podzol(above.is(BlockTags.SNOW)), 2);
            }
            return true;
        }

        private static BlockState podzol(boolean snowy) {
            return Blocks.PODZOL.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, snowy);
        }

        /** The land biome around a river spot (from the climate, so no chunks are needed): taiga, snowy plains, or other. */
        @Nullable
        private static Rule around(WorldGenLevel level, int x, int z) {
            ServerLevel server = level.getLevel();
            var source = server.getChunkSource().getGenerator().getBiomeSource();
            var sampler = server.getChunkSource().randomState().sampler();
            int taiga = 0, snowy = 0, other = 0;
            for (int r = 16; r <= 48; r += 16)
                for (int a = 0; a < 8; a++) {
                    int sx = x + (int) Math.round(r * Math.cos(a * Math.PI / 4)), sz = z + (int) Math.round(r * Math.sin(a * Math.PI / 4));
                    Holder<Biome> b = source.getNoiseBiome(QuartPos.fromBlock(sx), QuartPos.fromBlock(64), QuartPos.fromBlock(sz), sampler);
                    if (b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_BEACH)) continue;
                    if (b.is(Biomes.TAIGA)) taiga++;
                    else if (b.is(Biomes.SNOWY_PLAINS)) snowy++;
                    else other++;
                }
            if (taiga > snowy && taiga >= other) return Rule.TAIGA;
            if (snowy > taiga && snowy >= other) return Rule.SNOWY_PLAINS;
            return null;
        }

        private static boolean isPlant(BlockState s) {
            return s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.PUMPKIN) || s.is(BlockTags.FLOWERS) || s.is(Blocks.BROWN_MUSHROOM)
                    || s.is(Blocks.RED_MUSHROOM) || s.is(BlockTags.SAPLINGS);
        }
    }
}

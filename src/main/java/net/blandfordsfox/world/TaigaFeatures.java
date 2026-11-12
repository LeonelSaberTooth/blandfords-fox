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
    /**
     * Which ground rules a column follows: taiga; snowy plains; or snowy rock (snowy taiga and
     * ice spikes: snow straight on deepslate, no soil or plants that need it).
     */
    enum Rule { TAIGA, SNOWY_PLAINS, SNOWY_ROCK }

    /** Taiga ground patches: coarse dirt now and then (like old growth taiga), rarer, smaller grass patches. */
    static final double COARSE_SCALE = 11, COARSE_LEVEL = 0.71, GRASS_SCALE = 6, GRASS_LEVEL = 0.8;
    static final float GRASS_ON_PATCH = 0.45F, TALL_GRASS_ON_PATCH = 0.12F;

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
                    // a river, or the beach where one widens out
                    boolean river = here.is(BiomeTags.IS_RIVER) || here.is(BiomeTags.IS_BEACH) && nearRiver(level, x, z);
                    Rule rule;
                    if (river) {
                        int cell = (dx >> 2) * 4 + (dz >> 2);
                        if (!riverDone[cell]) { riverRule[cell] = around(level, x, z); riverDone[cell] = true; }
                        rule = riverRule[cell];
                    } else rule = here.is(Biomes.TAIGA) ? Rule.TAIGA : here.is(Biomes.SNOWY_PLAINS) ? Rule.SNOWY_PLAINS
                            : here.is(Biomes.SNOWY_TAIGA) || here.is(Biomes.ICE_SPIKES) ? Rule.SNOWY_ROCK : null;
                    if (rule != null) any |= column(level, chunk, pos, x, z, top, rule, river, ctx.random());
                }
            return any;
        }

        private static boolean column(WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int top,
                                      Rule rule, boolean river, net.minecraft.util.RandomSource random) {
            boolean snowLayer = false, underWater = false, pond = false, changed = false;
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
                // snowy rock: nothing that needs soil (it would stand on snow)
                if (rule == Rule.SNOWY_ROCK && (s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN) || s.is(Blocks.SWEET_BERRY_BUSH)
                        || s.is(BlockTags.FLOWERS) || s.is(Blocks.DEAD_BUSH))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    changed = true;
                    continue;
                }
                if (s.is(Blocks.SNOW)) { snowLayer = true; continue; }
                if (!s.getFluidState().isEmpty() || s.is(BlockTags.ICE)) {
                    if (river) underWater = true; else pond = true;   // a pond or lake: leave its bed alone
                    continue;
                }
                if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced() || isPlant(s)) continue;
                break;
            }
            int ground = y;
            // river edges can fall in the land biome's columns: sand by a river is gravel too
            boolean riverSand = river || level.getBlockState(pos.set(x, ground, z)).is(Blocks.SAND) && nearRiver(level, x, z);
            if (riverSand && rule != Rule.SNOWY_ROCK) {   // no sand: gravel, bed and banks, a few blocks down
                for (int k = 0; k < 6; k++) {
                    pos.set(x, ground - k, z);
                    if (level.getBlockState(pos).is(Blocks.SAND)) { level.setBlock(pos, Blocks.GRAVEL.defaultBlockState(), 2); changed = true; }
                }
            }
            if (rule == Rule.TAIGA || rule == Rule.SNOWY_ROCK) changed |= deepslate(chunk, pos, x, z, ground, rule == Rule.SNOWY_ROCK && !underWater && !pond, rule == Rule.SNOWY_ROCK);
            if (underWater || pond) return changed;
            pos.set(x, ground, z);
            BlockState s = level.getBlockState(pos);
            boolean soil = s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.COARSE_DIRT);
            switch (rule) {
                case SNOWY_ROCK -> {
                    // snow straight on the rock: the top block is snow (the soil under it is deepslate now)
                    if (!soil && !s.is(Blocks.DEEPSLATE) && !s.is(Blocks.GRAVEL) && !s.is(Blocks.SAND)) return changed;
                    level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
                    return true;
                }
                case SNOWY_PLAINS -> {
                    if (!soil) return changed;
                    if (snowLayer) {
                        level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
                        pos.set(x, ground - 1, z);
                        BlockState under = level.getBlockState(pos);
                        if (under.is(Blocks.DIRT) || under.is(Blocks.GRASS_BLOCK)) level.setBlock(pos, podzol(true), 2);
                    } else {
                        level.setBlock(pos, podzol(level.getBlockState(pos.above()).is(BlockTags.SNOW)), 2);
                    }
                    return true;
                }
                default -> {   // TAIGA
                    if (!soil) return changed;
                    boolean snowy = level.getBlockState(pos.above()).is(BlockTags.SNOW);
                    long seed = level.getSeed();
                    if (patch(seed + 7, x, z, GRASS_SCALE) > GRASS_LEVEL) {
                        // a small grass patch, with short and tall grass on it
                        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, snowy), 2);
                        BlockPos up = pos.above(), up2 = up.above();
                        BlockState on = level.getBlockState(up);
                        if (on.isAir() || on.is(Blocks.FERN)) {
                            float r = random.nextFloat();
                            if (r < TALL_GRASS_ON_PATCH && level.getBlockState(up2).isAir()) {
                                level.setBlock(up, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 2);
                                level.setBlock(up2, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
                            } else if (r < TALL_GRASS_ON_PATCH + GRASS_ON_PATCH) {
                                level.setBlock(up, Blocks.SHORT_GRASS.defaultBlockState(), 2);
                            } else if (on.is(Blocks.FERN)) {
                                level.setBlock(up, Blocks.AIR.defaultBlockState(), 2);
                            }
                        }
                    } else if (patch(seed + 3, x, z, COARSE_SCALE) > COARSE_LEVEL) {
                        level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                    } else {
                        level.setBlock(pos, podzol(snowy), 2);
                    }
                    return true;
                }
            }
        }

        /**
         * Stone becomes deepslate (ores their deepslate kind) from the ground down; with soilToo,
         * the soil under the top block as well (snowy rock: snow straight on deepslate).
         */
        private static boolean deepslate(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int ground, boolean soilToo, boolean topToo) {
            boolean changed = false;
            boolean soil = soilToo;
            for (int y = ground - (topToo ? 0 : 1); y >= Math.max(chunk.getMinBuildHeight(), -8); y--) {
                pos.set(x, y, z);
                BlockState s = chunk.getBlockState(pos);
                BlockState to = DEEPSLATE.get(s.getBlock());
                if (to == null && soil && (s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.COARSE_DIRT)))
                    to = Blocks.DEEPSLATE.defaultBlockState();
                else if (!s.is(Blocks.DIRT)) soil = false;    // only the soil layer right under the top
                if (to != null) { chunk.setBlockState(pos, to, false); changed = true; }
            }
            return changed;
        }

        private static final java.util.Map<net.minecraft.world.level.block.Block, BlockState> DEEPSLATE = java.util.Map.ofEntries(
                java.util.Map.entry(Blocks.STONE, Blocks.DEEPSLATE.defaultBlockState()),
                java.util.Map.entry(Blocks.ANDESITE, Blocks.DEEPSLATE.defaultBlockState()),
                java.util.Map.entry(Blocks.DIORITE, Blocks.DEEPSLATE.defaultBlockState()),
                java.util.Map.entry(Blocks.GRANITE, Blocks.DEEPSLATE.defaultBlockState()),
                java.util.Map.entry(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE.defaultBlockState()),
                java.util.Map.entry(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()),
                java.util.Map.entry(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState()),
                java.util.Map.entry(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState()),
                java.util.Map.entry(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState()),
                java.util.Map.entry(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState()),
                java.util.Map.entry(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState()),
                java.util.Map.entry(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()),
                java.util.Map.entry(Blocks.INFESTED_STONE, Blocks.INFESTED_DEEPSLATE.defaultBlockState()));

        /** Smooth 0..1 value noise for ground patches (two octaves). */
        static double patch(long seed, int x, int z, double scale) {
            return 0.7 * value(seed, x / scale, z / scale) + 0.3 * value(seed + 1, x / (scale * 0.5), z / (scale * 0.5));
        }

        private static double value(long seed, double x, double z) {
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
            double fx = x - ix, fz = z - iz;
            fx = fx * fx * (3 - 2 * fx);
            fz = fz * fz * (3 - 2 * fz);
            double a = hash(seed, ix, iz), b = hash(seed, ix + 1, iz), c = hash(seed, ix, iz + 1), d = hash(seed, ix + 1, iz + 1);
            return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
        }

        private static double hash(long seed, int x, int z) {
            long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L;
            h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
            h ^= h >>> 29;
            return (h >>> 11) * 0x1.0p-53;
        }

        /** A river within 8 blocks (from the climate, so no chunks are needed). */
        private static boolean nearRiver(WorldGenLevel level, int x, int z) {
            ServerLevel server = level.getLevel();
            var source = server.getChunkSource().getGenerator().getBiomeSource();
            var sampler = server.getChunkSource().randomState().sampler();
            for (int r = 4; r <= 8; r += 4)
                for (int a = 0; a < 8; a++) {
                    int sx = x + (int) Math.round(r * Math.cos(a * Math.PI / 4)), sz = z + (int) Math.round(r * Math.sin(a * Math.PI / 4));
                    if (source.getNoiseBiome(QuartPos.fromBlock(sx), QuartPos.fromBlock(64), QuartPos.fromBlock(sz), sampler).is(BiomeTags.IS_RIVER))
                        return true;
                }
            return false;
        }

        private static BlockState podzol(boolean snowy) {
            return Blocks.PODZOL.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, snowy);
        }

        /** The land biome around a river spot (from the climate, so no chunks are needed): taiga, snowy plains, or other. */
        @Nullable
        static Rule around(WorldGenLevel level, int x, int z) {
            ServerLevel server = level.getLevel();
            var source = server.getChunkSource().getGenerator().getBiomeSource();
            var sampler = server.getChunkSource().randomState().sampler();
            int taiga = 0, snowy = 0, rock = 0, other = 0;
            for (int r = 16; r <= 48; r += 16)
                for (int a = 0; a < 8; a++) {
                    int sx = x + (int) Math.round(r * Math.cos(a * Math.PI / 4)), sz = z + (int) Math.round(r * Math.sin(a * Math.PI / 4));
                    Holder<Biome> b = source.getNoiseBiome(QuartPos.fromBlock(sx), QuartPos.fromBlock(64), QuartPos.fromBlock(sz), sampler);
                    if (b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_BEACH)) continue;
                    if (b.is(Biomes.TAIGA)) taiga++;
                    else if (b.is(Biomes.SNOWY_PLAINS)) snowy++;
                    else if (b.is(Biomes.SNOWY_TAIGA) || b.is(Biomes.ICE_SPIKES)) rock++;
                    else other++;
                }
            // a river along a border follows taiga / snowy plains if they make up a third or more of the land
            if (rock > 0 && rock >= taiga && rock >= snowy && rock * 2 >= other) return Rule.SNOWY_ROCK;
            if (taiga > 0 && taiga >= snowy && taiga * 2 >= other) return Rule.TAIGA;
            if (snowy > 0 && snowy > taiga && snowy * 2 >= other) return Rule.SNOWY_PLAINS;
            return null;
        }

        private static boolean isPlant(BlockState s) {
            return s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.PUMPKIN) || s.is(BlockTags.FLOWERS) || s.is(Blocks.BROWN_MUSHROOM)
                    || s.is(Blocks.RED_MUSHROOM) || s.is(BlockTags.SAPLINGS);
        }
    }

    /**
     * Vanilla's underwater sand patches, but gravel in the taiga, the snowy plains and the
     * rivers through them. (Placed from a neighbouring chunk, a patch can land after that
     * chunk's ground was done, so Ground alone can't catch it.)
     */
    public static final class ColdDisk extends net.minecraft.world.level.levelgen.feature.DiskFeature {
        public ColdDisk(com.mojang.serialization.Codec<net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration> ctx) {
            WorldGenLevel level = ctx.level();
            BlockPos o = ctx.origin();
            Holder<Biome> b = level.getBiome(o);
            boolean cold = b.is(Biomes.TAIGA) || b.is(Biomes.SNOWY_PLAINS)
                    || (b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_BEACH)) && Ground.around(level, o.getX(), o.getZ()) != null;
            if (!cold) return super.place(ctx);
            var cfg = ctx.config();
            var gravel = new net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedBlockStateProvider(
                    net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.GRAVEL), cfg.stateProvider().rules());
            var config = new net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration(gravel, cfg.target(), cfg.radius(), cfg.halfHeight());
            return super.place(new FeaturePlaceContext<>(ctx.topFeature(), level, ctx.chunkGenerator(), ctx.random(), o, config));
        }
    }
}

package net.blandfordsfox.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.Nullable;

/** Painting rock layers and valley floors, then decorating the valleys. */
public final class NegevFeatures {
    private NegevFeatures() {}

    @Nullable
    static NegevColumns columnsFor(WorldGenLevel level) {
        if (!(level.getLevel().getChunkSource() instanceof ServerChunkCache cache)) return null;
        RandomState rs = cache.randomState();
        if (rs.router().finalDensity() instanceof NegevDensity.Detail d) return d.columns();
        return NegevColumns.forNoise(rs.getOrCreateNoise(NegevColumns.BIG_NOISE));
    }

    static boolean isDesert(WorldGenLevel level, BlockPos pos) {
        return level.getBiome(pos).is(Biomes.DESERT);
    }

    // ------------------------------------------------------------------ dried rivers
    /**
     * Dried desert rivers, read from the real terrain of one chunk (plus a margin): the bed is
     * every column vanilla made river and the dry-river lift left flat at river-bed height; the
     * banks are the columns up to {@link NegevShape#RIVER_BANK_WIDTH} blocks out from it.
     * Returns null for chunks with no dried river.
     */
    @Nullable
    static NegevShape.Kind[] riverKinds(WorldGenLevel level, NegevColumns columns, int x0, int z0) {
        boolean any = false;
        for (int dx = 0; dx < 16 && !any; dx += 5) for (int dz = 0; dz < 16 && !any; dz += 5) {
            any = columns.get(x0 + dx, z0 + dz).dry > 0.9;
        }
        if (!any || !(level.getLevel().getChunkSource() instanceof ServerChunkCache cache)) return null;
        BiomeSource source = cache.getGenerator().getBiomeSource();
        Climate.Sampler sampler = cache.randomState().sampler();
        int m = NegevShape.RIVER_BANK_WIDTH, w = 16 + 2 * m;
        boolean[] bed = new boolean[w * w];
        int[] top = new int[w * w];
        boolean anyBed = false;
        for (int i = 0; i < w; i++) for (int j = 0; j < w; j++) {
            int x = x0 - m + i, z = z0 - m + j;
            int t = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
            top[i * w + j] = t;
            if (Math.abs(t + 1 - NegevShape.MIN_FLOOR) > 1 || columns.get(x, z).dry <= 0.9) continue;
            if (level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1 != t) continue;     // still water there
            Holder<Biome> original = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(t), QuartPos.fromBlock(z), sampler);
            if (original.is(Biomes.RIVER)) { bed[i * w + j] = true; anyBed = true; }
        }
        if (!anyBed) return null;
        NegevShape.Kind[] out = new NegevShape.Kind[256];
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int i = dx + m, j = dz + m;
            NegevShape.Kind k = null;
            if (bed[i * w + j]) {
                k = NegevShape.Kind.RIVER_FLOOR;
            } else if (top[i * w + j] + 1 > NegevShape.MIN_FLOOR) {
                search:
                for (int r = 1; r <= m; r++) for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != r || a * a + b * b > m * m) continue;
                    if (bed[(i + a) * w + (j + b)]) { k = NegevShape.Kind.RIVER_SLOPE; break search; }
                }
            }
            out[dx * 16 + dz] = k;
        }
        return out;
    }

    /** Turns the river biome into desert where a river has been dried (desert grass colour, no river look). */
    static void dryRiverBiomes(WorldGenLevel level, NegevColumns columns, int x0, int z0) {
        if (!(level.getLevel().getChunkSource() instanceof ServerChunkCache cache)) return;
        ChunkAccess chunk = level.getChunk(x0 >> 4, z0 >> 4);
        Holder<Biome> desert = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
        chunk.fillBiomesFromNoise((qx, qy, qz, s) -> {
            Holder<Biome> b = chunk.getNoiseBiome(qx, qy, qz);
            if (b.is(Biomes.RIVER) && columns.get((qx << 2) + 2, (qz << 2) + 2).dry > 0.9) return desert;
            return b;
        }, cache.randomState().sampler());
    }

    /** The ground to paint or decorate a column as: its own shape, or a dried river's bed or bank. */
    @Nullable
    static NegevShape.Kind groundKind(NegevShape.Column col, int topY, @Nullable NegevShape.Kind[] river, int dx, int dz) {
        if (col.kind != NegevShape.Kind.VANILLA) {
            NegevShape.Kind k = verifiedKind(col, topY);
            return k == NegevShape.Kind.VANILLA ? null : k;
        }
        return river == null ? null : river[dx * 16 + dz];
    }

    // ------------------------------------------------------------------ painting
    /** Runs first in each chunk: rock layers on walls and plateaus, dirt/grass/sand on valley floors. */
    public static final class Paint extends Feature<NoneFeatureConfiguration> {
        public Paint(Codec<NoneFeatureConfiguration> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            WorldGenLevel level = ctx.level();
            NegevColumns columns = columnsFor(level);
            if (columns == null) return false;
            NegevShape shape = columns.shape();
            int x0 = ctx.origin().getX() & ~15, z0 = ctx.origin().getZ() & ~15;
            NegevShape.Kind[] river = riverKinds(level, columns, x0, z0);
            if (river != null) dryRiverBiomes(level, columns, x0, z0);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            boolean any = false;
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                NegevShape.Column col = columns.get(x, z);
                if (col.kind == NegevShape.Kind.VANILLA && river == null) continue;
                int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                pos.set(x, topY, z);
                if (!level.getFluidState(pos).isEmpty() || !isDesert(level, pos)) continue;
                NegevShape.Kind kind = groundKind(col, topY, river, dx, dz);
                if (kind == null) continue;
                boolean isRiver = kind == NegevShape.Kind.RIVER_FLOOR || kind == NegevShape.Kind.RIVER_SLOPE;
                for (int y = topY; y >= topY - 48; y--) {
                    NegevShape.Material m = isRiver ? shape.paint(x, y, z, col, topY, kind, NegevShape.MIN_FLOOR)
                            : shape.paint(x, y, z, col, topY);
                    if (m == null) continue;
                    pos.set(x, y, z);
                    BlockState here = level.getBlockState(pos);
                    if (here.isAir() || !here.getFluidState().isEmpty()) continue;   // leave caves alone
                    level.setBlock(pos, block(m), Block.UPDATE_CLIENTS);
                    any = true;
                }
            }
            return any;
        }
    }

    // ------------------------------------------------------------------ decoration
    /** Valley floors: grass, dead bushes, boulders, acacia-leaf bushes, rare acacias. Plateaus: rare dead bush. Walls: rare small caves. */
    public static final class Decorate extends Feature<NoneFeatureConfiguration> {
        // per floor column
        static final float TREE = 0.0008F, BUSH = 0.012F, BOULDER = 0.004F, GRASS = 0.18F, DEAD_BUSH = 0.03F;
        // floor columns at the foot of a wall, and wall columns
        static final float EDGE_BOULDER = 0.03F, SLOPE_BOULDER = 0.003F;
        // per plateau-top column
        static final float TOP_DEAD_BUSH = 0.0015F;
        // per chunk with valley walls
        static final float CAVE = 0.08F;

        public Decorate(Codec<NoneFeatureConfiguration> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            WorldGenLevel level = ctx.level();
            NegevColumns columns = columnsFor(level);
            if (columns == null) return false;
            RandomSource rnd = ctx.random();
            int x0 = ctx.origin().getX() & ~15, z0 = ctx.origin().getZ() & ~15;
            NegevShape.Kind[] river = riverKinds(level, columns, x0, z0);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int slopeX = 0, slopeZ = 0, slopes = 0;
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                NegevShape.Column col = columns.get(x, z);
                if (col.kind == NegevShape.Kind.VANILLA && river == null) continue;
                int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                pos.set(x, topY, z);
                NegevShape.Kind kind = groundKind(col, topY, river, dx, dz);
                if (kind == null || !isDesert(level, pos)) continue;
                BlockState ground = level.getBlockState(pos);
                BlockPos above = pos.above();
                if (!level.getBlockState(above).isAir()) continue;
                switch (kind) {
                    case FLOOR, WADI_FLOOR, RIVER_FLOOR -> {
                        if (!(ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.SAND))) continue;
                        float r = rnd.nextFloat();
                        if (r < TREE) {
                            tree(level, ctx, pos.immutable(), rnd);
                        } else if (r < TREE + BUSH) {
                            bush(level, above, rnd);
                        } else if (r < TREE + BUSH + (atWallFoot(level, x, z, topY) ? EDGE_BOULDER : BOULDER)) {
                            boulder(level, pos.immutable(), rnd);
                        } else if (ground.is(Blocks.GRASS_BLOCK) && rnd.nextFloat() < GRASS) {
                            level.setBlock(above, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
                        } else if (rnd.nextFloat() < DEAD_BUSH) {
                            level.setBlock(above, Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    case TOP -> {
                        if (rnd.nextFloat() < TOP_DEAD_BUSH) {
                            level.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
                            level.setBlock(above, Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    case SLOPE, WADI_SLOPE, RIVER_SLOPE -> {
                        if (rnd.nextFloat() < SLOPE_BOULDER) boulder(level, pos.immutable(), rnd);
                        slopes++;
                        if (rnd.nextInt(slopes) == 0) { slopeX = x; slopeZ = z; }   // pick one wall column at random
                    }
                    default -> { }
                }
            }
            if (slopes > 0 && rnd.nextFloat() < CAVE) cave(level, slopeX, slopeZ, rnd);
            return true;
        }

        private static void tree(WorldGenLevel level, FeaturePlaceContext<NoneFeatureConfiguration> ctx, BlockPos ground, RandomSource rnd) {
            level.registryAccess().registry(Registries.CONFIGURED_FEATURE)
                    .flatMap(r -> r.getHolder(TreeFeatures.ACACIA))
                    .ifPresent(h -> {
                        level.setBlock(ground, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                        h.value().place(level, ctx.chunkGenerator(), rnd, ground.above());
                    });
        }

        /** A low clump of acacia leaves sitting on the valley floor. */
        private static void bush(WorldGenLevel level, BlockPos base, RandomSource rnd) {
            BlockState leaves = Blocks.ACACIA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            double rx = 0.9 + rnd.nextDouble() * 1.0, rz = 0.9 + rnd.nextDouble() * 1.0, ry = 0.9 + rnd.nextDouble() * 0.9;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++) {
                double d = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz) + (dy * dy) / (ry * ry);
                if (d > 1.0 + rnd.nextDouble() * 0.25) continue;
                p.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                if (!level.getBlockState(p).isAir()) continue;
                if (!level.getBlockState(p.below()).isSolid() && !level.getBlockState(p.below()).is(Blocks.ACACIA_LEAVES)) continue;
                level.setBlock(p, leaves, Block.UPDATE_CLIENTS);
            }
        }

        /** Is this floor column right at the foot of a wall (ground 2+ blocks higher nearby)? */
        private static boolean atWallFoot(WorldGenLevel level, int x, int z, int topY) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + d.getStepX() * 2, z + d.getStepZ() * 2) - 1;
                if (h - topY >= 2) return true;
            }
            return false;
        }

        /** A small rounded boulder, mostly limestone, else granite or sandstone, half sunk into the ground. */
        private static void boulder(WorldGenLevel level, BlockPos ground, RandomSource rnd) {
            BlockState limestone = block(NegevShape.Material.LIMESTONE);
            float pick = rnd.nextFloat();
            BlockState main = pick < 0.55F ? limestone : pick < 0.8F ? Blocks.GRANITE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
            BlockState other = main == limestone ? Blocks.GRANITE.defaultBlockState() : limestone;
            double r = 0.8 + rnd.nextDouble() * 1.0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++) {
                double d = Math.sqrt(dx * dx + dz * dz + (dy * dy) * 1.6);
                if (d > r + rnd.nextDouble() * 0.4) continue;
                p.set(ground.getX() + dx, ground.getY() + dy, ground.getZ() + dz);
                BlockState here = level.getBlockState(p);
                if (dy > 0 && !here.isAir() && !here.canBeReplaced()) continue;
                level.setBlock(p, rnd.nextFloat() < 0.12 ? other : main, Block.UPDATE_CLIENTS);
            }
        }

        /** A small hollow in a valley wall, opening onto the lower side. */
        private static void cave(WorldGenLevel level, int x, int z, RandomSource rnd) {
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            Direction best = null;
            int lowest = top;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + d.getStepX() * 3, z + d.getStepZ() * 3) - 1;
                if (h < lowest) { lowest = h; best = d; }
            }
            if (best == null || top - lowest < 4) return;
            // centre a little into the wall, just above the lower ground
            Direction in = best.getOpposite();
            int cx = x + in.getStepX(), cz = z + in.getStepZ(), cy = lowest + 2;
            double rx = 1.6 + rnd.nextDouble() * 1.2, ry = 1.3 + rnd.nextDouble() * 0.6, rz = 1.6 + rnd.nextDouble() * 1.2;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 2; dy++) {
                double d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
                if (d > 1) continue;
                p.set(cx + dx, cy + dy, cz + dz);
                if (p.getY() <= lowest) continue;                 // keep a flat floor
                if (p.getY() >= top - 1) continue;                // keep a roof
                BlockState here = level.getBlockState(p);
                if (here.isAir() || !here.getFluidState().isEmpty()) continue;
                level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}

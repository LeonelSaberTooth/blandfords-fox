package net.blandfordsfox.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
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
        DensityFunction fin = cache.randomState().router().finalDensity();
        return fin instanceof NegevDensity.Detail d ? d.columns() : null;
    }

    static boolean isDesert(WorldGenLevel level, BlockPos pos) {
        return level.getBiome(pos).is(Biomes.DESERT);
    }

    static BlockState block(NegevShape.Material m) {
        return switch (m) {
            case SANDSTONE -> Blocks.SANDSTONE.defaultBlockState();
            case GRANITE -> Blocks.GRANITE.defaultBlockState();
            case PACKED_MUD -> Blocks.PACKED_MUD.defaultBlockState();
            case SAND -> Blocks.SAND.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }

    /** The column's kind, but only if the ground really is where the shape put it. */
    static NegevShape.Kind verifiedKind(NegevShape.Column col, int topY) {
        NegevShape.Kind k = col.kind;
        switch (k) {
            case FLOOR, SLOPE, TOP -> { if (Math.abs(topY + 1 - col.surfExact) > 1) return NegevShape.Kind.VANILLA; }
            case WADI_FLOOR, WADI_SLOPE -> { if (topY + 1 != col.wadiExact) return NegevShape.Kind.VANILLA; }
            default -> { }
        }
        return k;
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
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            boolean any = false;
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                NegevShape.Column col = columns.get(x, z);
                if (col.kind == NegevShape.Kind.VANILLA) continue;
                int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                pos.set(x, topY, z);
                if (!level.getFluidState(pos).isEmpty() || !isDesert(level, pos)) continue;
                NegevShape.Kind kind = verifiedKind(col, topY);
                if (kind == NegevShape.Kind.VANILLA) continue;
                NegevShape.Column view = col;
                for (int y = topY; y >= topY - 48; y--) {
                    NegevShape.Material m = shape.paint(x, y, z, view, topY);
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
        static final float TREE = 0.0009F, BUSH = 0.004F, BOULDER = 0.0035F, GRASS = 0.18F, DEAD_BUSH = 0.03F;
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
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int slopeX = 0, slopeZ = 0, slopes = 0;
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                NegevShape.Column col = columns.get(x, z);
                if (col.kind == NegevShape.Kind.VANILLA) continue;
                int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                pos.set(x, topY, z);
                if (verifiedKind(col, topY) != col.kind || !isDesert(level, pos)) continue;
                BlockState ground = level.getBlockState(pos);
                BlockPos above = pos.above();
                if (!level.getBlockState(above).isAir()) continue;
                switch (col.kind) {
                    case FLOOR, WADI_FLOOR -> {
                        if (!(ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.SAND))) continue;
                        float r = rnd.nextFloat();
                        if (r < TREE) {
                            tree(level, ctx, pos.immutable(), rnd);
                        } else if (r < TREE + BUSH) {
                            bush(level, above, rnd);
                        } else if (r < TREE + BUSH + BOULDER) {
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
                    case SLOPE, WADI_SLOPE -> {
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

        /** A small rounded boulder of sandstone or granite, half sunk into the floor. */
        private static void boulder(WorldGenLevel level, BlockPos ground, RandomSource rnd) {
            BlockState main = rnd.nextBoolean() ? Blocks.SANDSTONE.defaultBlockState() : Blocks.GRANITE.defaultBlockState();
            BlockState other = main.is(Blocks.GRANITE) ? Blocks.SANDSTONE.defaultBlockState() : Blocks.GRANITE.defaultBlockState();
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

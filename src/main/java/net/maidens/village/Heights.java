package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.HashMap;
import java.util.Map;

/**
 * The lie of the land for planning (castle sites, walls) without loading anything: loaded columns are read as they
 * are (the ground under trees and plants, and the water on top); unloaded ones come from the world generator's
 * terrain shape (no trees or buildings), sampled every {@link #GRID} blocks and blended in between, since asking
 * the generator costs as much as a thousand loaded lookups.
 */
final class Heights {
    private static final int GRID = 4;
    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final RandomState random;
    /** Generator samples: ground and water surface per grid point. */
    private final Map<Long, int[]> samples = new HashMap<>();

    Heights(ServerLevel level) {
        this.level = level;
        this.generator = level.getChunkSource().getGenerator();
        this.random = level.getChunkSource().randomState();
    }

    /** The top of the solid ground (under trees, plants and water). */
    int ground(int x, int z) {
        return column(x, z)[0];
    }

    /** The water surface over the ground, or the ground itself if it's dry. */
    int surface(int x, int z) {
        return column(x, z)[1];
    }

    boolean wet(int x, int z) {
        int[] c = column(x, z);
        return c[1] > c[0];
    }

    boolean loaded(int x, int z) {
        return level.getChunkSource().hasChunk(x >> 4, z >> 4);
    }

    private int[] column(int x, int z) {
        if (loaded(x, z)) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
            int water = y;
            boolean wet = !level.getFluidState(p).isEmpty();
            for (int n = 0; n < 40; n++) {
                BlockState s = level.getBlockState(p);
                boolean soft = s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced() || !s.getFluidState().isEmpty();
                if (!soft) break;
                p.move(0, -1, 0);
            }
            return new int[]{p.getY(), wet ? water : p.getY()};
        }
        // blended from the four generator samples around it
        int gx = Math.floorDiv(x, GRID), gz = Math.floorDiv(z, GRID);
        double fx = (x - gx * GRID) / (double) GRID, fz = (z - gz * GRID) / (double) GRID;
        int[] a = sample(gx, gz), b = sample(gx + 1, gz), c = sample(gx, gz + 1), d = sample(gx + 1, gz + 1);
        int[] out = new int[2];
        for (int i = 0; i < 2; i++) {
            double top = a[i] * (1 - fx) + b[i] * fx, bottom = c[i] * (1 - fx) + d[i] * fx;
            out[i] = (int) Math.round(top * (1 - fz) + bottom * fz);
        }
        if (out[1] < out[0]) out[1] = out[0];
        return out;
    }

    private int[] sample(int gx, int gz) {
        return samples.computeIfAbsent(((long) gx << 32) | (gz & 0xFFFFFFFFL), k -> {
            int x = gx * GRID, z = gz * GRID;
            int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
            int top = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random) - 1;
            return new int[]{floor, Math.max(floor, top)};
        });
    }
}

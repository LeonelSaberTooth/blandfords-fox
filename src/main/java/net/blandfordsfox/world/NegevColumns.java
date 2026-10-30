package net.blandfordsfox.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NegevShape wired to one world's noises and climate, with a cache of worked-out
 * columns shared by the terrain density functions and the painting features.
 */
public final class NegevColumns {
    private static final Map<Object, NegevColumns> BY_NOISE = new WeakHashMap<>();
    public static final ResourceKey<NormalNoise.NoiseParameters> BIG_NOISE =
            ResourceKey.create(Registries.NOISE, ResourceLocation.fromNamespaceAndPath("blandfordsfox", "negev_big"));

    final DensityFunction temperature, continents, erosion, depth;
    final NegevShape shape;
    private final ConcurrentHashMap<Long, NegevShape.Column> cache = new ConcurrentHashMap<>();

    private NegevColumns(DensityFunction temperature, DensityFunction continents, DensityFunction erosion, DensityFunction depth,
                         DensityFunction.NoiseHolder big, DensityFunction.NoiseHolder mid,
                         DensityFunction.NoiseHolder small, DensityFunction.NoiseHolder fine) {
        this.temperature = temperature;
        this.continents = continents;
        this.erosion = erosion;
        this.depth = depth;
        this.shape = new NegevShape(
                (x, z) -> big.getValue(x, 0, z), (x, z) -> mid.getValue(x, 0, z),
                (x, z) -> small.getValue(x, 0, z), (x, z) -> fine.getValue(x, 0, z));
    }

    /** One shared instance per world (keyed by that world's seeded noise). */
    static NegevColumns of(DensityFunction temperature, DensityFunction continents, DensityFunction erosion, DensityFunction depth,
                           DensityFunction.NoiseHolder big, DensityFunction.NoiseHolder mid,
                           DensityFunction.NoiseHolder small, DensityFunction.NoiseHolder fine) {
        Object key = big.noise();
        if (key == null) {
            return new NegevColumns(temperature, continents, erosion, depth, big, mid, small, fine);
        }
        synchronized (BY_NOISE) {
            return BY_NOISE.computeIfAbsent(key, k -> new NegevColumns(temperature, continents, erosion, depth, big, mid, small, fine));
        }
    }

    /** The instance wired to this world's "negev_big" noise, if the terrain has been set up. */
    public static NegevColumns forNoise(Object bigNoise) {
        synchronized (BY_NOISE) {
            return BY_NOISE.get(bigNoise);
        }
    }

    public NegevShape shape() {
        return shape;
    }

    private final NegevShape.Climate climate = new NegevShape.Climate() {
        public double temperature(int x, int z) { return temperature.compute(new DensityFunction.SinglePointContext(x, 0, z)); }
        public double continents(int x, int z) { return continents.compute(new DensityFunction.SinglePointContext(x, 0, z)); }
        public double erosion(int x, int z) { return erosion.compute(new DensityFunction.SinglePointContext(x, 0, z)); }
        public double depthAtZero(int x, int z) { return depth.compute(new DensityFunction.SinglePointContext(x, 0, z)); }
    };

    public NegevShape.Column get(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        NegevShape.Column col = cache.get(key);
        if (col == null) {
            if (cache.size() > 400_000) cache.clear();
            col = shape.column(x, z, climate);
            cache.put(key, col);
        }
        return col;
    }
}

package net.blandfordsfox.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;

import java.util.ArrayList;
import java.util.List;
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

    final DensityFunction temperature, vegetation, continents, erosion, ridges, depth;
    final NegevShape shape;
    private final ConcurrentHashMap<Long, NegevShape.Column> cache = new ConcurrentHashMap<>();

    private NegevColumns(DensityFunction temperature, DensityFunction vegetation, DensityFunction continents, DensityFunction erosion,
                         DensityFunction ridges, DensityFunction depth,
                         DensityFunction.NoiseHolder big, DensityFunction.NoiseHolder mid,
                         DensityFunction.NoiseHolder small, DensityFunction.NoiseHolder fine) {
        this.temperature = temperature;
        this.vegetation = vegetation;
        this.continents = continents;
        this.erosion = erosion;
        this.ridges = ridges;
        this.depth = depth;
        this.shape = new NegevShape(
                (x, z) -> big.getValue(x, 0, z), (x, z) -> mid.getValue(x, 0, z),
                (x, z) -> small.getValue(x, 0, z), (x, z) -> fine.getValue(x, 0, z));
    }

    /** One shared instance per world (keyed by that world's seeded noise). */
    static NegevColumns of(DensityFunction temperature, DensityFunction vegetation, DensityFunction continents, DensityFunction erosion,
                           DensityFunction ridges, DensityFunction depth,
                           DensityFunction.NoiseHolder big, DensityFunction.NoiseHolder mid,
                           DensityFunction.NoiseHolder small, DensityFunction.NoiseHolder fine) {
        Object key = big.noise();
        if (key == null) {
            return new NegevColumns(temperature, vegetation, continents, erosion, ridges, depth, big, mid, small, fine);
        }
        synchronized (BY_NOISE) {
            return BY_NOISE.computeIfAbsent(key, k -> new NegevColumns(temperature, vegetation, continents, erosion, ridges, depth, big, mid, small, fine));
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

    // -- where vanilla puts deserts: its own overworld biome table, on an 8-block grid
    private static volatile Climate.ParameterList<ResourceKey<Biome>> overworldBiomes;

    private static Climate.ParameterList<ResourceKey<Biome>> overworldBiomes() {
        Climate.ParameterList<ResourceKey<Biome>> list = overworldBiomes;
        if (list == null) {
            List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> points = new ArrayList<>();
            new OverworldBiomeBuilder().addBiomes(points::add);   // made public by META-INF/accesstransformer.cfg
            overworldBiomes = list = new Climate.ParameterList<>(points);
        }
        return list;
    }

    private static final int CELL = 8;
    private final ConcurrentHashMap<Long, Boolean> desertCells = new ConcurrentHashMap<>();

    private boolean desertCell(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
        Boolean d = desertCells.get(key);
        if (d == null) {
            if (desertCells.size() > 200_000) desertCells.clear();
            int x = cx * CELL + CELL / 2, z = cz * CELL + CELL / 2;
            DensityFunction.SinglePointContext p = new DensityFunction.SinglePointContext(x, 0, z);
            double t = temperature.compute(p);
            if (t < 0.5) {
                d = false;                                            // far too cool for desert: skip the lookup
            } else {
                Climate.TargetPoint target = Climate.target((float) t, (float) vegetation.compute(p), (float) continents.compute(p),
                        (float) erosion.compute(p), 0F, (float) ridges.compute(p));
                d = overworldBiomes().findValue(target) == Biomes.DESERT;
            }
            desertCells.put(key, d);
        }
        return d;
    }

    private final NegevShape.Climate climate = new NegevShape.Climate() {
        public double desertShare(int x, int z) {
            // tent-weighted share of desert cells within 24 blocks
            int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
            double sum = 0, wsum = 0;
            for (int i = -3; i <= 3; i++) for (int j = -3; j <= 3; j++) {
                double dx = (cx + i) * CELL + CELL / 2.0 - x, dz = (cz + j) * CELL + CELL / 2.0 - z;
                double w = Math.max(0, 1 - Math.abs(dx) / 24.0) * Math.max(0, 1 - Math.abs(dz) / 24.0);
                if (w <= 0) continue;
                wsum += w;
                if (desertCell(cx + i, cz + j)) sum += w;
            }
            return wsum > 0 ? sum / wsum : 0;
        }
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

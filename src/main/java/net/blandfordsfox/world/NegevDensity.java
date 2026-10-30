package net.blandfordsfox.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The two density functions that put the Negev shape into the overworld.
 * <ul>
 *   <li>{@code negev_terrain} wraps vanilla's "sloped cheese" (inside the 4×8×4 interpolation):
 *       the smooth shape of plateaus, valleys, dune wadis and dry rivers. Caves and
 *       aquifers keep working because they are still built on top of it.</li>
 *   <li>{@code negev_detail} wraps the final density (block by block, no interpolation):
 *       near the surface it swaps the smooth ramps for exact terrace steps.</li>
 * </ul>
 */
public final class NegevDensity {
    private NegevDensity() {}

    /** Shared fields: vanilla climate inputs and four seeded noises. */
    public record Inputs(DensityFunction temperature, DensityFunction vegetation, DensityFunction continents, DensityFunction erosion,
                         DensityFunction ridges, DensityFunction depth,
                         DensityFunction.NoiseHolder big, DensityFunction.NoiseHolder mid,
                         DensityFunction.NoiseHolder small, DensityFunction.NoiseHolder fine) {

        static final MapCodec<Inputs> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("temperature").forGetter(Inputs::temperature),
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("vegetation").forGetter(Inputs::vegetation),
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(Inputs::continents),
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("erosion").forGetter(Inputs::erosion),
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("ridges").forGetter(Inputs::ridges),
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("depth").forGetter(Inputs::depth),
                DensityFunction.NoiseHolder.CODEC.fieldOf("noise_big").forGetter(Inputs::big),
                DensityFunction.NoiseHolder.CODEC.fieldOf("noise_mid").forGetter(Inputs::mid),
                DensityFunction.NoiseHolder.CODEC.fieldOf("noise_small").forGetter(Inputs::small),
                DensityFunction.NoiseHolder.CODEC.fieldOf("noise_fine").forGetter(Inputs::fine)
        ).apply(i, Inputs::new));

        Inputs map(DensityFunction.Visitor v) {
            return new Inputs(temperature.mapAll(v), vegetation.mapAll(v), continents.mapAll(v), erosion.mapAll(v), ridges.mapAll(v), depth.mapAll(v),
                    v.visitNoise(big), v.visitNoise(mid), v.visitNoise(small), v.visitNoise(fine));
        }

        NegevColumns columns() {
            return NegevColumns.of(temperature, vegetation, continents, erosion, ridges, depth, big, mid, small, fine);
        }
    }

    // ------------------------------------------------------------------ coarse terrain
    public static final class Terrain implements DensityFunction {
        public static final MapCodec<Terrain> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(t -> t.input),
                Inputs.CODEC.forGetter(t -> t.inputs)
        ).apply(i, Terrain::new));
        public static final KeyDispatchDataCodec<Terrain> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

        final DensityFunction input;
        final Inputs inputs;
        private NegevColumns columns;

        public Terrain(DensityFunction input, Inputs inputs) {
            this.input = input;
            this.inputs = inputs;
        }

        private NegevColumns columns() {
            NegevColumns c = columns;
            if (c == null) columns = c = inputs.columns();
            return c;
        }

        @Override
        public double compute(FunctionContext ctx) {
            NegevShape.Column col = columns().get(ctx.blockX(), ctx.blockZ());
            double vanilla = input.compute(ctx);
            if (col.hot <= 0 && col.dry <= 0) return vanilla;
            return NegevShape.coarse(col, ctx.blockY(), vanilla);
        }

        @Override
        public void fillArray(double[] array, ContextProvider provider) {
            provider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            Terrain t = new Terrain(input.mapAll(visitor), inputs.map(visitor));
            t.columns();   // registers this world's columns so the painting features can find them
            return visitor.apply(t);
        }

        @Override
        public double minValue() {
            return Math.min(input.minValue(), -80);
        }

        @Override
        public double maxValue() {
            return Math.max(input.maxValue(), 80);
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    // ------------------------------------------------------------------ exact terraces
    public static final class Detail implements DensityFunction {
        public static final MapCodec<Detail> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(t -> t.input),
                Inputs.CODEC.forGetter(t -> t.inputs)
        ).apply(i, Detail::new));
        public static final KeyDispatchDataCodec<Detail> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

        final DensityFunction input;
        final Inputs inputs;
        private NegevColumns columns;

        public Detail(DensityFunction input, Inputs inputs) {
            this.input = input;
            this.inputs = inputs;
        }

        /** The shared column source, also used by the painting features. */
        public NegevColumns columns() {
            NegevColumns c = columns;
            if (c == null) columns = c = inputs.columns();
            return c;
        }

        @Override
        public double compute(FunctionContext ctx) {
            double fin = input.compute(ctx);
            NegevShape.Column col = columns().get(ctx.blockX(), ctx.blockZ());
            if (col.hot <= 0 && col.dry <= 0) return fin;
            return NegevShape.detail(col, ctx.blockY(), fin);
        }

        @Override
        public void fillArray(double[] array, ContextProvider provider) {
            provider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            Detail d = new Detail(input.mapAll(visitor), inputs.map(visitor));
            d.columns();
            return visitor.apply(d);
        }

        @Override
        public double minValue() {
            return Math.min(input.minValue(), -40);
        }

        @Override
        public double maxValue() {
            return Math.max(input.maxValue(), 40);
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }
}

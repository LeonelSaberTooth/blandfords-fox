package net.blandfordsfox.world;

/**
 * The shape of Negev-style desert terrain, worked out one column (x, z) at a time.
 * Plain Java with no Minecraft classes, so it can be tested and previewed on its own.
 *
 * <p>Inside hot, inland, non-badlands climate (where vanilla puts deserts) there are:
 * <ul>
 *   <li><b>Highlands:</b> a flat-topped plateau 6–30 blocks above the valley floor, cut by
 *       a network of branching valleys (wadis). Its walls are stepped terraces, with
 *       rare steep cliffs, and the plateau steps down the same way at its edges.</li>
 *   <li><b>Dune wadis:</b> single valleys winding through ordinary dunes, carved down
 *       into them.</li>
 *   <li><b>Dry rivers:</b> in about 60% of desert country, low ground is lifted to just
 *       above sea level so rivers and lakes become dry channels.</li>
 * </ul>
 * Everywhere else the column is left exactly as vanilla makes it.
 */
public final class NegevShape {

    /** A 2D noise sampled in blocks; roughly -1..1. */
    public interface Noise {
        double at(double x, double z);
    }

    /** Vanilla's climate at a column. */
    public interface Climate {
        /** 0..1: how much of the area around this column vanilla makes desert (smoothly weighted). */
        double desertShare(int x, int z);
        /** The overworld "depth" at y = 0; vanilla's surface sits near 128 * this. */
        double depthAtZero(int x, int z);
    }

    public static final int SEA_LEVEL = 63;
    /** Lowest a valley floor or dry channel may sit, so no aquifer water fills it. */
    public static final int MIN_FLOOR = 65;

    private final Noise big, mid, small, fine;

    public NegevShape(Noise big, Noise mid, Noise small, Noise fine) {
        this.big = big;
        this.mid = mid;
        this.small = small;
        this.fine = fine;
    }

    // -- channels: one noise sampled at different offsets and scales gives independent fields
    private static final double[][] OFF = {{0, 0}, {10301.5, -7731.25}, {-5413.75, 12007.5}, {20111.25, 3399.5}, {-17771.5, -15523.75}};

    private static double ch(Noise n, int k, double mult, double x, double z) {
        return n.at(x * mult + OFF[k][0], z * mult + OFF[k][1]);
    }

    double highland(double x, double z) { return ch(big, 0, 1.0, x, z); }
    double plateau(double x, double z)  { return ch(big, 1, 2.0, x, z); }
    double widthN(double x, double z)   { return ch(big, 2, 2.0, x, z); }
    double wadiGate(double x, double z) { return ch(big, 3, 2.0, x, z); }
    double dryGate(double x, double z)  { return ch(big, 4, 1.5, x, z); }
    double valleyA(double x, double z)  { return ch(mid, 0, 1.0, x, z); }
    double valleyB(double x, double z)  { return ch(mid, 1, 1.8, x, z); }
    double wadiN(double x, double z)    { return ch(mid, 2, 1.0, x, z); }
    double floorN(double x, double z)   { return ch(mid, 3, 1.0, x, z); }
    double cliffN(double x, double z)   { return ch(mid, 4, 1.6, x, z); }
    double stepN(double x, double z)    { return ch(small, 0, 1.0, x, z); }
    double hillsN(double x, double z)   { return ch(small, 1, 1.0, x, z); }
    /** Fine detail used when painting: rock-band wobble and surface patches. */
    public double wobble(double x, double z)   { return ch(fine, 0, 1.0, x, z); }
    public double patches(double x, double z)  { return ch(fine, 1, 1.0, x, z); }
    public double patches2(double x, double z) { return ch(fine, 2, 2.0, x, z); }

    // -- tunable numbers -------------------------------------------------------------
    /** Highland noise above this is plateau country (about 40% of desert). */
    public static double HIGHLAND_THRESHOLD = 0.06;
    /** Plateau height above the valley floor. */
    public static double PLATEAU_MIN = 6, PLATEAU_MAX = 30;
    /** Half the width of the flat valley floor, in blocks: main valleys, then side branches. */
    public static double MAIN_FLOOR_MIN = 2.5, MAIN_FLOOR_MAX = 12, SIDE_FLOOR_MIN = 1.0, SIDE_FLOOR_MAX = 4;
    /** Horizontal blocks per block of rise on stepped walls, and on cliffs. */
    public static double RUN_MIN = 0.8, RUN_MAX = 1.5, CLIFF_RUN = 0.12;
    /** Cliff noise above this makes a wall a cliff (rare). */
    public static double CLIFF_THRESHOLD = 0.42;
    /** Share of dune country that has a wadi line, via gate noise above this. */
    public static double WADI_GATE = 0.12;
    public static double WADI_MAX_DEPTH = 12;
    /** Dry-river gate: noise above this dries rivers (~60% of desert). */
    public static double DRY_GATE = -0.08;

    // -- one column ----------------------------------------------------------------------
    public enum Kind { VANILLA, FLOOR, SLOPE, TOP, WADI_FLOOR, WADI_SLOPE }

    public static final class Column {
        /** 0..1 how strongly this column is desert climate. */
        public double hot;
        /** 0..1 how much the highland shape replaces vanilla here. */
        public double high;
        /** Valley floor height (first air block above the floor), highland and wadi. */
        public int floor;
        /** Plateau height above the floor at this spot. */
        public int plateau;
        /** Highland surface (first air block), smooth ramp and exact terraces. */
        public double surfSmooth;
        public int surfExact;
        /** 0..1 how much the dune-wadi carve applies, and its surfaces. */
        public double wadi;
        public double wadiSmooth;
        public int wadiExact;
        public int wadiFloor;
        /** 0..1 how much low ground is lifted out of the water. */
        public double dry;
        /** Kind of ground, for painting (highland or wadi; VANILLA elsewhere). */
        public Kind kind = Kind.VANILLA;
    }

    private static double smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static double to01(double n) {
        return Math.max(0, Math.min(1, 0.5 + n * 1.25));
    }

    /** Distance in blocks to where f crosses zero, from its value and gradient. */
    private static double distToZero(Noise2 f, double x, double z, double value) {
        double gx = (f.at(x + 2, z) - value) / 2;
        double gz = (f.at(x, z + 2) - value) / 2;
        double g = Math.sqrt(gx * gx + gz * gz);
        return Math.abs(value) / Math.max(g, 1e-4);
    }

    private interface Noise2 {
        double at(double x, double z);
    }

    /**
     * Height above the floor at horizontal distance t past the floor's edge.
     * smooth: a straight ramp. exact: terraces of stepH blocks, each stepH*run wide.
     */
    private static double rampSmooth(double t, double total, double run) {
        if (t <= 0) return 0;
        return Math.min(total, t / run);
    }

    private static int rampExact(double t, double total, double run, int stepH, double jitter) {
        if (t <= 0) return 0;
        int steps = (int) Math.floor(t / (stepH * run) + jitter);
        return (int) Math.min(Math.round(total), Math.max(0, steps) * (long) stepH);
    }

    public Column column(int x, int z, Climate c) {
        Column col = new Column();
        col.hot = smooth(0.55, 0.95, c.desertShare(x, z));
        if (col.hot <= 0) return col;

        double vanillaSurface = 128 * c.depthAtZero(x, z);

        // dry rivers and lakes
        col.dry = col.hot * smooth(DRY_GATE, DRY_GATE + 0.12, dryGate(x, z));

        // valley floor height: near vanilla ground, never low enough for water
        double floorY = Math.max(MIN_FLOOR, Math.min(95, vanillaSurface + 1.5 * floorN(x, z)));
        col.floor = (int) Math.round(floorY);

        // wall style for this spot
        double cliff = cliffN(x, z);
        double run = cliff > CLIFF_THRESHOLD ? CLIFF_RUN
                : RUN_MIN + (RUN_MAX - RUN_MIN) * to01(stepN(x, z) * 0.8);
        double s = stepN(x * 0.5 + 911, z * 0.5 - 377);
        int stepH = s > 0.15 ? 3 : 2;
        double jitter = 0.5 + 0.35 * hillsN(x + 4000, z);

        // ---- highlands
        double h = highland(x, z) - HIGHLAND_THRESHOLD;
        double edge = h / Math.max(1e-4, gradLen(this::highland, x, z, h + HIGHLAND_THRESHOLD));   // + inside, blocks
        double highW = col.hot * smooth(-6, 0, edge);
        if (highW > 0) {
            double total = PLATEAU_MIN + (PLATEAU_MAX - PLATEAU_MIN) * to01(plateau(x, z));
            total += 1.5 * hillsN(x, z);                                         // gentle hills on top
            double wA = MAIN_FLOOR_MIN + (MAIN_FLOOR_MAX - MAIN_FLOOR_MIN) * to01(widthN(x, z));
            double wB = SIDE_FLOOR_MIN + (SIDE_FLOOR_MAX - SIDE_FLOOR_MIN) * to01(widthN(x + 5000, z));
            double dA = distToZero(this::valleyA, x, z, valleyA(x, z)) - wA;
            double dB = distToZero(this::valleyB, x, z, valleyB(x, z)) - wB;
            double dE = edge;
            // side branches only inside the plateau, not as moats along its foot
            double sm = Math.min(Math.min(rampSmooth(dA, total, run), rampSmooth(dB, total, run)), rampSmooth(dE, total, run));
            int sh = total > 16 ? stepH + 1 : stepH;                               // taller walls, taller steps
            int topAbs = (int) Math.round(floorY + total);                       // plateau top, absolute
            double cap = topAbs - col.floor;
            int ex = Math.min(Math.min(rampExact(dA, cap, run, sh, jitter), rampExact(dB, cap, run, sh, jitter)),
                    rampExact(dE, cap, run, sh, jitter));
            // Near the edge of the desert (or of the highlands) the plateau sinks towards the
            // floor instead of being blended with vanilla terrain, which would leave thin
            // floating crusts over air. The shape fully replaces vanilla once highW > 0.35.
            col.high = smooth(0.0, 0.35, highW);
            col.plateau = (int) Math.round(cap);
            col.surfSmooth = floorY + sm * highW;
            col.surfExact = col.floor + (int) Math.round(ex * highW);
            if (highW > 0.9) {
                col.kind = ex <= 0 ? Kind.FLOOR : ex >= col.plateau ? Kind.TOP : Kind.SLOPE;
            }
        }

        // ---- single wadis through the dunes
        double gate = smooth(WADI_GATE, WADI_GATE + 0.1, wadiGate(x, z));
        double wadiW = col.hot * (1 - highW) * gate;
        if (wadiW > 0) {
            double half = 1.5 + 3.0 * to01(widthN(x - 3000, z));
            double dW = distToZero(this::wadiN, x, z, wadiN(x, z)) - half;
            if (dW < WADI_MAX_DEPTH * RUN_MAX + 2) {
                double wf = Math.max(MIN_FLOOR, vanillaSurface - 1);
                // fades out by getting shallower, not by blending densities
                double lift = (1 - wadiW) * 40;
                col.wadi = smooth(0.0, 0.35, wadiW);
                col.wadiFloor = (int) Math.round(wf);
                col.wadiSmooth = wf + rampSmooth(dW, WADI_MAX_DEPTH, run) + lift;
                col.wadiExact = col.wadiFloor + rampExact(dW, WADI_MAX_DEPTH, run, stepH, jitter) + (int) Math.round(lift);
                if (wadiW > 0.9 && col.kind == Kind.VANILLA) {
                    col.kind = col.wadiExact <= col.wadiFloor ? Kind.WADI_FLOOR : Kind.WADI_SLOPE;
                }
            }
        }
        return col;
    }

    private static double gradLen(Noise2 f, double x, double z, double value) {
        double gx = (f.at(x + 2, z) - value) / 2;
        double gz = (f.at(x, z + 2) - value) / 2;
        return Math.sqrt(gx * gx + gz * gz);
    }

    // -- painting ------------------------------------------------------------------------
    public enum Material { SANDSTONE, GRANITE, PACKED_MUD, SAND, DIRT, GRASS }

    /** Rock layers: sandstone with thin bands of granite and packed mud, gently wavy. */
    public Material stratum(int x, int y, int z) {
        double region = big.at(x * 3 + 777, z * 3 - 555);
        int period = 6 + (int) Math.floor(4 * to01(region));            // 6..9 blocks between granite bands
        int yy = y + (int) Math.round(1.6 * wobble(x, z));
        int r = Math.floorMod(yy, period);
        if (r == 0) return Material.GRANITE;
        if (r == 1 && region > 0.15) return Material.GRANITE;
        if (r == period / 2 + 1) return Material.PACKED_MUD;
        return Material.SANDSTONE;
    }

    /**
     * What goes at height y in a painted column whose top solid block is at topY.
     * Returns null where the block should be left as it is.
     */
    public Material paint(int x, int y, int z, Column col, int topY) {
        Kind kind = col.kind;
        if (kind == Kind.VANILLA) return null;
        boolean floorKind = kind == Kind.FLOOR || kind == Kind.WADI_FLOOR;
        int floorY = kind == Kind.WADI_FLOOR || kind == Kind.WADI_SLOPE ? col.wadiFloor : col.floor;
        if (y < floorY - 5 || y > topY) return null;
        if (floorKind) {
            double p = patches(x, z) + 0.35 * patches2(x, z);
            Material top = p > 0.2 ? Material.GRASS : p < -0.08 ? Material.SAND : Material.DIRT;
            if (y == topY) return top;
            if (y >= topY - 3) return top == Material.SAND ? Material.SAND : Material.DIRT;
            return stratum(x, y, z);
        }
        if (y == topY) {
            if (kind == Kind.SLOPE || kind == Kind.WADI_SLOPE) {
                // ledges: often capped with granite where a granite band runs out
                if (patches(x + 100, z - 100) > 0.12 || stratum(x, y, z) == Material.GRANITE) return Material.GRANITE;
                return Material.SANDSTONE;
            }
            double p2 = patches2(x, z);
            if (p2 > 0.45) return Material.SAND;
            if (p2 < -0.55) return Material.GRANITE;
            return Material.SANDSTONE;
        }
        return stratum(x, y, z);
    }

    // -- densities -----------------------------------------------------------------------
    /** Blocks-to-density scale for the smooth shape (close to vanilla's terrain slope). */
    static final double K_COARSE = 0.15;
    static final double K_DETAIL = 0.1;

    /** The smooth terrain that replaces vanilla's "sloped cheese" (interpolated, caves work). */
    public static double coarse(Column col, double y, double vanilla) {
        double v = vanilla;
        if (col.dry > 0) {
            v = lerp(col.dry, v, Math.max(v, (MIN_FLOOR - 0.5 - y) * K_COARSE));
        }
        if (col.high > 0) {
            v = lerp(col.high, v, (col.surfSmooth - 0.5 - y) * K_COARSE);
        }
        if (col.wadi > 0) {
            v = lerp(col.wadi, v, Math.min(v, (col.wadiSmooth - 0.5 - y) * K_COARSE));
        }
        return v;
    }

    /** Exact block-by-block terraces, applied near the surface on top of the final density. */
    public static double detail(Column col, double y, double fin) {
        double v = fin;
        if (col.high > 0) {
            double lo = Math.min(col.surfSmooth, col.surfExact) - 2, hi = Math.max(col.surfSmooth, col.surfExact) + 2;
            if (y >= lo && y <= hi) v = lerp(col.high, v, (col.surfExact - 0.5 - y) * K_DETAIL);
        }
        if (col.wadi > 0) {
            double lo = Math.min(col.wadiSmooth, col.wadiExact) - 2, hi = Math.max(col.wadiSmooth, col.wadiExact) + 2;
            if (y >= lo && y <= hi) v = lerp(col.wadi, v, Math.min(v, (col.wadiExact - 0.5 - y) * K_DETAIL));
        }
        return v;
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}

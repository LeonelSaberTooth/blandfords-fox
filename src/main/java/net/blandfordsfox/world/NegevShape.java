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
        /** 0..1: the same, counting rivers that run through hot desert climate as desert too. */
        double dryShare(int x, int z);
        /** 0..1: share of nearby ground that is river biome in hot desert climate. */
        double riverShare(int x, int z);
        /** Vanilla's weirdness ("ridges"): rivers run where it is between -0.05 and 0.05. */
        double weirdness(int x, int z);
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
    private static final double[][] OFF = {{0, 0}, {10301.5, -7731.25}, {-5413.75, 12007.5}, {20111.25, 3399.5}, {-17771.5, -15523.75},
            {7919.25, 23117.5}, {-29303.5, 4441.75}, {13331.75, -26987.25}};

    private static double ch(Noise n, int k, double mult, double x, double z) {
        return n.at(x * mult + OFF[k][0], z * mult + OFF[k][1]);
    }

    double highland(double x, double z) { return ch(big, 0, 1.0, x, z); }
    double plateau(double x, double z)  { return ch(big, 1, 2.0, x, z); }
    double widthN(double x, double z)   { return ch(big, 2, 2.0, x, z); }
    double wadiGate(double x, double z) { return ch(big, 3, 2.0, x, z); }
    double dryGate(double x, double z)  { return ch(big, 4, 1.5, x, z); }
    // valleys follow zero lines of these; low frequency for long valleys, warped so they meander
    private double warpX(double x, double z) { return x + 70 * ch(mid, 5, 0.8, x, z); }
    private double warpZ(double x, double z) { return z + 70 * ch(mid, 6, 0.8, x, z); }
    double valleyA(double x, double z)  { return ch(mid, 0, 0.45, warpX(x, z), warpZ(x, z)); }
    double valleyB(double x, double z)  { return ch(mid, 1, 0.9, warpX(x, z), warpZ(x, z)); }
    double wadiN(double x, double z)    { return ch(mid, 2, 0.4, warpX(x, z), warpZ(x, z)); }
    double floorN(double x, double z)   { return ch(mid, 3, 1.0, x, z); }
    double cliffN(double x, double z)   { return ch(mid, 4, 1.6, x, z); }
    double terraceN(double x, double z) { return ch(big, 5, 4.0, x, z); }
    /** Small valleys cut into the plateau top: frequent, narrow, 5-9 blocks deep. */
    double valleyC(double x, double z)  { return ch(mid, 7, 1.7, warpX(x, z), warpZ(x, z)); }
    double smallGate(double x, double z) { return ch(big, 7, 3.0, x, z); }
    double wallStyleN(double x, double z) { return ch(big, 6, 5.0, x, z); }
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
    /** Horizontal blocks per block of rise on plain (non-terraced) slopes. */
    public static double SLOPE_RUN_MIN = 1.0, SLOPE_RUN_MAX = 2.2;
    /** Terrace noise above this makes a wall terraced: about 15% of walls. */
    public static double TERRACE_THRESHOLD = 0.34;
    /** Cliff noise above this makes a wall a cliff (rare). */
    public static double CLIFF_THRESHOLD = 0.42;
    /** A cliff is near-vertical for at most this many blocks; above that the wall slopes. */
    public static int CLIFF_MAX = 7;
    /** Share of dune country that has a wadi line, via gate noise above this. */
    public static double WADI_GATE = 0.12;
    public static double WADI_MAX_DEPTH = 12;
    /** Dry-river gate: noise above this dries rivers and lakes (~90% of desert country). */
    public static double DRY_GATE = -0.4;
    /** Distance into the highlands (blocks) over which plateaus grow to full height. */
    public static double GROW_DISTANCE = 110;

    // -- one column ----------------------------------------------------------------------
    public enum Kind { VANILLA, FLOOR, SLOPE, TOP, WADI_FLOOR, WADI_SLOPE, RIVER_FLOOR, RIVER_SLOPE, RIVER_SAND }

    /** Dried desert rivers: banks climb one block per this many blocks out from the river bed. */
    public static double RIVER_BANK_RUN = 1.3;

    /** Overhanging wall shapes: a slope under a limestone cliff band with a lip, or an undercut foot. */
    public static final int CAPROCK = 1, UNDERCUT = 2;

    /** Exact geometry of an overhanging highland wall, checked block by block. */
    public static final class Walls {
        final double[] t = new double[3];
        final double[] run = new double[3];
        final boolean[] shaped = new boolean[3];
        int n;
        public int capAbs, floor;
        int style, capH, undercutH;
        double jitter;

        void add(double t, double run, boolean shaped) {
            this.t[n] = t; this.run[n] = run; this.shaped[n] = shaped; n++;
        }

        private double face(int i, int yRel) {
            double run = this.run[i];
            if (!shaped[i]) return (yRel + 1 - jitter) * run;
            int capRel = capAbs - floor;
            if (style == CAPROCK) {
                int low = Math.max(1, capRel - capH);
                if (yRel < low) return (yRel + 1 - jitter) * run;
                double top = (low + 1 - jitter) * run + 0.3;
                return yRel >= capRel - 1 ? top - 0.7 : top;                   // lip overhangs the cliff slightly
            }
            if (yRel < undercutH) return 0.6 + (undercutH - yRel) * 0.45;     // recessed foot, slight overhang
            return 0.6 + (yRel - undercutH + 1 - jitter) * run;
        }

        /** Is the block at absolute height y rock? */
        public boolean solid(int y) {
            if (y >= capAbs) return false;
            int yRel = y - floor;
            if (yRel < 0) return true;
            for (int i = 0; i < n; i++) {
                if (t[i] <= 0 || t[i] < face(i, yRel)) return false;
            }
            return true;
        }
    }

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
        /** Whether this spot's walls are terraces (or cliffs) rather than a plain slope. */
        public boolean terraced;
        /** Exact overhanging walls, where this spot has that wall style (else null). */
        public Walls walls;
        /** Floor of a small valley on the plateau top, when this column is in one (else 0). */
        public int hangingFloor;
        /** Dried desert river: 0..1 how much its valley shape applies, and the surface it wants. */
        public double river;
        public double riverSurface;
        /** Height of the river valley's banks above its bed (where they meet the dunes). */
        public double riverBankH;
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

    /** A wall: plain or terraced ramp, or (cliffH > 0) a short near-vertical cliff with a slope above it. */
    private static double wallSmooth(double t, double total, double run, int cliffH) {
        if (cliffH <= 0) return rampSmooth(t, total, run);
        if (t <= 0) return 0;
        double c = Math.min(cliffH, t / CLIFF_RUN);
        return Math.min(total, c + Math.max(0, t - cliffH * CLIFF_RUN) / run);
    }

    private static int wallExact(double t, double total, double run, int stepH, double jitter, int cliffH) {
        if (cliffH <= 0) return rampExact(t, total, run, stepH, jitter);
        if (t <= 0) return 0;
        int c = (int) Math.min(cliffH, Math.floor(t / CLIFF_RUN + jitter));
        int above = (int) Math.max(0, Math.floor((t - cliffH * CLIFF_RUN) / run + jitter));
        return (int) Math.min(Math.round(total), c + (c >= cliffH ? above : 0));
    }

    private static int rampExact(double t, double total, double run, int stepH, double jitter) {
        if (t <= 0) return 0;
        int steps = (int) Math.floor(t / (stepH * run) + jitter);
        return (int) Math.min(Math.round(total), Math.max(0, steps) * (long) stepH);
    }

    public Column column(int x, int z, Climate c) {
        Column col = new Column();
        col.hot = smooth(0.55, 0.95, c.desertShare(x, z));
        // dry rivers and lakes (rivers are their own biome, so they count separately)
        double dryArea = smooth(0.3, 0.8, c.dryShare(x, z));
        if (dryArea > 0) col.dry = dryArea * smooth(DRY_GATE, DRY_GATE + 0.12, dryGate(x, z));
        // A dried desert river becomes a valley like the others: a narrow river bed down the
        // middle of vanilla's river, banks of layered rock climbing out of it, and the top of
        // the banks meeting the dunes where vanilla's river band ends.
        if (col.dry > 0.9 && c.riverShare(x, z) > 0) riverValley(x, z, c, col);
        if (col.hot <= 0) return col;

        double vanillaSurface = 128 * c.depthAtZero(x, z);

        // valley floor height: near vanilla ground, never low enough for water
        double floorY = Math.max(MIN_FLOOR, Math.min(95, vanillaSurface + 1.5 * floorN(x, z)));
        col.floor = (int) Math.round(floorY);

        // wall style for this spot
        // ~85% of walls are plain slopes (one block up every `run` blocks), ~15% are terraces
        // of 2-4 block steps, and a few are near-vertical cliffs
        boolean cliff = cliffN(x, z) > CLIFF_THRESHOLD;
        boolean terraced = terraceN(x, z) > TERRACE_THRESHOLD;
        double slopeRun = SLOPE_RUN_MIN + (SLOPE_RUN_MAX - SLOPE_RUN_MIN) * to01(stepN(x, z) * 0.8);
        double run = terraced ? RUN_MIN + (RUN_MAX - RUN_MIN) * to01(stepN(x, z) * 0.8) : slopeRun;
        double s = stepN(x * 0.5 + 911, z * 0.5 - 377);
        int stepH = !terraced ? 1 : s > 0.15 ? 3 : 2;
        col.terraced = terraced;
        // cliffs: a near-vertical face 4-7 blocks tall at the foot of the wall, plain slope above
        int cliffH = cliff ? 4 + (int) Math.round(to01(hillsN(x + 300, z)) * (CLIFF_MAX - 4)) : 0;
        // of the plain slopes, about a quarter have a limestone "roof" and a fifth an undercut foot
        double ws = wallStyleN(x, z);
        int overhang = terraced || cliff ? 0 : ws > 0.25 ? UNDERCUT : ws > 0.04 ? CAPROCK : 0;
        double jitter = 0.5 + 0.35 * hillsN(x + 4000, z);

        // ---- highlands
        double h = highland(x, z) - HIGHLAND_THRESHOLD;
        double edge = h / Math.max(1e-4, gradLen(this::highland, x, z, h + HIGHLAND_THRESHOLD));   // + inside, blocks
        double highW = col.hot * smooth(-6, 0, edge) * (1 - col.river);
        if (highW > 0) {
            double total = PLATEAU_MIN + (PLATEAU_MAX - PLATEAU_MIN) * to01(plateau(x, z));
            // the plateau starts low where it meets the dunes and rises the further in you go
            total = PLATEAU_MIN * 0.6 + (total - PLATEAU_MIN * 0.6) * smooth(0, GROW_DISTANCE, edge);
            total += 1.5 * hillsN(x, z);                                         // gentle hills on top
            double wA = MAIN_FLOOR_MIN + (MAIN_FLOOR_MAX - MAIN_FLOOR_MIN) * to01(widthN(x, z));
            double wB = SIDE_FLOOR_MIN + (SIDE_FLOOR_MAX - SIDE_FLOOR_MIN) * to01(widthN(x + 5000, z));
            double dA = distToZero(this::valleyA, x, z, valleyA(x, z)) - wA;
            double dB = distToZero(this::valleyB, x, z, valleyB(x, z)) - wB;
            double dE = edge;
            // side branches only inside the plateau, not as moats along its foot
            double edgeRun = Math.max(run, 1.0) * 2.5;                             // outer edges climb gently
            double sm = Math.min(Math.min(wallSmooth(dA, total, run, cliffH), wallSmooth(dB, total, run, cliffH)), rampSmooth(dE, total, edgeRun));
            int sh = stepH > 1 && total > 16 ? stepH + 1 : stepH;                  // taller walls, taller steps
            int topAbs = (int) Math.round(floorY + total);                       // plateau top, absolute
            double cap = topAbs - col.floor;
            int ex = Math.min(Math.min(wallExact(dA, cap, run, sh, jitter, cliffH), wallExact(dB, cap, run, sh, jitter, cliffH)),
                    rampExact(dE, cap, edgeRun, sh, jitter));

            // small valleys, 5-9 blocks deep, cut into the plateau top
            boolean inSmall = false;
            int depthC = (int) Math.min(5 + Math.round(4 * to01(plateau(x + 700, z))), Math.round(cap) - 2);
            if (depthC >= 4 && smallGate(x, z) > -0.2) {
                double wC = 1.5 + 2.5 * to01(widthN(x - 7000, z));
                double dC = distToZero(this::valleyC, x, z, valleyC(x, z)) - wC;
                if (dC < depthC * Math.max(run, 1) + 2) {
                    int baseRel = (int) Math.round(cap) - depthC;
                    int exC = baseRel + wallExact(dC, depthC, run, stepH, jitter, Math.min(cliffH, depthC));
                    sm = Math.min(sm, baseRel + wallSmooth(dC, depthC, run, Math.min(cliffH, depthC)));
                    if (exC < ex) {
                        ex = exC;
                        inSmall = true;
                        if (exC == baseRel) col.hangingFloor = col.floor + baseRel;
                    }
                }
            }
            // Near the edge of the desert (or of the highlands) the plateau sinks towards the
            // floor instead of being blended with vanilla terrain, which would leave thin
            // floating crusts over air. The shape fully replaces vanilla once highW > 0.35.
            col.high = smooth(0.0, 0.35, highW);
            col.plateau = (int) Math.round(cap);
            col.surfSmooth = floorY + sm * highW;
            col.surfExact = col.floor + (int) Math.round(ex * highW);
            // only where the plateau is at full height: in the fade zone heights are scaled down,
            // and unscaled overhang walls there would stand up as sheer steps
            if (overhang != 0 && highW >= 0.97 && !inSmall) {
                Walls w = new Walls();
                w.capAbs = topAbs;
                w.floor = col.floor;
                w.style = overhang;
                w.jitter = jitter;
                w.capH = 3 + (int) Math.round(to01(hillsN(x + 900, z)) * 2);      // 3-5 block cliff band
                w.undercutH = 2 + (int) Math.round(to01(hillsN(x - 900, z)));     // 2-3 block recessed foot
                w.add(dA, run, true);
                w.add(dB, run, true);
                w.add(dE, edgeRun, false);
                int top = col.floor;
                for (int y = topAbs - 1; y >= col.floor; y--) if (w.solid(y)) { top = y + 1; break; }
                col.walls = w;
                col.surfExact = top;
                ex = top - col.floor;
            }
            if (highW > 0.6) {
                col.kind = ex <= 0 || col.hangingFloor > 0 ? Kind.FLOOR : ex >= col.plateau ? Kind.TOP : Kind.SLOPE;
            }
            if (highW < 0.97) col.hangingFloor = 0;
        }

        // ---- single wadis through the dunes
        double gate = smooth(WADI_GATE, WADI_GATE + 0.1, wadiGate(x, z));
        double wadiW = col.hot * (1 - highW) * gate * (1 - col.river);
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

    private void riverValley(int x, int z, Climate c, Column col) {
        double w = c.weirdness(x, z);
        if (Math.abs(w) > 0.12) return;
        double gx = (c.weirdness(x + 2, z) - w) / 2, gz = (c.weirdness(x, z + 2) - w) / 2;
        double g = Math.max(1e-5, Math.sqrt(gx * gx + gz * gz));
        double fromCentre = Math.abs(w) / g;                  // blocks from the river's centre line
        double bandHalf = 0.05 / g;                           // vanilla's river band, half width
        if (fromCentre > bandHalf + 4) return;
        double bedHalf = Math.max(3, Math.min(7, 2 + 0.25 * bandHalf));
        // dune height where the band ends: sample vanilla's ground a little past the band edge
        double nx = gx / g * Math.signum(w), nz = gz / g * Math.signum(w);
        double out = bandHalf - fromCentre + 4;
        double dune = 128 * c.depthAtZero((int) Math.round(x + nx * out), (int) Math.round(z + nz * out)) + 1;
        double bankH = Math.max(3, Math.min(14, dune - MIN_FLOOR));
        col.riverBankH = bankH;
        col.riverSurface = MIN_FLOOR + Math.min(bankH, Math.max(0, fromCentre - bedHalf) / RIVER_BANK_RUN);
        col.river = smooth(bandHalf + 4, bandHalf - 3, fromCentre);
        if (col.river > 0.5) {
            col.kind = fromCentre <= bedHalf ? Kind.RIVER_FLOOR
                    : col.riverSurface < MIN_FLOOR + bankH - 0.5 ? Kind.RIVER_SLOPE : Kind.RIVER_SAND;
        }
    }

    private static double gradLen(Noise2 f, double x, double z, double value) {
        double gx = (f.at(x + 2, z) - value) / 2;
        double gz = (f.at(x, z + 2) - value) / 2;
        return Math.sqrt(gx * gx + gz * gz);
    }

    // -- painting ------------------------------------------------------------------------
    /** PACKED_MUD bands and DIRT river beds are now limestone gravel in game (see NegevFeatures.block). */
    public enum Material { SANDSTONE, GRANITE, PACKED_MUD, LIMESTONE, SAND, DIRT, GRASS }

    /** Rock layers: sandstone with thin bands of granite and packed mud, gently wavy. */
    /**
     * Rock layers: an irregular stack of limestone, sandstone, granite and packed mud with
     * varying thicknesses (no fixed repeat), level like real strata, gently wavy, and drifting
     * up or down from place to place so neighbouring walls don't show the identical sequence.
     */
    private Material[] layers;

    private Material[] layers() {
        Material[] l = layers;
        if (l != null) return l;
        l = new Material[768];
        java.util.Random r = new java.util.Random(Double.doubleToLongBits(big.at(1234.5, -987.25)) ^ 0x5DEECE66DL);
        Material last = null;
        int i = 0;
        while (i < l.length) {
            Material m;
            do {
                double p = r.nextDouble();
                m = p < 0.46 ? Material.LIMESTONE : p < 0.66 ? Material.SANDSTONE : p < 0.84 ? Material.GRANITE : Material.PACKED_MUD;
            } while (m == last);
            int thick = switch (m) {
                case LIMESTONE -> 1 + r.nextInt(3);
                case SANDSTONE -> 1 + r.nextInt(2);
                case GRANITE -> r.nextInt(3) == 0 ? 2 : 1;
                default -> 1;
            };
            for (int k = 0; k < thick && i < l.length; k++) l[i++] = m;
            last = m;
        }
        return layers = l;
    }

    public Material stratum(int x, int y, int z) {
        int shift = (int) Math.round(7 * big.at(x * 2.0 + 777, z * 2.0 - 555));   // slow up/down drift
        int yy = y + (int) Math.round(1.6 * wobble(x, z)) + shift;
        return layers()[Math.floorMod(yy + 256, 768)];
    }

    /**
     * What goes at height y in a painted column whose top solid block is at topY.
     * Returns null where the block should be left as it is.
     */
    public Material paint(int x, int y, int z, Column col, int topY) {
        Kind kind = col.kind;
        int floorY = kind == Kind.WADI_FLOOR || kind == Kind.WADI_SLOPE ? col.wadiFloor
                : col.hangingFloor > 0 ? col.hangingFloor : col.floor;
        return paint(x, y, z, col, topY, kind, floorY);
    }

    /** Paint as the given kind of ground with the given valley-floor height (used for dried rivers). */
    public Material paint(int x, int y, int z, Column col, int topY, Kind kind, int floorY) {
        // the rest of a dried river's old biome area: ordinary desert sand, like the dunes around it
        if (kind == Kind.RIVER_SAND) {
            if (y > topY || y < topY - 6) return null;
            return y >= topY - 3 ? Material.SAND : Material.SANDSTONE;
        }
        // dried rivers paint exactly like valleys: the bed as river bed, the banks as plain-slope rock
        boolean river = kind == Kind.RIVER_FLOOR || kind == Kind.RIVER_SLOPE;
        if (kind == Kind.RIVER_FLOOR) kind = Kind.FLOOR;
        else if (kind == Kind.RIVER_SLOPE) kind = Kind.SLOPE;
        if (kind == Kind.VANILLA) return null;
        boolean floorKind = kind == Kind.FLOOR || kind == Kind.WADI_FLOOR;
        if (y < floorY - 5 || y > topY) return null;
        if (river && kind == Kind.SLOPE) return stratum(x, y, z);
        if (floorKind) {
            // small patches: fine noise plus a per-block shake
            double p = 0.55 * patches2(x * 2.0, z * 2.0) + 0.25 * patches(x, z) + 0.35 * (hash(x, z) - 0.5);
            Material top = p > 0.12 ? Material.GRASS : p < -0.1 ? Material.SAND : Material.DIRT;
            if (y == topY) return top;
            if (y >= topY - 3) return top == Material.SAND ? Material.SAND : Material.DIRT;
            return stratum(x, y, z);
        }
        // the top few blocks under the plateau top are limestone: the "roof" over the walls
        if ((kind == Kind.SLOPE || kind == Kind.TOP) && col.plateau > 0 && y >= col.floor + col.plateau - 3) return Material.LIMESTONE;
        if (kind == Kind.WADI_SLOPE && topY + 1 > col.wadiExact) {
            // dune above the carved wadi wall: keep its sand cap, layer the rock under it
            if (y >= topY - 2) return null;
            return stratum(x, y, z);
        }
        if (y == topY) {
            if (kind == Kind.SLOPE || kind == Kind.WADI_SLOPE) {
                // plain slopes show the rock bands as stripes across the surface
                if (!col.terraced) return stratum(x, y, z);
                // terrace ledges: often capped with granite where a granite band runs out
                if (patches(x + 100, z - 100) > 0.12 || stratum(x, y, z) == Material.GRANITE) return Material.GRANITE;
                return Material.SANDSTONE;
            }
            return Material.LIMESTONE;                                    // plateau tops: always limestone
        }
        return stratum(x, y, z);
    }

    private static double hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 31); h *= 0xBF58476D1CE4E5B9L; h ^= (h >>> 29);
        return (h >>> 11) * 0x1.0p-53;
    }

    // -- densities -----------------------------------------------------------------------
    /** Blocks-to-density scale for the smooth shape (close to vanilla's terrain slope). */
    static final double K_COARSE = 0.15;
    static final double K_DETAIL = 0.1;
    /** Blocks under the shaped surface where caves are filled in. */
    static final int SEALED = 8;

    /** The smooth terrain that replaces vanilla's "sloped cheese" (interpolated, caves work). */
    public static double coarse(Column col, double y, double vanilla) {
        double v = vanilla;
        if (col.dry > 0) {
            v = lerp(col.dry, v, Math.max(v, (MIN_FLOOR - 0.5 - y) * K_COARSE));
        }
        if (col.river > 0) {
            v = lerp(col.river, v, (col.riverSurface - 0.5 - y) * K_COARSE);
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
        if (col.walls != null) {
            // overhanging walls, block by block, from the valley floor to the plateau top
            int yi = (int) Math.floor(y);
            if (yi >= col.floor - 1 && yi <= col.walls.capAbs) return col.walls.solid(yi) ? K_DETAIL : -K_DETAIL;
            if (yi < col.floor - 1 && yi >= col.floor - 1 - SEALED) return Math.max(v, K_DETAIL);
            return v;
        }
        if (col.high > 0) {
            double lo = Math.min(col.surfSmooth, col.surfExact) - 2, hi = Math.max(col.surfSmooth, col.surfExact) + 2;
            if (y >= lo && y <= hi) v = lerp(col.high, v, (col.surfExact - 0.5 - y) * K_DETAIL);
            // no cave openings just under the surface: they left 2-block roofs over air or water
            else if (y < lo && y >= lo - SEALED) v = lerp(col.high, v, Math.max(v, (col.surfExact - 0.5 - y) * K_DETAIL));
        }
        if (col.wadi > 0) {
            double lo = Math.min(col.wadiSmooth, col.wadiExact) - 2, hi = Math.max(col.wadiSmooth, col.wadiExact) + 2;
            if (y >= lo && y <= hi) v = lerp(col.wadi, v, Math.min(v, (col.wadiExact - 0.5 - y) * K_DETAIL));
            // fill caves under the wadi floor (only below floor level, so nothing is built above the ground)
            else if (col.wadi >= 0.99 && v < 0 && y < col.wadiFloor - 0.5 && y >= col.wadiFloor - SEALED)
                v = (col.wadiFloor - 0.5 - y) * K_DETAIL;
        }
        return v;
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}

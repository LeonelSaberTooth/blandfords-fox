import java.util.Random;

/** Stand-in for Minecraft's NormalNoise (improved Perlin octaves, two offset samples). */
final class McNoise {
    static final class Improved {
        final int[] p = new int[512]; final double xo, yo, zo;
        Improved(Random r) {
            xo = r.nextDouble() * 256; yo = r.nextDouble() * 256; zo = r.nextDouble() * 256;
            int[] perm = new int[256]; for (int i = 0; i < 256; i++) perm[i] = i;
            for (int i = 0; i < 256; i++) { int j = r.nextInt(256 - i) + i; int t = perm[i]; perm[i] = perm[j]; perm[j] = t; }
            for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
        }
        static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
        static final int[][] G = {{1,1,0},{-1,1,0},{1,-1,0},{-1,-1,0},{1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},{0,1,1},{0,-1,1},{0,1,-1},{0,-1,-1},{1,1,0},{0,-1,1},{-1,1,0},{0,-1,-1}};
        static double grad(int h, double x, double y, double z) {
            int[] g = G[h & 15]; return g[0] * x + g[1] * y + g[2] * z;
        }
        double noise(double x, double y, double z) {
            x += xo; y += yo; z += zo;
            int X = (int) Math.floor(x), Y = (int) Math.floor(y), Z = (int) Math.floor(z);
            double fx = x - X, fy = y - Y, fz = z - Z; X &= 255; Y &= 255; Z &= 255;
            double u = fade(fx), v = fade(fy), w = fade(fz);
            int A = p[X] + Y, AA = p[A] + Z, AB = p[A + 1] + Z, B = p[X + 1] + Y, BA = p[B] + Z, BB = p[B + 1] + Z;
            return lerp(w, lerp(v, lerp(u, grad(p[AA], fx, fy, fz), grad(p[BA], fx - 1, fy, fz)),
                                   lerp(u, grad(p[AB], fx, fy - 1, fz), grad(p[BB], fx - 1, fy - 1, fz))),
                           lerp(v, lerp(u, grad(p[AA + 1], fx, fy, fz - 1), grad(p[BA + 1], fx - 1, fy, fz - 1)),
                                   lerp(u, grad(p[AB + 1], fx, fy - 1, fz - 1), grad(p[BB + 1], fx - 1, fy - 1, fz - 1))));
        }
        static double lerp(double t, double a, double b) { return a + t * (b - a); }
    }
    static final class Perlin {
        final Improved[] oct; final double[] amp; final int first; final double lowFreq, highValue;
        Perlin(Random r, int first, double[] amp) {
            this.first = first; this.amp = amp; oct = new Improved[amp.length];
            for (int i = 0; i < amp.length; i++) oct[i] = new Improved(r);
            lowFreq = Math.pow(2, first); highValue = Math.pow(2, amp.length - 1) / (Math.pow(2, amp.length) - 1);
        }
        double value(double x, double y, double z) {
            double v = 0, f = lowFreq, a = highValue;
            for (int i = 0; i < oct.length; i++) { v += amp[i] * oct[i].noise(x * f, y * f, z * f) * a; f *= 2; a /= 2; }
            return v;
        }
    }
    final Perlin a, b; final double factor;
    McNoise(long seed, int first, double... amp) {
        Random r = new Random(seed);
        a = new Perlin(r, first, amp); b = new Perlin(r, first, amp);
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < amp.length; i++) if (amp[i] != 0) { lo = Math.min(lo, i); hi = Math.max(hi, i); }
        factor = (1.0 / 6.0) / (0.1 * (1 + 1.0 / (hi - lo + 1)));
    }
    double at(double x, double z) {
        double m = 1.0181268882175227;
        return (a.value(x, 0, z) + b.value(x * m, 0, z * m)) * factor;
    }
}

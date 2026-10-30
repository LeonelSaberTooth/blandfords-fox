import net.blandfordsfox.world.NegevShape;
import java.io.*;

/** Runs NegevShape over an area and writes per-column top height and kind for previews. */
public class Preview {
    public static void main(String[] args) throws Exception {
        int size = Integer.parseInt(args[0]); int ox = Integer.parseInt(args[1]), oz = Integer.parseInt(args[2]);
        long seed = Long.parseLong(args[3]); int step = args.length > 5 ? Integer.parseInt(args[5]) : 1;
        McNoise big = new McNoise(seed + 1, -10, 1, 1), mid = new McNoise(seed + 2, -8, 1, 0.5, 0.25),
                small = new McNoise(seed + 3, -6, 1, 0.5), fine = new McNoise(seed + 4, -4, 1, 0.5);
        McNoise dunes = new McNoise(seed + 9, -6, 1, 0.5, 0.25), base = new McNoise(seed + 10, -9, 1, 1);
        NegevShape shape = new NegevShape(big::at, mid::at, small::at, fine::at);
        NegevShape.Climate hot = new NegevShape.Climate() {
            public double desertShare(int x, int z) { return 1.0; }
            public double depthAtZero(int x, int z) { return (68 + 4 * base.at(x, z)) / 128.0; }
        };
        try (PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(args[4])))) {
            w.println(size);
            for (int zi = 0; zi < size; zi++) { int z = oz + zi * step;
                StringBuilder sb = new StringBuilder();
                for (int xi = 0; xi < size; xi++) { int x = ox + xi * step;
                    NegevShape.Column c = shape.column(x, z, hot);
                    double vanilla = 128 * hot.depthAtZero(x, z) + 5 * dunes.at(x, z) + 2;   // stand-in dunes
                    if (vanilla < 63) vanilla = 63;
                    double top = vanilla;
                    if (c.dry > 0.5) top = Math.max(top, NegevShape.MIN_FLOOR);
                    if (c.high > 0) top = top + (c.surfExact - top) * c.high;
                    if (c.wadi > 0.5) top = Math.min(top, c.wadiExact);
                    String kind = c.kind.name();
                    if ((kind.startsWith("WADI")) && Math.round(top) != c.wadiExact) kind = "VANILLA";
                    sb.append((int) Math.round(top)).append(':').append(kind.charAt(0) == 'W' ? (kind.equals("WADI_FLOOR") ? 'w' : 'v') : kind.charAt(0)).append(' ');
                }
                w.println(sb);
            }
        }
    }
}

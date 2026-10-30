import net.blandfordsfox.world.NegevShape;
import java.io.*;

/** Writes column tops and painted materials (top down 40 blocks) for an isometric render. */
public class Voxels {
    public static void main(String[] args) throws Exception {
        int size = Integer.parseInt(args[0]), ox = Integer.parseInt(args[1]), oz = Integer.parseInt(args[2]);
        long seed = Long.parseLong(args[3]);
        boolean river = args.length > 5;            // test river: straight along x through the middle
        int rz = oz + size / 2;
        McNoise big = new McNoise(seed + 1, -10, 1, 1), mid = new McNoise(seed + 2, -8, 1, 0.5, 0.25),
                small = new McNoise(seed + 3, -6, 1, 0.5), fine = new McNoise(seed + 4, -4, 1, 0.5);
        McNoise dunes = new McNoise(seed + 9, -6, 1, 0.5, 0.25), base = new McNoise(seed + 10, -9, 1, 1);
        NegevShape shape = new NegevShape(big::at, mid::at, small::at, fine::at);
        NegevShape.Climate hot = new NegevShape.Climate() {
            public double desertShare(int x, int z) { return river ? 0.4 : 1.0; }
            public double dryShare(int x, int z) { return 1.0; }
            public double riverShare(int x, int z) { return river ? 1.0 : 0.0; }
            public double weirdness(int x, int z) { return river ? 0.004 * (z - rz) + 0.02 * mid.at(x * 2.0, 0) : 0.5; }
            public double depthAtZero(int x, int z) {
                double v = 70 + 4 * base.at(x, z);
                if (river) { double d = Math.abs(weirdness(x, z)) / 0.05; if (d < 1.4) v -= 10 * Math.max(0, 1 - d / 1.4); }
                return v / 128.0;
            }
        };
        try (PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(args[4])))) {
            w.println(size);
            for (int z = oz; z < oz + size; z++) for (int x = ox; x < ox + size; x++) {
                NegevShape.Column c = shape.column(x, z, hot);
                double vanilla = Math.max(63, 128 * hot.depthAtZero(x, z) + (river ? 2 : 5) * dunes.at(x, z) + 2);
                double top = vanilla;
                if (c.dry > 0.5) top = Math.max(top, NegevShape.MIN_FLOOR);
                if (c.river > 0) top = top + (c.riverSurface - top) * c.river;
                if (c.high > 0) top = top + (c.surfExact - top) * c.high;
                if (c.wadi > 0.5) top = Math.min(top, c.wadiExact);
                int topY = (int) Math.round(top) - 1;   // top solid block
                if (c.walls != null) topY = c.surfExact - 1;
                if (c.kind.name().startsWith("WADI") && topY + 1 != c.wadiExact) c.kind = NegevShape.Kind.VANILLA;
                if (c.high > 0 && c.high <= 0.5) c.kind = NegevShape.Kind.VANILLA;
                StringBuilder sb = new StringBuilder().append(topY).append(' ');
                for (int y = topY; y > topY - 40; y--) {
                    if (c.walls != null && y >= c.floor && !c.walls.solid(y)) { sb.append('.'); continue; }
                    boolean rk = c.kind.name().startsWith("RIVER");
                    NegevShape.Material m = rk ? shape.paint(x, y, z, c, topY, c.kind, NegevShape.MIN_FLOOR) : shape.paint(x, y, z, c, topY);
                    sb.append(m == null ? (y >= topY - 3 ? 'a' : 'b') : switch (m) { case SAND -> 'a'; case SANDSTONE -> 'S'; case GRANITE -> 'G'; case PACKED_MUD -> 'P'; case LIMESTONE -> 'l'; case DIRT -> 'D'; case GRASS -> 'R'; });
                }
                w.println(sb);
            }
        }
    }
}

import net.blandfordsfox.world.NegevShape;
import java.io.*;

/** Writes column tops and painted materials (top down 40 blocks) for an isometric render. */
public class Voxels {
    public static void main(String[] args) throws Exception {
        int size = Integer.parseInt(args[0]), ox = Integer.parseInt(args[1]), oz = Integer.parseInt(args[2]);
        long seed = Long.parseLong(args[3]);
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
            for (int z = oz; z < oz + size; z++) for (int x = ox; x < ox + size; x++) {
                NegevShape.Column c = shape.column(x, z, hot);
                double vanilla = Math.max(63, 128 * hot.depthAtZero(x, z) + 5 * dunes.at(x, z) + 2);
                double top = vanilla;
                if (c.dry > 0.5) top = Math.max(top, NegevShape.MIN_FLOOR);
                if (c.high > 0) top = top + (c.surfExact - top) * c.high;
                if (c.wadi > 0.5) top = Math.min(top, c.wadiExact);
                int topY = (int) Math.round(top) - 1;   // top solid block
                if (c.kind.name().startsWith("WADI") && topY + 1 != c.wadiExact) c.kind = NegevShape.Kind.VANILLA;
                if (c.high > 0 && c.high <= 0.5) c.kind = NegevShape.Kind.VANILLA;
                StringBuilder sb = new StringBuilder().append(topY).append(' ');
                for (int y = topY; y > topY - 40; y--) {
                    NegevShape.Material m = shape.paint(x, y, z, c, topY);
                    sb.append(m == null ? (y >= topY - 3 ? 'a' : 'b') : switch (m) { case SAND -> 'a'; case SANDSTONE -> 'S'; case GRANITE -> 'G'; case PACKED_MUD -> 'P'; case DIRT -> 'D'; case GRASS -> 'R'; });
                }
                w.println(sb);
            }
        }
    }
}

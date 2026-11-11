package net.maidens.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A grown woman's body as quads, bone by bone, for a set of proportions (the model viewer's body sliders). The same
 * shaping as the viewer: her body below the neck stretched to her height; the hips wider (the tops of the legs going
 * out with them) and deeper (the legs' backs with them); a deeper chest piece tilted further so its corners still
 * meet the chest, the chest and the piece's edges on it a little wider and its bottom edge wider still; the waist
 * bent into an hourglass; the arms moved out with the chest and angled out just enough to miss the hips and waist.
 * Her head and hair keep their size. Faces made slanted are drawn in thin rows so the pixels stay straight.
 *
 * <p>Built in Blockbench units (y up from her feet, her right at +x, front at -z), stored in the game's model units
 * (pixels; x and y the other way round), each bone's quads relative to the bone's pivot.
 */
final class WomanMesh {
    /** Her proportions: height (percent), chest depth, hourglass, hip width, hip depth (pixels). */
    record Body(float height, float chest, float hourglass, float hips, float hipBack) {
        static final Body DEFAULT = new Body(100, 1, 0, 8, 0);
        /** The one saved in the model viewer, for a maiden named with a "model" name tag. */
        static final Body MODEL = new Body(130, 2.5F, 1.4F, 10.25F, 1);
    }

    /** The bones and the bone each is carried by. */
    static final String[] BONES = {"root", "hips", "right_leg", "left_leg", "waist", "torso", "chest_piece", "right_arm", "left_arm", "head"};
    static final Map<String, String> PARENT = Map.of("hips", "root", "right_leg", "hips", "left_leg", "hips", "waist", "hips",
            "torso", "waist", "chest_piece", "torso", "right_arm", "torso", "left_arm", "torso", "head", "torso");

    /** Per bone: its quads (each 4 vertices of x, y, z, u, v, then the normal: 23 floats); and the dress legs'. */
    final Map<String, float[]> quads = new HashMap<>(), dressQuads = new HashMap<>();
    /** Per bone: its pivot in model units. */
    final Map<String, float[]> pivot = new LinkedHashMap<>();
    /** The pivot of the body part the game moves (her neck), in model units. */
    final float[] bodyPivot;
    /** The corners of each leg's sole, relative to the leg's pivot, in model units (for keeping her feet on the ground). */
    final Map<String, float[][]> soles = new HashMap<>();
    final Body body;

    private static final Map<Body, WomanMesh> MADE = new HashMap<>();

    static WomanMesh of(Body body) {
        return MADE.computeIfAbsent(body, WomanMesh::new);
    }

    // ------------------------------------------------------------------ her shape (in Blockbench units)

    private final float h, hw, dx, back, wide, lip, armOut, chest, hourglass;

    private float Y(float y) {
        return y <= 27.5F ? y * h : y + 27.5F * (h - 1);
    }

    private WomanMesh(Body b) {
        body = b;
        h = b.height() / 100F;
        hw = b.hips() / 2;
        dx = hw - 4;
        back = b.hipBack();
        float extra = Math.max(0, Math.min(b.chest(), 5 * h - 0.01F) - 1);
        wide = extra * 0.25F;
        lip = extra;
        chest = b.chest();
        hourglass = b.hourglass();
        float sh = 3.5F + wide - 0.1F, g = hourglass;
        double clear = Math.max(Math.atan2(Math.max(hw, 3 + g) - sh, 10 * h), Math.atan2(3 + g / 3 - sh, 5 * h)) * 180 / Math.PI;
        armOut = (float) Math.max(0, clear - 5);

        // the bones' pivots
        Map<String, float[]> bb = new LinkedHashMap<>();
        bb.put("root", new float[]{0, 0, 0});
        bb.put("hips", new float[]{0, (Y(14.5F) + Y(17.5F)) / 2, 0});
        bb.put("right_leg", new float[]{WomanGeometry.RIGHT_LEG[0], Y(WomanGeometry.RIGHT_LEG[1]), WomanGeometry.RIGHT_LEG[2]});
        bb.put("left_leg", new float[]{WomanGeometry.LEFT_LEG[0], Y(WomanGeometry.LEFT_LEG[1]), WomanGeometry.LEFT_LEG[2]});
        bb.put("waist", new float[]{0, Y(17.5F), 0});
        bb.put("torso", new float[]{0, Y(22.5F), 0});
        bb.put("chest_piece", new float[]{0, Y(27.5F), -2});
        bb.put("right_arm", new float[]{WomanGeometry.RIGHT_ARM[0] + wide, Y(WomanGeometry.RIGHT_ARM[1]), WomanGeometry.RIGHT_ARM[2]});
        bb.put("left_arm", new float[]{WomanGeometry.LEFT_ARM[0] - wide, Y(WomanGeometry.LEFT_ARM[1]), WomanGeometry.LEFT_ARM[2]});
        bb.put("head", new float[]{WomanGeometry.HEAD[0], Y(WomanGeometry.HEAD[1]), WomanGeometry.HEAD[2]});
        for (var e : bb.entrySet()) pivot.put(e.getKey(), game(e.getValue()));
        bodyPivot = game(new float[]{0, Y(27.5F), 0});

        Map<String, List<float[]>> q = new HashMap<>(), dq = new HashMap<>();
        for (WomanGeometry.E e : WomanGeometry.BODY) {
            Shaped s = shape(e, false);
            String bone = boneOf(e.name());
            build(s, bb.get(bone), q.computeIfAbsent(bone, k -> new ArrayList<>()));
            if (e.name().endsWith("_leg")) soles.put(bone, soleCorners(s, bb.get(bone)));
        }
        for (WomanGeometry.E e : WomanGeometry.DRESS_LEGS) {
            Shaped s = shape(e, true);
            build(s, bb.get(e.name()), dq.computeIfAbsent(e.name(), k -> new ArrayList<>()));
        }
        q.forEach((k, v) -> quads.put(k, flat(v)));
        dq.forEach((k, v) -> dressQuads.put(k, flat(v)));
    }

    static String boneOf(String element) {
        return switch (element) {
            case "head", "neck", "head_layer" -> "head";
            case "chest" -> "torso";
            case "breast" -> "chest_piece";
            case "waist" -> "waist";
            case "hips" -> "hips";
            default -> element;          // the arms and legs
        };
    }

    /** A cube after shaping. Shape: 0 a box, 1 grow (legs), 2 lip (chest piece), 3 curve (waist). */
    private record Shaped(float[] from, float[] to, float[] origin, float[] rot, float inflate, float[][] uv,
                          int kind, float growSide, float grow, float back, float lip, float[] curve) {}

    private Shaped shape(WomanGeometry.E e, boolean dressLeg) {
        float[] from = e.from().clone(), to = e.to().clone(), origin = e.origin().clone(), rot = e.rotation().clone();
        String bone = boneOf(e.name());
        boolean head = bone.equals("head");
        for (float[] v : new float[][]{from, to, origin}) v[1] = head ? v[1] + 27.5F * (h - 1) : Y(v[1]);
        int kind = 0;
        float growSide = 1, grow = 0, bk = 0, lp = 0;
        float[] curve = null;
        switch (e.name()) {
            case "hips" -> { from[0] = -hw; to[0] = hw; to[2] += back; }
            case "chest" -> { from[0] -= wide; to[0] += wide; }
            case "waist" -> {
                if (hourglass != 0) { kind = 3; curve = new float[]{hourglass / 3, hourglass, hourglass * 0.2F, hourglass / 2}; }
            }
            case "breast" -> {
                float top = 27.5F * h, H = 5 * h, d = Math.min(chest, H - 0.01F), L = (float) Math.sqrt(H * H - d * d);
                origin = new float[]{0, top, -2};
                from = new float[]{e.from()[0] - wide, top - L, -2};
                to = new float[]{e.to()[0] + wide, top, -2 + d};
                rot = new float[]{(float) Math.toDegrees(Math.asin(d / H)), 0, 0};
                lp = lip - wide;
                if (lp > 0) kind = 2;
            }
            default -> { }
        }
        if (bone.endsWith("_leg")) {
            growSide = bone.equals("right_leg") ? 1 : -1;
            grow = growSide * dx;
            bk = back;
            if (grow != 0 || bk != 0) kind = 1;
        }
        if (bone.endsWith("_arm")) {
            float sg = bone.equals("right_arm") ? 1 : -1;
            for (float[] v : new float[][]{from, to, origin}) v[0] += sg * wide;
            rot[2] += sg * armOut;
        }
        return new Shaped(from, to, origin, rot, dressLeg ? 0.06F : e.inflate(), e.uv(), kind, growSide, grow, bk, lp, curve);
    }

    // ------------------------------------------------------------------ cubes to quads

    private static final String[] SIDES = {"north", "east", "south", "west", "up", "down"};

    private static float[][] corners(float[] f, float[] t, int side) {
        float fx = f[0], fy = f[1], fz = f[2], tx = t[0], ty = t[1], tz = t[2];
        return switch (side) {
            case 0 -> new float[][]{{tx, ty, fz}, {fx, ty, fz}, {tx, fy, fz}, {fx, fy, fz}};
            case 2 -> new float[][]{{fx, ty, tz}, {tx, ty, tz}, {fx, fy, tz}, {tx, fy, tz}};
            case 1 -> new float[][]{{tx, ty, tz}, {tx, ty, fz}, {tx, fy, tz}, {tx, fy, fz}};
            case 3 -> new float[][]{{fx, ty, fz}, {fx, ty, tz}, {fx, fy, fz}, {fx, fy, tz}};
            case 4 -> new float[][]{{fx, ty, fz}, {tx, ty, fz}, {fx, ty, tz}, {tx, ty, tz}};
            default -> new float[][]{{fx, fy, tz}, {tx, fy, tz}, {fx, fy, fz}, {tx, fy, fz}};
        };
    }

    private static final float[][] OUT = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private void build(Shaped s, float[] bonePivot, List<float[]> out) {
        float inf = s.inflate();
        float[] f = new float[3], t = new float[3];
        for (int i = 0; i < 3; i++) {
            f[i] = s.from()[i] - inf - s.origin()[i];
            t[i] = s.to()[i] + inf - s.origin()[i];
        }
        boolean flat = s.from()[2] == s.to()[2];
        float mid = (f[1] + t[1]) / 2;
        float outer = s.growSide() > 0 ? t[0] : f[0];
        for (int side = 0; side < 6; side++) {
            float[] u = s.uv()[side];
            if (u == null || u[0] == u[2] || u[1] == u[3]) continue;
            if (flat && side == 2) continue;
            boolean still = switch (s.kind()) {
                case 1, 3 -> side == 4 || side == 5;
                case 2 -> side == 4 || side == 2;
                default -> true;
            };
            int rows = still ? 1 : Math.max(4, 2 * Math.round(Math.abs(u[3] - u[1])));
            float[][] c = corners(f, t, side);
            for (int r = 0; r < rows; r++) {
                float a = (float) r / rows, z = (float) (r + 1) / rows;
                float[][] p = {lerp(c[0], c[2], a), lerp(c[1], c[3], a), lerp(c[0], c[2], z), lerp(c[1], c[3], z)};
                float va = u[1] + (u[3] - u[1]) * a, vz = u[1] + (u[3] - u[1]) * z;
                float[][] uv = {{u[0], va}, {u[2], va}, {u[0], vz}, {u[2], vz}};
                float[] quad = new float[23];
                int[] order = {0, 1, 3, 2};               // round the quad
                float[][] g = new float[4][];
                for (int k = 0; k < 4; k++) {
                    float[] w = warp(s, p[order[k]], f, t, mid, outer);
                    g[k] = place(s, w, bonePivot);
                    quad[k * 5] = g[k][0];
                    quad[k * 5 + 1] = g[k][1];
                    quad[k * 5 + 2] = g[k][2];
                    quad[k * 5 + 3] = uv[order[k]][0] / 64F;
                    quad[k * 5 + 4] = uv[order[k]][1] / 64F;
                }
                float[] n = normal(g, game(rotate(s.rot(), OUT[side])));
                quad[20] = n[0];
                quad[21] = n[1];
                quad[22] = n[2];
                out.add(quad);
            }
        }
    }

    private static float[] lerp(float[] a, float[] b, float k) {
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    private static float[] warp(Shaped s, float[] p, float[] f, float[] t, float mid, float outer) {
        float x = p[0], y = p[1], z = p[2];
        switch (s.kind()) {
            case 1 -> {
                float k = (y - f[1]) / (t[1] - f[1]);
                if (x == outer) x += s.grow() * k;
                if (z == t[2]) z += s.back() * k;
            }
            case 3 -> {
                float[] cv = s.curve();
                if (x == f[0] || x == t[0]) {
                    float k = y >= mid ? -cv[2] + (cv[0] + cv[2]) * (y - mid) / (t[1] - mid) : -cv[2] + (cv[1] + cv[2]) * (mid - y) / (mid - f[1]);
                    x += (x == t[0] ? 1 : -1) * k;
                }
                if (cv[3] != 0 && z == t[2] && y < mid) z += cv[3] * (mid - y) / (mid - f[1]);
            }
            case 2 -> {
                if ((x == f[0] || x == t[0]) && t[2] > f[2]) {
                    float k = (1 - (y - f[1]) / (t[1] - f[1])) * (1 - (z - f[2]) / (t[2] - f[2]));
                    x += (x == t[0] ? 1 : -1) * s.lip() * k;
                }
            }
            default -> { }
        }
        return new float[]{x, y, z};
    }

    /** A point of a cube (relative to its own pivot) rotated, then relative to its bone's pivot, in model units. */
    private static float[] place(Shaped s, float[] p, float[] bonePivot) {
        float[] r = rotate(s.rot(), p);
        return game(new float[]{r[0] + s.origin()[0] - bonePivot[0], r[1] + s.origin()[1] - bonePivot[1], r[2] + s.origin()[2] - bonePivot[2]});
    }

    /** Rotated by Blockbench's degrees: about x first, then y, then z. */
    private static float[] rotate(float[] deg, float[] p) {
        double ax = Math.toRadians(deg[0]), ay = Math.toRadians(deg[1]), az = Math.toRadians(deg[2]);
        double x = p[0], y = p[1], z = p[2];
        double y1 = y * Math.cos(ax) - z * Math.sin(ax), z1 = y * Math.sin(ax) + z * Math.cos(ax);
        double x2 = x * Math.cos(ay) + z1 * Math.sin(ay), z2 = -x * Math.sin(ay) + z1 * Math.cos(ay);
        double x3 = x2 * Math.cos(az) - y1 * Math.sin(az), y3 = x2 * Math.sin(az) + y1 * Math.cos(az);
        return new float[]{(float) x3, (float) y3, (float) z2};
    }

    /** Blockbench units to the game's (x and y the other way round). */
    static float[] game(float[] v) {
        return new float[]{-v[0], -v[1], v[2]};
    }

    private static float[] normal(float[][] g, float[] expect) {
        float ax = g[1][0] - g[0][0], ay = g[1][1] - g[0][1], az = g[1][2] - g[0][2];
        float bx = g[3][0] - g[0][0], by = g[3][1] - g[0][1], bz = g[3][2] - g[0][2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6F) return expect;
        nx /= len; ny /= len; nz /= len;
        if (nx * expect[0] + ny * expect[1] + nz * expect[2] < 0) { nx = -nx; ny = -ny; nz = -nz; }
        return new float[]{nx, ny, nz};
    }

    private float[][] soleCorners(Shaped s, float[] bonePivot) {
        float inf = s.inflate();
        float[] f = new float[3], t = new float[3];
        for (int i = 0; i < 3; i++) {
            f[i] = s.from()[i] - inf - s.origin()[i];
            t[i] = s.to()[i] + inf - s.origin()[i];
        }
        float[][] c = corners(f, t, 5);
        float[][] out = new float[4][];
        for (int k = 0; k < 4; k++) out[k] = place(s, c[k], bonePivot);
        return out;
    }

    private static float[] flat(List<float[]> list) {
        float[] out = new float[list.size() * 23];
        for (int i = 0; i < list.size(); i++) System.arraycopy(list.get(i), 0, out, i * 23, 23);
        return out;
    }
}

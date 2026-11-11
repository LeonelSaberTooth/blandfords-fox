package net.maidens.client;

import java.util.HashMap;
import java.util.Map;

/**
 * The runway walk (made in the model viewer), as the pose of each bone at a point of a stride, in the viewer's
 * degrees (x, y, z; turned about x first, then y, then z).
 *
 * <p>One stride is a step with each foot. Each leg is planted for 60% of it, sweeping back at an even pace, then
 * swings forward easing in and out; the legs turn out just enough that the feet pass without touching, and back
 * against the hips' roll so they hang straight. Without knees, the swinging leg is drawn up into the hips a little so
 * its foot clears the ground. The hips roll up over the standing leg and turn with the leg in front; the waist and
 * chest turn back against them, so the shoulders stay nearly level and the head looks straight ahead. The arms swing
 * against the legs. The chest piece lifts a little just after her highest point and settles just after her lowest.
 */
final class Runway {
    private Runway() {}

    private static final float A = 21, STANCE = 0.6F, ROLL = 5, YAW = 7;

    /** The pose at t (0..1 of a stride), every angle and lift scaled by k (0 standing .. 1 walking). "_lift": the legs'. */
    static Map<String, float[]> pose(float t, WomanMesh.Body b, float k) {
        double TAU = Math.PI * 2;
        float h = b.height() / 100F;
        float L = 15 * h;
        double lean = Math.toRadians(4), innerFoot = 2 - Math.cos(lean) - L * Math.sin(lean);
        float out = (float) Math.max(0, Math.toDegrees(Math.atan2(0.2 - innerFoot, L)));
        float roll = (float) (ROLL * Math.cos(TAU * (t - 0.3)));        // right hip up while the right leg stands
        float yaw = (float) (YAW * Math.cos(TAU * t));                    // right hip forward with the right leg
        float arm = (float) (13 * Math.cos(TAU * (t - 0.04)));
        float bounce = 1.2F + Math.max(0, b.chest() - 1);
        Map<String, float[]> p = new HashMap<>();
        p.put("right_leg", s(k, leg(t), 0, out - roll));
        p.put("left_leg", s(k, leg(t + 0.5F), 0, -out - roll));
        p.put("hips", s(k, 0, yaw, roll));
        p.put("waist", s(k, 0, -0.6F * yaw, -0.5F * roll));
        p.put("torso", s(k, 1.5F, -0.9F * yaw, -0.7F * roll));
        p.put("head", s(k, -1, 0.5F * yaw, 0.2F * roll));
        p.put("right_arm", s(k, -arm - 2, 0, 1 + 0.5F * Math.max(0, -arm) / 13));
        p.put("left_arm", s(k, arm - 2, 0, -1 - 0.5F * Math.max(0, arm) / 13));
        p.put("chest_piece", s(k, (float) (bounce * Math.cos(2 * TAU * (t - 0.3 - 0.07))), 0, 0));
        p.put("_lift", new float[]{k * lift(t), k * lift(t + 0.5F)});
        return p;
    }

    /** Standing still. */
    static Map<String, float[]> still() {
        Map<String, float[]> p = new HashMap<>();
        for (String b : WomanMesh.BONES) p.put(b, new float[3]);
        return p;
    }

    private static float phase(float u) {
        return u - (float) Math.floor(u);
    }

    private static float leg(float u) {
        u = phase(u);
        if (u < STANCE) return A - 2 * A * (u / STANCE);
        float k = (u - STANCE) / (1 - STANCE);
        return (float) (-A + 2 * A * (1 - Math.cos(Math.PI * k)) / 2);
    }

    private static float lift(float u) {
        u = phase(u);
        return u < STANCE ? 0 : (float) (0.8 * Math.sin(Math.PI * (u - STANCE) / (1 - STANCE)));
    }

    private static float[] s(float k, float x, float y, float z) {
        return new float[]{x * k, y * k, z * k};
    }
}

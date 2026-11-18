package net.blandfordsfox.client.fareast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Far East compatibility: the tiger model and the animations made for it, read from
 * assets/blandfordsfox/fe_tiger (written by tools/gen_fe_tiger.py from the Tiger Animations page).
 */
public final class FeTigerData {
    private FeTigerData() {}

    /** A texture shown between two times of an animation (in the animation's own seconds). */
    public record Window(float from, float to, String face) {}

    private static JsonObject read(String name) {
        try (InputStream in = FeTigerData.class.getResourceAsStream("/assets/blandfordsfox/fe_tiger/" + name)) {
            if (in == null) throw new IllegalStateException("missing fe_tiger/" + name);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    public static LayerDefinition layer() {
        JsonObject m = read("model.json");
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        for (JsonElement e : m.getAsJsonArray("parts")) {
            JsonObject p = e.getAsJsonObject();
            String name = p.get("name").getAsString();
            PartDefinition parent = p.get("parent").isJsonNull() ? mesh.getRoot() : parts.get(p.get("parent").getAsString());
            CubeListBuilder cubes = CubeListBuilder.create();
            for (JsonElement ce : p.getAsJsonArray("cubes")) {
                JsonObject c = ce.getAsJsonObject();
                float[] tex = floats(c.getAsJsonArray("tex")), from = floats(c.getAsJsonArray("from")),
                        size = floats(c.getAsJsonArray("size")), d = floats(c.getAsJsonArray("deform"));
                cubes.texOffs((int) tex[0], (int) tex[1]).mirror(c.get("mirror").getAsBoolean())
                        .addBox(from[0], from[1], from[2], size[0], size[1], size[2], new CubeDeformation(d[0], d[1], d[2]));
            }
            float[] off = floats(p.getAsJsonArray("offset")), rot = floats(p.getAsJsonArray("rotation"));
            parts.put(name, parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(off[0], off[1], off[2], rot[0], rot[1], rot[2])));
        }
        JsonArray res = m.getAsJsonArray("texture");
        return LayerDefinition.create(mesh, res.get(0).getAsInt(), res.get(1).getAsInt());
    }

    public static final Map<String, AnimationDefinition> ANIMS = new HashMap<>();
    public static final Map<String, List<Window>> FACES = new HashMap<>();

    public static void load() {
        if (!ANIMS.isEmpty()) return;
        JsonObject root = read("anims.json");
        for (Map.Entry<String, JsonElement> en : root.getAsJsonObject("anims").entrySet()) {
            JsonObject a = en.getValue().getAsJsonObject();
            AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(a.get("length").getAsFloat());
            if (a.get("loop").getAsBoolean()) b.looping();
            for (JsonElement ce : a.getAsJsonArray("channels")) {
                JsonObject c = ce.getAsJsonObject();
                String target = c.get("target").getAsString();
                AnimationChannel.Target t = switch (target) {
                    case "rotation" -> AnimationChannel.Targets.ROTATION;
                    case "position" -> AnimationChannel.Targets.POSITION;
                    default -> AnimationChannel.Targets.SCALE;
                };
                JsonArray ks = c.getAsJsonArray("keys");
                Keyframe[] frames = new Keyframe[ks.size()];
                for (int i = 0; i < frames.length; i++) {
                    float[] k = floats(ks.get(i).getAsJsonArray());
                    var v = switch (target) {
                        case "rotation" -> KeyframeAnimations.degreeVec(k[1], k[2], k[3]);
                        case "position" -> KeyframeAnimations.posVec(k[1], k[2], k[3]);
                        default -> KeyframeAnimations.scaleVec(k[1], k[2], k[3]);
                    };
                    frames[i] = new Keyframe(k[0], v, k[4] > 0.5F ? AnimationChannel.Interpolations.CATMULLROM : AnimationChannel.Interpolations.LINEAR);
                }
                b.addAnimation(c.get("bone").getAsString(), new AnimationChannel(t, frames));
            }
            ANIMS.put(en.getKey(), b.build());
        }
        for (Map.Entry<String, JsonElement> en : root.getAsJsonObject("faces").entrySet()) {
            List<Window> list = new ArrayList<>();
            JsonObject f = en.getValue().getAsJsonObject();
            if (f.has("windows"))
                for (JsonElement w : f.getAsJsonArray("windows")) {
                    JsonArray a = w.getAsJsonArray();
                    list.add(new Window(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsString()));
                }
            FACES.put(en.getKey(), list);
        }
    }

    public static AnimationDefinition anim(String name) {
        load();
        return ANIMS.get(name);
    }

    /** The face texture an animation asks for at a time, or null for the usual one. */
    public static String face(String anim, float seconds) {
        load();
        for (Window w : FACES.getOrDefault(anim, List.of())) if (seconds >= w.from() && seconds < w.to()) return w.face();
        return null;
    }
}

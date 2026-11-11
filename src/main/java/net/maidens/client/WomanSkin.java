package net.maidens.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.maidens.MaidensMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * A grown woman's look, put together as the model viewer does it (layers made by the viewer's export_mod.py, in
 * textures/entity/maiden/woman): her skin with the shadow her hair casts on her face, blush (for a moment after she's
 * grown fonder of someone), freckles (some women have them), her brows, her hair, her eyes (closed in a smile when
 * she introduces herself), then what she wears: swimwear at home, a dress outside, at work the dress with her trade's
 * things over it (only the trade's own pixels of the vanilla profession texture, no robe). Under a dress she wears
 * the straight outdoor legs, with their own texture. Each combination is drawn once and kept.
 */
public final class WomanSkin {
    public static final int TONES = 5, HAIRS = 21, EYES = 8, OUTFITS = 21;

    public enum Wear { SWIM, DRESS, WORK }

    private static final Map<String, ResourceLocation> MADE = new HashMap<>();
    private static final Map<String, NativeImage> LAYERS = new HashMap<>();
    /** Things worn over the eyes (glasses, eyepatch, monocle) sit a pixel higher, on her eyes: from this row down. */
    private static final Map<String, Integer> EYE_ROWS = Map.of("librarian", 13, "cartographer", 13, "weaponsmith", 11);

    private WomanSkin() {}

    public static ResourceLocation body(int tone, int hair, int eyes, int outfit, Wear wear, String job, boolean freckles, boolean blush, boolean smile) {
        tone = Math.floorMod(tone, TONES);
        hair = Math.floorMod(hair, HAIRS);
        eyes = Math.floorMod(eyes, EYES);
        outfit = Math.floorMod(outfit, OUTFITS);
        if (wear != Wear.WORK || job.isEmpty()) job = "";
        if (wear == Wear.WORK && job.isEmpty()) wear = Wear.DRESS;
        String key = "body_" + tone + "_" + hair + "_" + eyes + "_" + (wear == Wear.SWIM ? "swim" : outfit) + "_" + job
                + (freckles ? "_f" : "") + (blush ? "_b" : "") + (smile ? "_s" : "");
        int t = tone, h = hair, e = eyes, o = outfit;
        Wear w = wear;
        String j = job;
        return MADE.computeIfAbsent(key, k -> register(k, makeBody(t, h, e, o, w, j, freckles, blush, smile)));
    }

    /** The outdoor legs under a dress: her skin, the dress (and at work her trade's things). */
    public static ResourceLocation legs(int tone, int outfit, String job) {
        tone = Math.floorMod(tone, TONES);
        outfit = Math.floorMod(outfit, OUTFITS);
        String key = "legs_" + tone + "_" + outfit + "_" + job;
        int t = tone, o = outfit;
        return MADE.computeIfAbsent(key, k -> {
            NativeImage out = copy(layer("legs_tone_" + (t + 1)));
            put(out, layer("legs_dress_" + (o + 1)));
            if (!job.isEmpty()) trade(out, job, "job_map_legs");
            return register(k, out);
        });
    }

    /** Her job's hat: the profession texture with only its own pixels (glasses and the like a pixel higher). */
    public static ResourceLocation hat(String job) {
        return MADE.computeIfAbsent("hat_" + job, k -> register(k, profession(job)));
    }

    private static NativeImage makeBody(int tone, int hair, int eyes, int outfit, Wear wear, String job, boolean freckles, boolean blush, boolean smile) {
        NativeImage out = copy(layer("tone_" + (tone + 1)));
        NativeImage hairImg = layer("hair_" + (hair + 1));
        hairShadow(out, hairImg, tone == 0 ? 0.5F : tone == 1 ? 0.7F : 1.0F);
        if (blush) put(out, layer("blush"));
        if (freckles) put(out, layer("freckles"));
        put(out, layer("brows_" + (eyes + 1)));
        put(out, hairImg);
        put(out, layer(smile ? "smile" : "eyes_" + (eyes + 1)));
        switch (wear) {
            case SWIM -> put(out, layer("swim"));
            case DRESS -> put(out, layer("dress_" + (outfit + 1)));
            case WORK -> {
                put(out, layer("dress_" + (outfit + 1)));
                trade(out, job, "job_map_body");
            }
        }
        fillUnderChestPiece(out);
        return out;
    }

    // ------------------------------------------------------------------ the parts

    private static final int FACE_X = 7, FACE_Y = 7, HAIR_FRONT_X = 41;
    private static final float[] SHADE = {0.94F, 0.87F, 0.82F};

    /**
     * Where her hair covers her forehead it shades the skin below it (and behind the hair cube's front), slightly
     * darker and warmer, in the middle 6 columns of her face; lone strands of the hair cube don't.
     */
    private static void hairShadow(NativeImage skin, NativeImage hair, float strength) {
        if (hair == null) return;
        float[] mul = new float[3];
        for (int k = 0; k < 3; k++) mul[k] = 1 - (1 - SHADE[k]) * strength;
        boolean[][] onHead = new boolean[9][8], cube = new boolean[9][8];
        for (int j = 0; j < 8; j++)
            for (int i = 0; i < 8; i++) {
                onHead[j][i] = alpha(hair.getPixelRGBA(FACE_X + i, FACE_Y + j)) > 0;
                cube[j][i] = alpha(hair.getPixelRGBA(HAIR_FRONT_X + i, FACE_Y + j)) > 0;
            }
        for (int j = 0; j < 8; j++)
            for (int i = 1; i < 7; i++) {
                if (onHead[j][i]) continue;
                boolean below = j > 0 && (onHead[j - 1][i] || mass(cube, i, j - 1)), behind = mass(cube, i, j);
                if (!below && !behind) continue;
                int x = FACE_X + i, y = FACE_Y + j, c = skin.getPixelRGBA(x, y);
                int r = Math.round((c & 255) * mul[0]), g = Math.round((c >> 8 & 255) * mul[1]), b = Math.round((c >> 16 & 255) * mul[2]);
                skin.setPixelRGBA(x, y, (c & 0xFF000000) | b << 16 | g << 8 | r);
            }
    }

    private static boolean mass(boolean[][] cube, int i, int j) {
        return cube[j][i] && (i > 0 && cube[j][i - 1] || i < 7 && cube[j][i + 1]);
    }

    /**
     * The chest's front, hidden behind the chest piece, shows as a strip under the piece when it swings up while she
     * walks: each column of it takes the colour of the piece's underside in that column.
     */
    private static void fillUnderChestPiece(NativeImage img) {
        // the chest's front: 7 x 5 at (4, 19), drawn from +x to -x; the piece's underside: its front row 45, -x to +x
        for (int i = 0; i < 7; i++) {
            float s = (i + 0.5F) / 7;
            int nu = (int) Math.floor(11 - 7 * s), du = (int) Math.floor(51 - 7 * s);
            int c = img.getPixelRGBA(du, 45);
            if (alpha(c) == 0) continue;
            for (int y = 19; y < 24; y++) img.setPixelRGBA(nu, y, c);
        }
    }

    /** Her trade's things, moved onto her by a map (red = x, green = y in the profession texture). */
    private static void trade(NativeImage out, String job, String mapName) {
        NativeImage map = layer(mapName);
        NativeImage prof = profession(job);
        if (map == null) {
            prof.close();
            return;
        }
        NativeImage t = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                int m = map.getPixelRGBA(x, y);
                if (alpha(m) == 0) continue;
                int u = m & 255, v = m >> 8 & 255;
                if (u < prof.getWidth() && v < prof.getHeight()) t.setPixelRGBA(x, y, prof.getPixelRGBA(u, v));
            }
        LayeredSkin.over(out, t);
        t.close();
        prof.close();
    }

    private static NativeImage profession(String job) {
        NativeImage src = LayeredSkin.vanilla("villager/profession/" + job);
        NativeImage out = new NativeImage(64, 64, true);
        if (src == null) return out;
        for (int y = 0; y < Math.min(64, src.getHeight()); y++)
            for (int x = 0; x < Math.min(64, src.getWidth()); x++) out.setPixelRGBA(x, y, src.getPixelRGBA(x, y));
        Integer from = EYE_ROWS.get(job);
        if (from != null)
            for (int y = from; y < 18; y++)
                for (int x = 0; x < 64; x++) {
                    int c = out.getPixelRGBA(x, y);
                    if (alpha(c) == 0) continue;
                    out.setPixelRGBA(x, y - 1, c);
                    out.setPixelRGBA(x, y, 0);
                }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static void put(NativeImage out, NativeImage layer) {
        if (layer != null) LayeredSkin.over(out, layer);
    }

    private static int alpha(int c) {
        return c >>> 24;
    }

    private static NativeImage copy(NativeImage src) {
        NativeImage out = new NativeImage(64, 64, true);
        if (src != null) out.copyFrom(src);
        return out;
    }

    private static ResourceLocation register(String key, NativeImage img) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "dynamic/woman_" + key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
        return id;
    }

    private static NativeImage layer(String name) {
        return LAYERS.computeIfAbsent(name, n -> {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "textures/entity/maiden/woman/" + n + ".png");
            try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(loc).orElseThrow().open()) {
                return NativeImage.read(in);
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("Missing woman layer {}", loc);
                return null;
            }
        });
    }
}

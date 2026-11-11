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
 * A maiden's look put together from layers (made by tools/make_layers.py): her skin tone, then her clothes, her eyes
 * and her hair, each chosen separately. Each combination is drawn once into a texture and kept; there are a few
 * hundred at most, and only the ones in sight are ever made.
 */
public final class LayeredSkin {
    /** Which body (the texture spots differ) and which clothes. */
    public enum Body { USUAL, CASUAL, CHILD }

    private static final Map<String, ResourceLocation> MADE = new HashMap<>();
    private static final Map<String, NativeImage> LAYERS = new HashMap<>();

    private LayeredSkin() {}

    public static ResourceLocation get(Body body, int tone, int outfit, int eyes, int hair) {
        return get(body, tone, outfit, eyes, hair, "", "plains");
    }

    /**
     * With work clothes: job is a villager profession ("farmer"...; "" for her own clothes) and type the villager
     * type of where she is ("plains", "desert"...). Her work clothes are that villager's robe and trade clothes.
     */
    public static ResourceLocation get(Body body, int tone, int outfit, int eyes, int hair, String job, String type) {
        if (body != Body.USUAL) job = "";
        // men and children have the first banks (5 outfits, 5 eyes, 6 hairs); grown women have their own (WomanSkin)
        outfit = Math.floorMod(outfit, 5);
        eyes = Math.floorMod(eyes, 5);
        hair = Math.floorMod(hair, 6);
        String key = body.ordinal() + "_" + tone + "_" + outfit + "_" + eyes + "_" + hair + (job.isEmpty() ? "" : "_" + job + "_" + type);
        String j = job;
        int o = outfit, e = eyes, h = hair;
        return MADE.computeIfAbsent(key, k -> make(k, body, tone, o, e, h, j, type));
    }

    private static ResourceLocation make(String key, Body body, int tone, int outfit, int eyes, int hair, String job, String type) {
        NativeImage out = new NativeImage(64, 64, true);
        String base = body == Body.CASUAL ? "casual_tone_" + (tone + 1) : "tone_" + (tone + 1);
        String clothes = switch (body) {
            case USUAL -> "clothes_" + (outfit + 1);
            case CASUAL -> "casual_clothes";
            case CHILD -> "child_clothes_" + (outfit + 1);
        };
        NativeImage work = job.isEmpty() ? null : workClothes(job, type);
        for (String layer : new String[]{base, clothes, "eyes_" + (eyes + 1), "hair_" + (hair + 1)}) {
            if (layer.equals(clothes) && work != null) {
                over(out, work);
                continue;
            }
            NativeImage img = layer(layer);
            if (img != null) over(out, img);
        }
        if (work != null) work.close();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "dynamic/maiden_" + key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
        return id;
    }

    /** A vanilla villager's clothes (its body, its type's robe, its trade's clothes) moved onto her body by job_map. */
    private static NativeImage workClothes(String job, String type) {
        NativeImage map = layer("job_map");
        if (map == null) return null;
        NativeImage villager = new NativeImage(64, 64, true);
        for (String path : new String[]{"villager/villager", "villager/type/" + type, "villager/profession/" + job}) {
            NativeImage img = vanilla(path);
            if (img != null) over(villager, img);
        }
        NativeImage out = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                int m = map.getPixelRGBA(x, y);
                if (m >>> 24 == 0) continue;
                int u = m & 255, v = m >> 8 & 255;
                if (u < villager.getWidth() && v < villager.getHeight()) out.setPixelRGBA(x, y, villager.getPixelRGBA(u, v));
            }
        villager.close();
        return out;
    }

    static NativeImage vanilla(String path) {
        return LAYERS.computeIfAbsent("vanilla:" + path, n -> {
            ResourceLocation loc = ResourceLocation.withDefaultNamespace("textures/entity/" + path + ".png");
            try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(loc).orElseThrow().open()) {
                return NativeImage.read(in);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private static NativeImage layer(String name) {
        return LAYERS.computeIfAbsent(name, n -> {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "textures/entity/maiden/layers/" + n + ".png");
            try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(loc).orElseThrow().open()) {
                return NativeImage.read(in);
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("Missing maiden layer {}", loc);
                return null;
            }
        });
    }

    /** Draws src over dst (both ABGR), blending by src's alpha. */
    static void over(NativeImage dst, NativeImage src) {
        int w = Math.min(dst.getWidth(), src.getWidth()), h = Math.min(dst.getHeight(), src.getHeight());
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int s = src.getPixelRGBA(x, y);
                int a = s >>> 24;
                if (a == 0) continue;
                if (a == 255) {
                    dst.setPixelRGBA(x, y, s);
                    continue;
                }
                int d = dst.getPixelRGBA(x, y);
                int da = d >>> 24;
                int oa = a + da * (255 - a) / 255;
                int r = mix(s & 255, d & 255, a), g = mix(s >> 8 & 255, d >> 8 & 255, a), b = mix(s >> 16 & 255, d >> 16 & 255, a);
                dst.setPixelRGBA(x, y, oa << 24 | b << 16 | g << 8 | r);
            }
    }

    private static int mix(int s, int d, int a) {
        return (s * a + d * (255 - a)) / 255;
    }
}

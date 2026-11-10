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

    private static final Map<Long, ResourceLocation> MADE = new HashMap<>();
    private static final Map<String, NativeImage> LAYERS = new HashMap<>();

    private LayeredSkin() {}

    public static ResourceLocation get(Body body, int tone, int outfit, int eyes, int hair) {
        long key = (((body.ordinal() * 16L + tone) * 16 + outfit) * 16 + eyes) * 16 + hair;
        return MADE.computeIfAbsent(key, k -> make(k, body, tone, outfit, eyes, hair));
    }

    private static ResourceLocation make(long key, Body body, int tone, int outfit, int eyes, int hair) {
        NativeImage out = new NativeImage(64, 64, true);
        String base = body == Body.CASUAL ? "casual_tone_" + (tone + 1) : "tone_" + (tone + 1);
        String clothes = switch (body) {
            case USUAL -> "clothes_" + (outfit + 1);
            case CASUAL -> "casual_clothes_" + (outfit + 1);
            case CHILD -> "child_clothes_" + (outfit + 1);
        };
        for (String layer : new String[]{base, clothes, "eyes_" + (eyes + 1), "hair_" + (hair + 1)}) {
            NativeImage img = layer(layer);
            if (img != null) over(out, img);
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "dynamic/maiden_" + Long.toHexString(key));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
        return id;
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
    private static void over(NativeImage dst, NativeImage src) {
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

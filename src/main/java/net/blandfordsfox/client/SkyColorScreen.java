package net.blandfordsfox.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

/**
 * The /middleeast skycolor panel: a small box in the top-left corner (the sky stays in view),
 * with a Warmth slider that walks vanilla's own temperature-to-sky line, Hue and Saturation
 * sliders to step off it (kept inside vanilla's bounds), the colour as a swatch, hex, RGB and
 * the decimal number a biome file uses, and Copy / Reset / Done.
 */
public class SkyColorScreen extends Screen {
    private static final int W = 220, X = 6, Y = 6;

    private final Biome biome;
    private final String biomeName;
    private float hue, sat;
    private Float warmth;   // null when hue/saturation are off vanilla's line
    private Slider warmthSlider, hueSlider, satSlider;
    private String copied = "";

    public SkyColorScreen(Biome biome, String biomeName) {
        super(Component.literal("Sky colour"));
        this.biome = biome;
        this.biomeName = biomeName;
        float[] hsv = hsv(SkyColor.get(biome));
        hue = Mth.clamp(hsv[0], SkyColor.HUE_MIN, SkyColor.HUE_MAX);
        sat = Mth.clamp(hsv[1], SkyColor.SAT_MIN, SkyColor.SAT_MAX);
        warmth = warmthFor(hue, sat);
    }

    @Override
    protected void init() {
        int x = X + 6, w = W - 12, y = Y + 26;
        float t0 = warmth != null ? warmth : (SkyColor.TEMP_MIN + SkyColor.TEMP_MAX) / 2;
        warmthSlider = addRenderableWidget(new Slider(x, y, w, unit(t0, SkyColor.TEMP_MIN, SkyColor.TEMP_MAX), v -> {
            warmth = lerp(v, SkyColor.TEMP_MIN, SkyColor.TEMP_MAX);
            hue = SkyColor.hueFor(warmth);
            sat = SkyColor.satFor(warmth);
            hueSlider.set(unit(hue, SkyColor.HUE_MIN, SkyColor.HUE_MAX));
            satSlider.set(unit(sat, SkyColor.SAT_MIN, SkyColor.SAT_MAX));
            apply();
        }, v -> warmth == null ? "Warmth: (custom)" : String.format("Warmth (vanilla temperature): %.2f", lerp(v, SkyColor.TEMP_MIN, SkyColor.TEMP_MAX))));
        hueSlider = addRenderableWidget(new Slider(x, y + 24, w, unit(hue, SkyColor.HUE_MIN, SkyColor.HUE_MAX), v -> {
            hue = lerp(v, SkyColor.HUE_MIN, SkyColor.HUE_MAX);
            offLine();
        }, v -> String.format("Hue: %.1f°", lerp(v, SkyColor.HUE_MIN, SkyColor.HUE_MAX) * 360)));
        satSlider = addRenderableWidget(new Slider(x, y + 48, w, unit(sat, SkyColor.SAT_MIN, SkyColor.SAT_MAX), v -> {
            sat = lerp(v, SkyColor.SAT_MIN, SkyColor.SAT_MAX);
            offLine();
        }, v -> String.format("Saturation: %.0f%%", lerp(v, SkyColor.SAT_MIN, SkyColor.SAT_MAX) * 100)));
        int by = y + 108, bw = (w - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Copy"), b -> {
            int c = SkyColor.get(biome);
            minecraft.keyboardHandler.setClipboard(String.format("\"sky_color\": %d  (#%06X)", c, c));
            copied = "Copied";
        }).bounds(x, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            SkyColor.set(biome, SkyColor.original(biome));
            minecraft.setScreen(new SkyColorScreen(biome, biomeName));
        }).bounds(x + bw + 4, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x + 2 * (bw + 4), by, bw, 20).build());
    }

    private void offLine() {
        warmth = warmthFor(hue, sat);
        warmthSlider.refresh();
        apply();
    }

    private void apply() {
        SkyColor.set(biome, Mth.hsvToRgb(Mth.clamp(hue, 0, 1), Mth.clamp(sat, 0, 1), 1.0F));
        copied = "";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int c = SkyColor.get(biome), orig = SkyColor.original(biome);
        g.fill(X, Y, X + W, Y + 160, 0xC0101018);
        g.drawString(font, "Sky colour: " + biomeName, X + 6, Y + 6, 0xFFFFFF);
        g.drawString(font, "vanilla range only", X + 6, Y + 15, 0x9090A0);
        int sy = Y + 26 + 72;
        g.fill(X + 6, sy, X + 36, sy + 24, 0xFF000000 | c);
        g.renderOutline(X + 6, sy, 30, 24, 0xFFFFFFFF);
        g.drawString(font, String.format("#%06X   RGB %d, %d, %d", c, c >> 16 & 255, c >> 8 & 255, c & 255), X + 42, sy + 2, 0xFFFFFF);
        g.drawString(font, String.format("biome file: \"sky_color\": %d", c), X + 42, sy + 13, 0xFFFFA0);
        g.drawString(font, String.format("this biome's own: #%06X (%d)  %s", orig, orig, copied), X + 6, Y + 26 + 132 - 6, 0x9090A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** No darkening or blur: the world, and its sky, stay visible behind the panel. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------- helpers

    /** The temperature whose vanilla sky is this hue and saturation, if they're on vanilla's line. */
    private static Float warmthFor(float hue, float sat) {
        float t = (0.62222224F - hue) / 0.05F * 3.0F;
        if (t < SkyColor.TEMP_MIN - 0.01F || t > SkyColor.TEMP_MAX + 0.01F) return null;
        return Math.abs(SkyColor.satFor(t) - sat) < 0.004F ? Mth.clamp(t, SkyColor.TEMP_MIN, SkyColor.TEMP_MAX) : null;
    }

    private static float lerp(double v, float a, float b) {
        return (float) (a + (b - a) * v);
    }

    private static double unit(float x, float a, float b) {
        return Mth.clamp((x - a) / (b - a), 0, 1);
    }

    private static float[] hsv(int rgb) {
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) / 6F;
        else if (max == g) h = ((b - r) / d + 2) / 6F;
        else h = ((r - g) / d + 4) / 6F;
        if (h < 0) h += 1;
        return new float[] {h, max == 0 ? 0 : d / max, max};
    }

    private static final class Slider extends AbstractSliderButton {
        private final java.util.function.DoubleConsumer onChange;
        private final java.util.function.DoubleFunction<String> label;

        Slider(int x, int y, int w, double value, java.util.function.DoubleConsumer onChange, java.util.function.DoubleFunction<String> label) {
            super(x, y, w, 20, Component.empty(), value);
            this.onChange = onChange;
            this.label = label;
            updateMessage();
        }

        void set(double v) {
            this.value = Mth.clamp(v, 0, 1);
            updateMessage();
        }

        void refresh() {
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (label != null) setMessage(Component.literal(label.apply(value)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(value);
        }
    }
}

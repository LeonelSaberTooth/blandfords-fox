package net.blandfordsfox.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

/**
 * The /middleeast skycolor panel: a small box in the top-left corner (the sky stays in view).
 * Any colour: Hue, Saturation and Brightness sliders over their full range, or type a hex
 * code. The Warmth slider walks vanilla's own temperature-to-sky line, as a starting point.
 * Shows the colour as a swatch, hex, RGB and the decimal number a biome file uses, with
 * Copy / Reset / Done.
 */
public class SkyColorScreen extends Screen {
    private static final int W = 230, X = 6, Y = 6, H = 214;

    private final Biome biome;
    private final String biomeName;
    private float hue, sat, val;
    private Float warmth;   // null when the colour is off vanilla's line
    private Slider warmthSlider, hueSlider, satSlider, valSlider;
    private EditBox hexBox;
    private boolean updating;
    private String copied = "";

    public SkyColorScreen(Biome biome, String biomeName) {
        super(Component.literal("Sky colour"));
        this.biome = biome;
        this.biomeName = biomeName;
        float[] hsv = hsv(SkyColor.get(biome));
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        warmth = warmthFor(hue, sat, val);
    }

    @Override
    protected void init() {
        int x = X + 6, w = W - 12, y = Y + 26;
        float t0 = warmth != null ? warmth : (SkyColor.TEMP_MIN + SkyColor.TEMP_MAX) / 2;
        warmthSlider = addRenderableWidget(new Slider(x, y, w, unit(t0, SkyColor.TEMP_MIN, SkyColor.TEMP_MAX), v -> {
            warmth = lerp(v, SkyColor.TEMP_MIN, SkyColor.TEMP_MAX);
            hue = SkyColor.hueFor(warmth);
            sat = SkyColor.satFor(warmth);
            val = 1.0F;
            syncSliders();
            apply();
        }, v -> warmth == null ? "Vanilla warmth: (off vanilla's line)" : String.format("Vanilla warmth (temperature): %.2f", lerp(v, SkyColor.TEMP_MIN, SkyColor.TEMP_MAX))));
        hueSlider = addRenderableWidget(new Slider(x, y + 22, w, hue, v -> { hue = (float) v; changed(); },
                v -> String.format("Hue: %.0f\u00b0", v * 360)));
        satSlider = addRenderableWidget(new Slider(x, y + 44, w, sat, v -> { sat = (float) v; changed(); },
                v -> String.format("Saturation: %.0f%%", v * 100)));
        valSlider = addRenderableWidget(new Slider(x, y + 66, w, val, v -> { val = (float) v; changed(); },
                v -> String.format("Brightness: %.0f%%", v * 100)));

        hexBox = addRenderableWidget(new EditBox(font, x + 64, y + 118, 70, 16, Component.literal("Hex")));
        hexBox.setMaxLength(7);
        hexBox.setValue(String.format("#%06X", SkyColor.get(biome)));
        hexBox.setResponder(text -> {
            if (updating) return;
            String t = text.startsWith("#") ? text.substring(1) : text;
            if (!t.matches("[0-9a-fA-F]{6}")) return;
            float[] hsv = hsv(Integer.parseInt(t, 16));
            hue = hsv[0]; sat = hsv[1]; val = hsv[2];
            syncSliders();
            changed();
        });

        int by = y + 142, bw = (w - 8) / 3;
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

    private void syncSliders() {
        hueSlider.set(hue);
        satSlider.set(sat);
        valSlider.set(val);
    }

    private void changed() {
        warmth = warmthFor(hue, sat, val);
        warmthSlider.refresh();
        apply();
    }

    private void apply() {
        int c = Mth.hsvToRgb(Mth.clamp(hue, 0, 0.9999F), Mth.clamp(sat, 0, 1), Mth.clamp(val, 0, 1));
        SkyColor.set(biome, c);
        if (hexBox != null && !hexBox.isFocused()) {
            updating = true;
            hexBox.setValue(String.format("#%06X", c));
            updating = false;
        }
        copied = "";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int c = SkyColor.get(biome), orig = SkyColor.original(biome);
        int y = Y + 26;
        g.fill(X, Y, X + W, Y + H, 0xC0101018);
        g.drawString(font, "Sky colour: " + biomeName, X + 6, Y + 6, 0xFFFFFF);
        g.drawString(font, "any colour; warmth = vanilla's own skies", X + 6, Y + 15, 0x9090A0);
        int sy = y + 90;
        g.fill(X + 6, sy, X + 36, sy + 22, 0xFF000000 | c);
        g.renderOutline(X + 6, sy, 30, 22, 0xFFFFFFFF);
        g.drawString(font, String.format("#%06X   RGB %d, %d, %d", c, c >> 16 & 255, c >> 8 & 255, c & 255), X + 42, sy + 2, 0xFFFFFF);
        g.drawString(font, String.format("biome file: \"sky_color\": %d", c), X + 42, sy + 12, 0xFFFFA0);
        g.drawString(font, "Type hex:", X + 6, y + 122, 0xFFFFFF);
        g.drawString(font, String.format("this biome's own: #%06X (%d)  %s", orig, orig, copied), X + 6, y + 168, 0x9090A0);
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

    /** The temperature whose vanilla sky is this colour, if it's on vanilla's line. */
    private static Float warmthFor(float hue, float sat, float val) {
        if (val < 0.995F) return null;
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

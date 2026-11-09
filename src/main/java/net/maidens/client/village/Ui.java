package net.maidens.client.village;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Drawing helpers in the vanilla grey style the village screens use. */
final class Ui {
    static final int TEXT = 0xFF404040, TEXT_LIGHT = 0xFF707070, PANEL = 0xFFC6C6C6, INSET = 0xFF8B8B8B;

    private Ui() {}

    /** A raised panel (white top-left edge, dark bottom-right, black outline). */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w - 1, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFF555555);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFF555555);
    }

    /** A sunken area. */
    static void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** A button; hovered or "on" ones are highlighted. */
    static void button(GuiGraphics g, Font font, String text, int x, int y, int w, int h, boolean on, boolean hover, boolean enabled) {
        int bg = !enabled ? 0xFF505050 : on ? 0xFF7E8FD0 : hover ? 0xFF8C8C8C : 0xFF6F6F6F;
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, enabled ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Text drawn smaller (for labels on maps and cards). */
    static void small(GuiGraphics g, Font font, String text, float x, float y, int colour, boolean shadow, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 400);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, colour, shadow);
        g.pose().popPose();
    }
}

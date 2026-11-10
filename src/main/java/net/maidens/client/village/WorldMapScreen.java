package net.maidens.client.village;

import com.mojang.blaze3d.platform.NativeImage;
import net.maidens.MaidensMod;
import net.maidens.network.VillageActionPayload;
import net.maidens.politics.Stance;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The world map, seed-map style: biome colours for 2048 x 2048 blocks around where it was opened, every village in
 * it (coloured by how it treats your village; grey if it's a villager village or not met), and the roads between
 * them. Scroll to zoom, drag to move (moving far fetches a new area). Click a village to see it, and change your
 * village's stance towards it if you may.
 */
public class WorldMapScreen extends Screen {
    private static final ResourceLocation MAP = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "world_map");

    private record Village(long id, String name, int x, int z, boolean maiden, boolean visited, String style, Stance temper,
                           @Nullable Stance ours, @Nullable Stance theirs, int distance, boolean neighbour, String news, boolean walled) {}

    private record RoadLine(int ax, int az, int bx, int bz, int fromA, int fromB, int length) {}

    private final Screen parent;
    private long viewer;
    private boolean mayDecide;
    private int x0, z0, scale = 8, pixels = 256;
    private DynamicTexture texture;
    private final List<Village> villages = new ArrayList<>();
    private final List<RoadLine> roads = new ArrayList<>();

    private int px, py, pw, ph, mapX, mapY, mapW, mapH, sideX, sideW;
    /** Blocks at the middle of the view, and screen pixels per block. */
    private double centreX, centreZ, zoom = -1;
    private boolean dragging;
    private Village selected;
    private int refresh;

    public WorldMapScreen(Screen parent, CompoundTag data) {
        super(Component.literal("World map"));
        this.parent = parent;
        update(data);
    }

    public void update(CompoundTag t) {
        int[] area = t.getIntArray("Area");
        if (area.length == 4) {
            boolean first = texture == null;
            x0 = area[0];
            z0 = area[1];
            scale = area[2];
            pixels = area[3];
            picture(t.getByteArray("Pixels"), t.getIntArray("Palette"));
            if (first) {
                centreX = x0 + pixels * scale / 2.0;
                centreZ = z0 + pixels * scale / 2.0;
            }
        }
        viewer = t.getLong("Viewer");
        mayDecide = t.getBoolean("MayDecide");
        villages.clear();
        for (Tag e : t.getList("Villages", Tag.TAG_COMPOUND)) {
            CompoundTag v = (CompoundTag) e;
            int[] pos = v.getIntArray("Pos");
            villages.add(new Village(v.getLong("Id"), v.getString("Name"), pos.length == 2 ? pos[0] : 0, pos.length == 2 ? pos[1] : 0,
                    v.getBoolean("Maiden"), v.getBoolean("Visited"), v.getString("Style"), Stance.byId(v.getInt("Temper")),
                    v.contains("Ours") ? Stance.byId(v.getInt("Ours")) : null, v.contains("Theirs") ? Stance.byId(v.getInt("Theirs")) : null,
                    v.getInt("Distance"), v.getBoolean("Neighbour"), v.getString("News"), v.getBoolean("Walled")));
        }
        roads.clear();
        for (Tag e : t.getList("Roads", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) e;
            int[] ends = r.getIntArray("Ends"), built = r.getIntArray("Built");
            if (ends.length == 4 && built.length == 3) roads.add(new RoadLine(ends[0], ends[1], ends[2], ends[3], built[0], built[1], built[2]));
        }
        if (selected != null) {
            for (Village v : villages) if (v.id() == selected.id()) selected = v;
        }
    }

    private void picture(byte[] pixelsIn, int[] palette) {
        NativeImage img = new NativeImage(pixels, pixels, true);
        for (int z = 0; z < pixels; z++)
            for (int x = 0; x < pixels; x++) {
                int i = z * pixels + x;
                int idx = i < pixelsIn.length ? pixelsIn[i] & 0xFF : 0;
                int rgb = idx < palette.length ? palette[idx] : 0x404040;
                // NativeImage wants ABGR
                img.setPixelRGBA(x, z, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF));
            }
        if (texture != null) texture.close();
        texture = new DynamicTexture(img);
        Minecraft.getInstance().getTextureManager().register(MAP, texture);
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 600);
        ph = Math.min(height - 16, 330);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sideW = Math.min(170, pw / 3);
        sideX = px + pw - sideW - 6;
        mapX = px + 6;
        mapY = py + 6;
        mapW = sideX - mapX - 6;
        mapH = ph - 12;
        if (zoom < 0) zoom = Math.min(mapW, mapH) / (double) (pixels * scale);
    }

    private double toScreenX(double wx) {
        return mapX + mapW / 2.0 + (wx - centreX) * zoom;
    }

    private double toScreenZ(double wz) {
        return mapY + mapH / 2.0 + (wz - centreZ) * zoom;
    }

    @Override
    public void tick() {
        // villages in view are looked up a few at a time on the server: ask again now and then to see the new ones
        if (++refresh % 100 == 0) ask(x0 + pixels * scale / 2.0, z0 + pixels * scale / 2.0, true);
    }

    /** Asks for the map around x, z; villagesOnly leaves the picture out (it hasn't changed). */
    private void ask(double x, double z, boolean villagesOnly) {
        PacketDistributor.sendToServer(new VillageActionPayload(VillageActionPayload.WORLD_MAP, viewer, "", villagesOnly ? 1 : 0, (int) x, (int) z, 0));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph);
        Ui.inset(g, mapX, mapY, mapW, mapH, 0xFF101830);
        g.enableScissor(mapX + 1, mapY + 1, mapX + mapW - 1, mapY + mapH - 1);
        if (texture != null) {
            int sx = (int) toScreenX(x0), sz = (int) toScreenZ(z0), size = (int) (pixels * scale * zoom);
            g.blit(MAP, sx, sz, size, size, 0, 0, pixels, pixels, pixels, pixels);
        }
        for (RoadLine r : roads) drawRoad(g, r);
        Village hover = null;
        for (Village v : villages) {
            int sx = (int) toScreenX(v.x()), sz = (int) toScreenZ(v.z());
            int colour = v.id() == viewer ? 0xFFFFFFFF : !v.maiden() ? 0xFFA0A0A0 : v.theirs() != null ? 0xFF000000 | v.theirs().color : 0xFF707070;
            int r = v.maiden() ? 3 : 2;
            if (v.walled()) {
                // a walled village: a stone-grey ring around its marker
                g.fill(sx - r - 3, sz - r - 3, sx + r + 4, sz + r + 4, 0xFF000000);
                g.fill(sx - r - 2, sz - r - 2, sx + r + 3, sz + r + 3, 0xFF9A9A9A);
            }
            g.fill(sx - r - 1, sz - r - 1, sx + r + 2, sz + r + 2, 0xFF000000);
            g.fill(sx - r, sz - r, sx + r + 1, sz + r + 1, colour);
            if (v.id() == viewer) g.fill(sx - 1, sz - 1, sx + 2, sz + 2, 0xFF30A030);
            if (v == selected) g.renderOutline(sx - r - 3, sz - r - 3, 2 * r + 7, 2 * r + 7, 0xFFFFFF60);
            if (v.maiden() || zoom > 0.25) Ui.small(g, font, v.name(), sx + r + 3, sz - 3, v.maiden() ? 0xFFFFFFFF : 0xFFC8C8C8, true, v.maiden() ? 0.75F : 0.6F);
            if (Math.abs(mouseX - sx) <= r + 2 && Math.abs(mouseY - sz) <= r + 2) hover = v;
        }
        g.disableScissor();
        String where = "";
        if (Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH)) {
            int wx = (int) Math.floor(centreX + (mouseX - mapX - mapW / 2.0) / zoom), wz = (int) Math.floor(centreZ + (mouseY - mapY - mapH / 2.0) / zoom);
            where = "  |  " + wx + ", " + wz;
        }
        if (texture == null) g.drawCenteredString(font, "Drawing the map...", mapX + mapW / 2, mapY + mapH / 2 - 4, 0xFFFFFFFF);
        Ui.small(g, font, "scroll to zoom, drag to move" + where, mapX + 3, mapY + mapH - 8, 0xFFFFFFFF, true, 0.65F);
        renderSide(g, mouseX, mouseY);
        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hover.name()));
            tip.add(Component.literal(hover.x() + ", " + hover.z()).withStyle(ChatFormatting.DARK_GRAY));
            tip.add(Component.literal(!hover.maiden() ? "a villager village" : hover.id() == viewer ? "your village" : hover.theirs() == null ? "a maiden village"
                    : "they're " + hover.theirs().label + " towards you").withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    /** The road as a line: solid where it's built, faint where it's still planned. */
    private void drawRoad(GuiGraphics g, RoadLine r) {
        double len = Math.max(1, r.length());
        double dx = r.bx() - r.ax(), dz = r.bz() - r.az(), total = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.max(2, total * zoom);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double along = t * total - 48;      // steps from the edge of village a (as the road counts them)
            boolean built = along < r.fromA() || along >= len - r.fromB();
            if (!built && i % 3 != 0) continue;
            int sx = (int) toScreenX(r.ax() + dx * t), sz = (int) toScreenZ(r.az() + dz * t);
            g.fill(sx, sz, sx + 1, sz + 1, built ? 0xFFB08850 : 0x90D0B080);
        }
    }

    private void renderSide(GuiGraphics g, int mouseX, int mouseY) {
        int y = py + 8, x = sideX + 2;
        g.drawString(font, "World map", x, y, Ui.TEXT, false);
        y += 14;
        Village v = selected;
        if (v == null) {
            for (FormattedCharSequence line : font.split(Component.literal("Click a village on the map to see it."), sideW - 4)) {
                g.drawString(font, line, x, y, Ui.TEXT_LIGHT, false);
                y += 10;
            }
            y += 6;
            legend(g, x, y, 0xFF000000 | Stance.TRADE.color, "trading with you");
            legend(g, x, y + 10, 0xFF000000 | Stance.NEUTRAL.color, "neutral");
            legend(g, x, y + 20, 0xFF000000 | Stance.AGGRESSIVE.color, "hostile");
            legend(g, x, y + 30, 0xFFA0A0A0, "villager village");
            legend(g, x, y + 40, 0xFFFFFFFF, "your village");
            g.fill(x - 1, y + 50, x + 8, y + 59, 0xFF9A9A9A);
            g.fill(x + 1, y + 52, x + 6, y + 57, 0xFF707070);
            g.drawString(font, "walled, with a castle", x + 11, y + 50, Ui.TEXT, false);
        } else {
            g.drawString(font, v.name(), x, y, 0xFF202020, false);
            y += 11;
            List<String> lines = new ArrayList<>();
            lines.add(capital(v.style()) + (v.maiden() ? (v.walled() ? " walled maiden village" : " maiden village") : " villager village"));
            if (v.id() == viewer) lines.add("This is your village.");
            if (v.maiden() && v.id() != viewer) {
                lines.add(v.distance() + " blocks away" + (v.neighbour() ? "" : " (too far to deal with)"));
                lines.add("By nature: " + v.temper().label);
                if (v.theirs() != null) lines.add("Towards you: " + v.theirs().label);
                if (v.ours() != null) lines.add("You towards them: " + v.ours().label);
            }
            if (!v.visited()) lines.add("Not visited yet");
            if (!v.news().isEmpty()) lines.add(v.news());
            for (String s : lines) {
                for (FormattedCharSequence line : font.split(Component.literal(s), sideW - 4)) {
                    g.drawString(font, line, x, y, Ui.TEXT, false);
                    y += 10;
                }
            }
            y += 6;
            if (v.maiden() && v.id() != viewer && v.neighbour()) {
                if (mayDecide) {
                    for (Stance s : Stance.values()) {
                        String label = s == Stance.TRADE ? "Trade" : s == Stance.NEUTRAL ? "Neutral" : "Aggressive";
                        Ui.button(g, font, label, x, y, sideW - 4, 16, v.ours() == s, Ui.inside(mouseX, mouseY, x, y, sideW - 4, 16), true);
                        y += 19;
                    }
                } else {
                    for (FormattedCharSequence line : font.split(Component.literal("You can't speak for your village yet."), sideW - 4)) {
                        g.drawString(font, line, x, y, Ui.TEXT_LIGHT, false);
                        y += 10;
                    }
                }
            }
        }
        int by = py + ph - 26;
        Ui.button(g, font, "Back", x, by, sideW - 4, 16, false, Ui.inside(mouseX, mouseY, x, by, sideW - 4, 16), true);
    }

    private void legend(GuiGraphics g, int x, int y, int colour, String text) {
        g.fill(x, y + 1, x + 7, y + 8, 0xFF000000);
        g.fill(x + 1, y + 2, x + 6, y + 7, colour);
        g.drawString(font, text, x + 11, y, Ui.TEXT, false);
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = sideX + 2, by = py + ph - 26;
        if (Ui.inside(mx, my, x, by, sideW - 4, 16)) {
            onClose();
            return true;
        }
        if (selected != null && mayDecide && selected.maiden() && selected.id() != viewer && selected.neighbour()) {
            int y = buttonsTop();
            for (Stance s : Stance.values()) {
                if (Ui.inside(mx, my, x, y, sideW - 4, 16)) {
                    if (s != selected.ours()) {
                        PacketDistributor.sendToServer(new VillageActionPayload(VillageActionPayload.STANCE, viewer, "", selected.id(), 0, 0, s.ordinal()));
                        ask(x0 + pixels * scale / 2.0, z0 + pixels * scale / 2.0, true);
                    }
                    return true;
                }
                y += 19;
            }
        }
        if (Ui.inside(mx, my, mapX, mapY, mapW, mapH)) {
            for (Village v : villages) {
                if (Math.abs(mx - toScreenX(v.x())) <= 5 && Math.abs(my - toScreenZ(v.z())) <= 5) {
                    selected = v;
                    return true;
                }
            }
            dragging = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** For the client test: picks the nearest other maiden village, as if clicked. */
    public void selectForTest() {
        for (Village v : villages) {
            if (!v.maiden() || v.id() == viewer) continue;
            if (selected == null || v.distance() < selected.distance()) selected = v;
        }
    }

    /** Where the stance buttons start (laid out as drawn). */
    private int buttonsTop() {
        Village v = selected;
        int y = py + 8 + 14 + 11;
        List<String> lines = new ArrayList<>();
        lines.add(capital(v.style()) + (v.walled() ? " walled maiden village" : " maiden village"));
        lines.add(v.distance() + " blocks away");
        lines.add("By nature: " + v.temper().label);
        if (v.theirs() != null) lines.add("Towards you: " + v.theirs().label);
        if (v.ours() != null) lines.add("You towards them: " + v.ours().label);
        if (!v.visited()) lines.add("Not visited yet");
        if (!v.news().isEmpty()) lines.add(v.news());
        for (String s : lines) y += font.split(Component.literal(s), sideW - 4).size() * 10;
        return y + 6;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            centreX -= dx / zoom;
            centreZ -= dy / zoom;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            // moved well away from the area we have: fetch the area around the new middle
            double half = pixels * scale / 2.0;
            if (Math.abs(centreX - (x0 + half)) > half * 0.5 || Math.abs(centreZ - (z0 + half)) > half * 0.5) ask(centreX, centreZ, false);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (Ui.inside(mx, my, mapX, mapY, mapW, mapH)) {
            double min = Math.min(mapW, mapH) / (double) (pixels * scale) * 0.5;
            zoom = Mth.clamp(zoom * (scrollY > 0 ? 1.25 : 0.8), min, 1.0);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
        // stances may have changed here: the village screen asks for itself again
        if (parent instanceof VillageScreen v) PacketDistributor.sendToServer(new VillageActionPayload(VillageActionPayload.REFRESH, v.villageId(), "", 0, 0, 0, 0));
    }

    @Override
    public void removed() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(MAP);
        texture = null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

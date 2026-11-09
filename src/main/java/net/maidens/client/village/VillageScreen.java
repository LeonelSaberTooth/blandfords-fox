package net.maidens.client.village;

import com.mojang.blaze3d.platform.NativeImage;
import net.maidens.MaidensMod;
import net.maidens.network.VillageActionPayload;
import net.maidens.politics.Stance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The village screen, opened at its bell: a map of the village on the left (buildings, and where each maiden
 * sleeps), and on the right: Build (pictures of every building the village can make; pick one, then click a spot
 * on the map; right-click or R turns it), Diplomacy (how it stands with each neighbour, and for those who may,
 * changing that), News, and a button for the world map.
 */
public class VillageScreen extends Screen {
    private static final ResourceLocation MAP = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "village_map");
    private static final String[] TABS = {"Build", "Diplomacy", "News"};

    private record Piece(int x0, int z0, int x1, int z1, String name, boolean isNew) {}

    private record Person(String name, String title, int bedX, int bedZ, boolean hasBed, boolean royal) {}

    private record Tpl(String name, String display, int beds, int sx, int sz) {}

    private record Neighbour(long id, String name, int distance, Stance ours, Stance theirs) {}

    private long id;
    private String name = "", royal = "", right = "", howTo = "", project = "";
    private boolean citizen, building;
    private int mx0, mz0, mpw, mph, mstep = 1;
    private DynamicTexture texture;
    private final List<Piece> pieces = new ArrayList<>();
    private final List<Person> people = new ArrayList<>();
    private final List<String> news = new ArrayList<>();
    private final List<Tpl> templates = new ArrayList<>();
    private final List<Neighbour> neighbours = new ArrayList<>();

    private int px, py, pw, ph, mapX, mapY, mapW, mapH, drawX, drawY, sideX, sideY, sideW, sideH;
    private float mapScale = 1;
    private int tab;
    private double scroll;
    private Tpl selected;
    private int rotation;

    public VillageScreen(CompoundTag data) {
        super(Component.literal("Village"));
        update(data);
    }

    public long villageId() {
        return id;
    }

    /** New data from the server (after opening, or after something was done). */
    public void update(CompoundTag t) {
        id = t.getLong("Id");
        name = t.getString("Name");
        royal = t.getString("Royal");
        right = t.getString("Right");
        howTo = t.getString("HowTo");
        citizen = t.getBoolean("Citizen");
        building = t.getBoolean("Building");
        project = t.getString("Project");
        int[] m = t.getIntArray("Map");
        if (m.length == 5) {
            mx0 = m[0];
            mz0 = m[1];
            mpw = Math.max(1, m[2]);
            mph = Math.max(1, m[3]);
            mstep = Math.max(1, m[4]);
            picture(t.getByteArray("Colours"));
        }
        pieces.clear();
        for (Tag e : t.getList("Pieces", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) e;
            int[] b = p.getIntArray("Box");
            if (b.length == 4) pieces.add(new Piece(b[0], b[1], b[2], b[3], p.getString("Name"), p.getBoolean("New")));
        }
        people.clear();
        for (Tag e : t.getList("Residents", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) e;
            int[] bed = p.getIntArray("Bed");
            people.add(new Person(p.getString("Name"), p.getString("Title"), bed.length == 2 ? bed[0] : 0, bed.length == 2 ? bed[1] : 0,
                    bed.length == 2, p.getBoolean("Royal")));
        }
        news.clear();
        for (Tag e : t.getList("News", Tag.TAG_STRING)) news.add(e.getAsString());
        templates.clear();
        for (Tag e : t.getList("Templates", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) e;
            int[] s = p.getIntArray("Size");
            templates.add(new Tpl(p.getString("Name"), p.getString("Display"), p.getInt("Beds"), s.length == 3 ? s[0] : 8, s.length == 3 ? s[2] : 8));
        }
        neighbours.clear();
        for (Tag e : t.getList("Neighbours", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) e;
            neighbours.add(new Neighbour(p.getLong("Id"), p.getString("Name"), p.getInt("Distance"), Stance.byId(p.getInt("Ours")), Stance.byId(p.getInt("Theirs"))));
        }
        if (building) selected = null;
    }

    private void picture(byte[] colours) {
        NativeImage img = new NativeImage(mpw, mph, true);
        for (int z = 0; z < mph; z++)
            for (int x = 0; x < mpw; x++) {
                int i = z * mpw + x;
                int packed = i < colours.length ? colours[i] & 0xFF : 0;
                img.setPixelRGBA(x, z, packed == 0 ? 0xFF2A2A2A : MapColor.getColorFromPackedId(packed));
            }
        if (texture != null) texture.close();
        texture = new DynamicTexture(img);
        Minecraft.getInstance().getTextureManager().register(MAP, texture);
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        pw = Math.min(width - 16, 560);
        ph = Math.min(height - 16, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sideW = Math.min(190, pw / 2 - 10);
        sideX = px + pw - sideW - 6;
        sideY = py + 6;
        sideH = ph - 12;
        mapX = px + 6;
        mapY = py + 20;
        mapW = sideX - mapX - 6;
        mapH = ph - 26;
        mapScale = Math.min((mapW - 4) / (float) mpw, (mapH - 4) / (float) mph);
        drawX = mapX + (int) ((mapW - mpw * mapScale) / 2);
        drawY = mapY + (int) ((mapH - mph * mapScale) / 2);
    }

    private float sx(int worldX) {
        return drawX + (worldX - mx0) / (float) mstep * mapScale;
    }

    private float sz(int worldZ) {
        return drawY + (worldZ - mz0) / (float) mstep * mapScale;
    }

    private int worldX(double screenX) {
        return mx0 + (int) Math.floor((screenX - drawX) / mapScale * mstep);
    }

    private int worldZ(double screenY) {
        return mz0 + (int) Math.floor((screenY - drawY) / mapScale * mstep);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph);
        g.drawString(font, name, mapX, py + 7, Ui.TEXT, false);
        String sub = royal.isEmpty() ? "no royal yet" : "Royal: Queen " + royal;
        g.drawString(font, sub, mapX + font.width(name) + 8, py + 7, Ui.TEXT_LIGHT, false);
        List<Component> tooltip = renderMap(g, mouseX, mouseY);
        renderSide(g, mouseX, mouseY, partialTick);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    private List<Component> renderMap(GuiGraphics g, int mouseX, int mouseY) {
        Ui.inset(g, mapX, mapY, mapW, mapH, 0xFF2A2A2A);
        if (texture != null) {
            g.blit(MAP, drawX, drawY, (int) (mpw * mapScale), (int) (mph * mapScale), 0, 0, mpw, mph, mpw, mph);
        }
        boolean overMap = Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH);
        int wx = worldX(mouseX), wz = worldZ(mouseY);
        List<Component> tip = null;
        // buildings
        for (Piece p : pieces) {
            int x0 = (int) sx(p.x0()), z0 = (int) sz(p.z0()), x1 = (int) sx(p.x1() + 1), z1 = (int) sz(p.z1() + 1);
            boolean over = overMap && selected == null && wx >= p.x0() && wx <= p.x1() && wz >= p.z0() && wz <= p.z1();
            if (over) g.fill(x0, z0, x1, z1, 0x50FFFFFF);
            g.renderOutline(x0, z0, Math.max(1, x1 - x0), Math.max(1, z1 - z0), p.isNew() ? 0xFFE8C040 : over ? 0xFFFFFFFF : 0x90FFF4D0);
            if (over) {
                tip = new ArrayList<>();
                tip.add(Component.literal(p.name().isEmpty() ? "Building" : capital(p.name())));
                if (p.isNew()) tip.add(Component.literal("Being built").withStyle(net.minecraft.ChatFormatting.YELLOW));
                List<String> sleepers = new ArrayList<>();
                for (Person q : people) if (q.hasBed() && q.bedX() >= p.x0() && q.bedX() <= p.x1() && q.bedZ() >= p.z0() && q.bedZ() <= p.z1()) sleepers.add(q.name());
                if (!sleepers.isEmpty()) tip.add(Component.literal("Sleeps here: " + String.join(", ", sleepers)).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        // beds, with who sleeps there
        for (Person q : people) {
            if (!q.hasBed()) continue;
            float bx = sx(q.bedX()), bz = sz(q.bedZ());
            g.fill((int) bx - 1, (int) bz - 1, (int) bx + 3, (int) bz + 2, 0xFFB02020);
            g.fill((int) bx - 1, (int) bz - 1, (int) bx, (int) bz + 2, 0xFFEEEEEE);
            Ui.small(g, font, q.name(), bx + 4, bz - 2, q.royal() ? 0xFFFFD040 : 0xFFFFFFFF, true, 0.6F);
        }
        // where a chosen building would go
        if (selected != null && overMap) {
            int[] fp = footprint(selected, rotation);
            int x0 = (int) sx(wx + fp[0]), z0 = (int) sz(wz + fp[1]), x1 = (int) sx(wx + fp[2] + 1), z1 = (int) sz(wz + fp[3] + 1);
            g.fill(x0, z0, x1, z1, 0x6030FF30);
            g.renderOutline(x0, z0, Math.max(1, x1 - x0), Math.max(1, z1 - z0), 0xFF30E030);
            tip = List.of(Component.literal(selected.display() + " here"), Component.literal("Click to build, right-click or R to turn").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        int homeless = 0;
        for (Person q : people) if (!q.hasBed()) homeless++;
        String foot = people.size() + " maidens" + (homeless > 0 ? ", " + homeless + " without a bed" : "") + (building ? " - building: " + capital(project) : "");
        Ui.small(g, font, foot, mapX + 3, mapY + mapH - 8, 0xFFFFFFFF, true, 0.75F);
        return tip;
    }

    /** A building's footprint around its corner for a rotation: min x, min z, max x, max z (as the server places it). */
    private static int[] footprint(Tpl t, int rot) {
        int ax = t.sx() - 1, az = t.sz() - 1;
        int[][] corners = {{0, 0}, {ax, az}};
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : corners) {
            int x = c[0], z = c[1], rx, rz;
            switch (rot) {
                case 1 -> { rx = -z; rz = x; }
                case 2 -> { rx = -x; rz = -z; }
                case 3 -> { rx = z; rz = -x; }
                default -> { rx = x; rz = z; }
            }
            minX = Math.min(minX, rx);
            minZ = Math.min(minZ, rz);
            maxX = Math.max(maxX, rx);
            maxZ = Math.max(maxZ, rz);
        }
        return new int[]{minX, minZ, maxX, maxZ};
    }

    private void renderSide(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int tw = (sideW - 4) / 3;
        for (int i = 0; i < TABS.length; i++) {
            int bx = sideX + i * (tw + 2);
            Ui.button(g, font, TABS[i], bx, sideY, tw, 16, tab == i, Ui.inside(mouseX, mouseY, bx, sideY, tw, 16), true);
        }
        int cy = sideY + 20, ch = sideH - 20 - 40;
        Ui.inset(g, sideX, cy, sideW, ch, 0xFFA9A9A9);
        g.enableScissor(sideX + 1, cy + 1, sideX + sideW - 1, cy + ch - 1);
        switch (tab) {
            case 0 -> renderBuild(g, mouseX, mouseY, cy, ch);
            case 1 -> renderDiplomacy(g, mouseX, mouseY, cy);
            default -> renderNews(g, cy);
        }
        g.disableScissor();
        int by = sideY + sideH - 36;
        Ui.button(g, font, "World map", sideX, by, sideW, 16, false, Ui.inside(mouseX, mouseY, sideX, by, sideW, 16), true);
        Ui.button(g, font, "Done", sideX, by + 20, sideW, 16, false, Ui.inside(mouseX, mouseY, sideX, by + 20, sideW, 16), true);
    }

    private int cardSize() {
        return (sideW - 8) / 2;
    }

    private void renderBuild(GuiGraphics g, int mouseX, int mouseY, int cy, int ch) {
        int x = sideX + 3, y = cy + 3 - (int) scroll;
        if (!citizen || building) {
            String msg = building ? "Busy building: " + capital(project) + ". One building at a time." : "Only citizens of " + name + " choose what's built.";
            for (FormattedCharSequence line : font.split(Component.literal(msg), sideW - 8)) {
                g.drawString(font, line, x + 1, y, Ui.TEXT, false);
                y += 10;
            }
            y += 4;
        }
        int size = cardSize();
        float turn = (System.currentTimeMillis() % 20000L) / 20000.0F * 360.0F;
        for (int i = 0; i < templates.size(); i++) {
            Tpl t = templates.get(i);
            int cx = x + (i % 2) * (size + 2), cardY = y + (i / 2) * (size + 16);
            if (cardY + size + 14 < cy || cardY > cy + ch) continue;
            boolean hover = Ui.inside(mouseX, mouseY, cx, cardY, size, size + 14) && mouseY > cy && mouseY < cy + ch;
            g.fill(cx, cardY, cx + size, cardY + size + 14, t == selected ? 0xFFD8D8A0 : hover ? 0xFFBDBDBD : 0xFF9C9C9C);
            g.renderOutline(cx, cardY, size, size + 14, t == selected ? 0xFFE8C040 : 0xFF555555);
            TemplateCache.Building b = TemplateCache.get(id, t.name());
            if (b != null) IsoRenderer.render(g, b, cx + 2, cardY + 2, size - 4, size - 4, hover || t == selected ? turn : 0);
            else g.drawCenteredString(font, "...", cx + size / 2, cardY + size / 2 - 4, 0xFFFFFFFF);
            Ui.small(g, font, capital(t.display()), cx + 2, cardY + size + 1, Ui.TEXT, false, 0.65F);
            Ui.small(g, font, t.beds() == 1 ? "1 bed" : t.beds() + " beds", cx + 2, cardY + size + 7, Ui.TEXT_LIGHT, false, 0.6F);
        }
    }

    private void renderDiplomacy(GuiGraphics g, int mouseX, int mouseY, int cy) {
        int x = sideX + 4, y = cy + 4 - (int) scroll;
        String head = right.isEmpty() ? howTo : "You may speak for " + name + ": " + right + ".";
        for (FormattedCharSequence line : font.split(Component.literal(head), sideW - 8)) {
            g.drawString(font, line, x, y, right.isEmpty() ? Ui.TEXT_LIGHT : 0xFF2A6A2A, false);
            y += 10;
        }
        y += 4;
        if (neighbours.isEmpty()) g.drawString(font, "No neighbours known yet.", x, y, Ui.TEXT, false);
        for (Neighbour n : neighbours) {
            g.drawString(font, n.name(), x, y, Ui.TEXT, false);
            Ui.small(g, font, n.distance() + " blocks", x + font.width(n.name()) + 4, y + 2, Ui.TEXT_LIGHT, false, 0.7F);
            y += 10;
            Ui.small(g, font, "we're ", x, y, Ui.TEXT_LIGHT, false, 0.7F);
            Ui.small(g, font, n.ours().label, x + 20, y, 0xFF000000 | darker(n.ours().color), false, 0.7F);
            Ui.small(g, font, "they're ", x + 70, y, Ui.TEXT_LIGHT, false, 0.7F);
            Ui.small(g, font, n.theirs().label, x + 95, y, 0xFF000000 | darker(n.theirs().color), false, 0.7F);
            y += 8;
            if (!right.isEmpty()) {
                int bw = (sideW - 14) / 3;
                for (Stance s : Stance.values()) {
                    int bx = x + s.ordinal() * (bw + 2);
                    Ui.button(g, font, s == Stance.TRADE ? "Trade" : s == Stance.NEUTRAL ? "Neutral" : "Hostile", bx, y, bw, 12,
                            n.ours() == s, Ui.inside(mouseX, mouseY, bx, y, bw, 12), true);
                }
                y += 14;
            }
            y += 5;
        }
    }

    private void renderNews(GuiGraphics g, int cy) {
        int x = sideX + 4, y = cy + 4 - (int) scroll;
        if (news.isEmpty()) g.drawString(font, "Nothing has happened yet.", x, y, Ui.TEXT, false);
        for (String n : news) {
            for (FormattedCharSequence line : font.split(Component.literal(n), sideW - 8)) {
                g.drawString(font, line, x, y, Ui.TEXT, false);
                y += 10;
            }
            y += 3;
        }
    }

    private static int darker(int rgb) {
        return (rgb >> 1) & 0x7F7F7F;
    }

    private static String capital(String s) {
        s = s.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // tabs and buttons
        int tw = (sideW - 4) / 3;
        for (int i = 0; i < TABS.length; i++) {
            if (Ui.inside(mx, my, sideX + i * (tw + 2), sideY, tw, 16)) {
                tab = i;
                scroll = 0;
                return true;
            }
        }
        int by = sideY + sideH - 36;
        if (Ui.inside(mx, my, sideX, by, sideW, 16)) {
            openWorldMap();
            return true;
        }
        if (Ui.inside(mx, my, sideX, by + 20, sideW, 16)) {
            onClose();
            return true;
        }
        // the map: place or turn the chosen building
        if (selected != null && Ui.inside(mx, my, mapX, mapY, mapW, mapH)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                rotation = (rotation + 1) % 4;
                return true;
            }
            PacketDistributor.sendToServer(new VillageActionPayload(VillageActionPayload.BUILD, id, selected.name(), 0, worldX(mx), worldZ(my), rotation));
            selected = null;
            return true;
        }
        int cy = sideY + 20, ch = sideH - 60;
        if (Ui.inside(mx, my, sideX, cy, sideW, ch)) {
            if (tab == 0 && citizen && !building) {
                int size = cardSize(), x = sideX + 3, y = cy + 3 - (int) scroll;
                for (int i = 0; i < templates.size(); i++) {
                    int cx = x + (i % 2) * (size + 2), cardY = y + (i / 2) * (size + 16);
                    if (Ui.inside(mx, my, cx, cardY, size, size + 14)) {
                        selected = templates.get(i) == selected ? null : templates.get(i);
                        rotation = 0;
                        return true;
                    }
                }
            }
            if (tab == 1 && !right.isEmpty()) return clickStance(mx, my, cy);
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Asks for the world map around this village (it opens when it arrives). */
    public void openWorldMap() {
        int cx = mx0 + mpw * mstep / 2, cz = mz0 + mph * mstep / 2;
        PacketDistributor.sendToServer(new VillageActionPayload(VillageActionPayload.WORLD_MAP, id, "", 0, cx, cz, 0));
    }

    public void showTab(int tab) {
        this.tab = tab;
        scroll = 0;
    }

    /** Lays the diplomacy rows out as they're drawn, to find the button under the mouse. */
    private boolean clickStance(double mx, double my, int cy) {
        int x = sideX + 4, y = cy + 4 - (int) scroll;
        y += font.split(Component.literal("You may speak for " + name + ": " + right + "."), sideW - 8).size() * 10 + 4;
        for (Neighbour n : neighbours) {
            y += 18;
            int bw = (sideW - 14) / 3;
            for (Stance s : Stance.values()) {
                if (Ui.inside(mx, my, x + s.ordinal() * (bw + 2), y, bw, 12)) {
                    if (s != n.ours()) PacketDistributor.sendToServer(new VillageActionPayload(VillageActionPayload.STANCE, id, "", n.id(), 0, 0, s.ordinal()));
                    return true;
                }
            }
            y += 19;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (Ui.inside(mx, my, sideX, sideY + 20, sideW, sideH - 60)) {
            scroll = Math.max(0, scroll - scrollY * 16);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_R && selected != null) {
            rotation = (rotation + 1) % 4;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && selected != null) {
            selected = null;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        // released here, not in removed(): the world map covers this screen and comes back to it
        if (texture != null) Minecraft.getInstance().getTextureManager().release(MAP);
        texture = null;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

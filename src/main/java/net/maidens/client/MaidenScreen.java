package net.maidens.client;

import net.maidens.maiden.work.Job;
import net.maidens.maiden.Maiden;
import net.maidens.network.ActionPayload;
import net.maidens.network.MenuPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * A maiden's menu, in the spirit of Minecraft Comes Alive: her portrait, how much she likes you in hearts,
 * what she just said, and buttons to chat, joke, compliment, flirt, give a gift, trade, hire her or open her bag.
 */
public class MaidenScreen extends Screen {
    private static final int W = 308, H = 196;
    private static final ResourceLocation HEART_EMPTY = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");

    private MenuPayload menu;
    private boolean workPage;
    private int x0, y0;

    public MaidenScreen(MenuPayload menu) {
        super(Component.literal(menu.name()));
        this.menu = menu;
    }

    public int entityId() {
        return menu.entityId();
    }

    public void update(MenuPayload newMenu) {
        menu = newMenu;
        rebuildWidgets();
    }

    /** For the client test. */
    public void showWorkPage() {
        workPage = true;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void send(int action, int arg) {
        PacketDistributor.sendToServer(new ActionPayload(menu.entityId(), action, arg));
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        int bx = x0 + 92, bw = 50, gap = 4, row1 = y0 + 118, row2 = row1 + 24, row3 = row2 + 26;
        if (!workPage) {
            String[] talk = {"Chat", "Joke", "Compliment", "Flirt"};
            int[] acts = {ActionPayload.CHAT, ActionPayload.JOKE, ActionPayload.COMPLIMENT, ActionPayload.FLIRT};
            int[] widths = {44, 44, 68, 44};
            for (int i = 0, x = bx; i < 4; x += widths[i] + gap, i++) {
                int a = acts[i];
                addRenderableWidget(Button.builder(Component.literal(talk[i]), b -> send(a, 0))
                        .bounds(x, row1, widths[i], 20).build());
            }
            ItemStack held = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getMainHandItem() : ItemStack.EMPTY;
            addRenderableWidget(Button.builder(Component.literal("Gift"), b -> send(ActionPayload.GIFT, 0))
                    .bounds(bx, row2, bw, 20)
                    .tooltip(Tooltip.create(Component.literal(held.isEmpty() ? "Hold the item you want to give her."
                            : "Give her the " + held.getHoverName().getString() + " in your hand.")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Trade"), b -> send(ActionPayload.TRADE, 0))
                    .bounds(bx + (bw + gap), row2, bw, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Work..."), b -> { workPage = true; rebuildWidgets(); })
                    .bounds(bx + 2 * (bw + gap), row2, bw, 20).build());
            Button bag = addRenderableWidget(Button.builder(Component.literal("Bag"), b -> send(ActionPayload.BAG, 0))
                    .bounds(bx + 3 * (bw + gap), row2, bw, 20)
                    .tooltip(Tooltip.create(Component.literal("What she gathered while working, and where to put tools, seeds, saplings, torches or a sword for her.")))
                    .build());
            bag.active = menu.canOpenBag();
        } else {
            Job[] jobs = {Job.FARM, Job.GUARD, Job.WOOD, Job.MINE, Job.FISH};
            String[] tips = {
                    "Harvests and replants the crops around her house. Seeds in her bag go on bare farmland.",
                    "Follows you and fights monsters (not creepers). Uses a sword or axe from her bag.",
                    "Cuts down the trees around where she is now. Replants saplings from her bag.",
                    "Digs a staircase down from here, the way you're facing, then a tunnel, digging out every ore she spots. Hold an ore or a stone block when you hire her and she'll go looking for that instead. Lights it with torches from her bag.",
                    "Fishes at the nearest water around where she is now."};
            for (int i = 0; i < jobs.length; i++) {
                Job j = jobs[i];
                int col = i % 3, row = i / 3;
                Button b = addRenderableWidget(Button.builder(Component.literal(j.button), x -> send(ActionPayload.HIRE, j.ordinal()))
                        .bounds(bx + col * (68 + gap), row == 0 ? row1 : row2, 68, 20)
                        .tooltip(Tooltip.create(Component.literal(tips[i]))).build());
                b.active = menu.employer() != 2;
            }
            Button stop = addRenderableWidget(Button.builder(Component.literal("Stop work"), b -> send(ActionPayload.STOP, 0))
                    .bounds(bx + 2 * (68 + gap), row2, 68, 20).build());
            stop.active = menu.job() != 0 && (menu.employer() == 1 || menu.married() == 1);
        }
        addRenderableWidget(Button.builder(Component.literal(workPage ? "Back" : "Close"), b -> {
            if (workPage) { workPage = false; rebuildWidgets(); } else onClose();
        }).bounds(bx + 79, row3, 60, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(x0, y0, x0 + W, y0 + H, 0xE0201A1E);
        g.renderOutline(x0, y0, W, H, 0xFF8A6C7A);
        g.fill(x0 + 6, y0 + 6, x0 + 84, y0 + H - 6, 0xFF2E262B);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(menu.entityId()) instanceof Maiden maiden) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0 + 8, y0 + 12, x0 + 82, y0 + H - 12, 50, 0.0625F, mouseX, mouseY, maiden);
        }
        int tx = x0 + 92, y = y0 + 10;
        g.drawString(font, Component.literal(menu.name()).withStyle(ChatFormatting.BOLD), tx, y, 0xFFF4D8E4, true);
        g.drawString(font, menu.personality(), tx + font.width(Component.literal(menu.name()).withStyle(ChatFormatting.BOLD)) + 8, y, 0xFFB79AA8, false);
        // hearts: each is ten points
        y += 14;
        for (int i = 0; i < 10; i++) {
            int hx = tx + i * 10, value = menu.affection() - i * 10;
            g.blitSprite(HEART_EMPTY, hx, y, 9, 9);
            if (value >= 10) g.blitSprite(HEART_FULL, hx, y, 9, 9);
            else if (value >= 5) g.blitSprite(HEART_HALF, hx, y, 9, 9);
        }
        g.drawString(font, menu.affection() + "/100", tx + 104, y + 1, 0xFFB79AA8, false);
        y += 14;
        g.drawString(font, status(), tx, y, 0xFFE0C9A6, false);
        // what she said
        y += 16;
        List<FormattedCharSequence> lines = font.split(Component.literal("“" + menu.message() + "”"), W - 100);
        for (int i = 0; i < Math.min(lines.size(), workPage ? 4 : 5); i++) {
            g.drawString(font, lines.get(i), tx, y + i * 11, 0xFFFFFFFF, false);
        }
        if (workPage) {
            String cost = menu.freeWork() ? "Free for you, my love. She works until you tell her to stop."
                    : menu.employer() == 1 ? "Switching jobs is free for the rest of her shift."
                    : "A day's work costs " + menu.rent() + " emeralds.";
            g.drawString(font, cost, tx, y0 + 104, 0xFFB79AA8, false);
        }
    }

    private String status() {
        Job job = Job.byId(menu.job());
        StringBuilder s = new StringBuilder();
        if (menu.married() == 1) s.append("Your wife");
        else if (menu.married() == 2) s.append("Married");
        else s.append("Single");
        if (job != Job.NONE) {
            s.append(" · ").append(job.doing.substring(0, 1).toUpperCase()).append(job.doing.substring(1));
            if (menu.employer() == 2) s.append(" for ").append(menu.employerName());
            if (menu.shiftLeft() > 0) s.append(", ").append(Math.max(1, menu.shiftLeft() / 1200)).append(" min left");
        }
        return s.toString();
    }
}

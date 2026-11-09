package net.maidens.village;

import net.maidens.maiden.Maiden;
import net.maidens.politics.Diplomacy;
import net.maidens.politics.KnownVillage;
import net.maidens.politics.Network;
import net.maidens.politics.Stance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The village's chat menu (ringing its bell, or /village): its name and your standing, and for citizens a list of
 * buildings to click on, which runs /village build &lt;building&gt;.
 */
public final class VillageMenu {
    private VillageMenu() {}

    public static void show(ServerLevel level, VillageRecord v, ServerPlayer p) {
        boolean citizen = v.isCitizen(p.getUUID());
        p.sendSystemMessage(Component.literal("~ " + v.name + " ~").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal(citizen ? "You are a citizen here." : "You're a visitor. Citizens are those the maidens like, trade with often, or marry.")
                .withStyle(ChatFormatting.GRAY));
        StringBuilder board = new StringBuilder();
        for (Maiden m : VillageSocial.residents(level, v)) {
            String wish = m.wishes().describe();
            if (!wish.isEmpty()) board.append(board.isEmpty() ? "" : "; ").append(m.name()).append(": ").append(wish);
        }
        p.sendSystemMessage(Component.literal(board.isEmpty() ? "Requests board: nobody needs anything right now." : "Requests board: " + board)
                .withStyle(ChatFormatting.GREEN));
        diplomacy(level, v, p);
        if (v.blueprint.isBuilding()) {
            String what = v.project.startsWith(v.style + "_") ? v.project.substring(v.style.length() + 1) : v.project;
            p.sendSystemMessage(Component.literal("The maidens are building: " + what.replace('_', ' ') + ". One building at a time.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!citizen) return;
        List<ResourceLocation> all = Buildings.templates(level, v.style);
        MutableComponent line = Component.literal("Build next to where you stand: ").withStyle(ChatFormatting.WHITE);
        for (ResourceLocation t : all) {
            String name = Buildings.displayName(t, v.style);
            line.append(Component.literal("[" + name + "] ").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/village build " + Buildings.shortName(t)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Build a " + name)))));
        }
        p.sendSystemMessage(line);
    }

    /** Its royal, its neighbours and how it stands with each, and (for those who may) buttons to change that. */
    private static void diplomacy(ServerLevel level, VillageRecord v, ServerPlayer p) {
        String royal = "nobody yet";
        for (Maiden m : VillageSocial.residents(level, v)) if (v.isRoyal(m.getUUID())) royal = "Queen " + m.name();
        Network n = Network.get(level);
        KnownVillage self = n.know(level, v);
        String right = Diplomacy.rightToDecide(level, v, p);
        p.sendSystemMessage(Component.literal("Royal: " + royal + ". " + (right != null ? "You may speak for " + v.name + " (" + right + ")."
                : Diplomacy.howToEarnIt(level, v, p))).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (KnownVillage o : n.neighbours(self)) {
            Stance ours = n.stance(self, o), theirs = n.stance(o, self);
            MutableComponent line = Component.literal("  " + o.name + " (" + Math.round(self.distanceTo(o)) + " blocks): we're " + ours.label
                    + ", they're " + theirs.label + " ").withStyle(ChatFormatting.GRAY);
            if (right != null) {
                for (Stance s : Stance.values()) {
                    if (s == ours) continue;
                    line.append(Component.literal("[" + s.label + "] ").withStyle(Style.EMPTY.withColor(s.color)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/village stance " + o.name + " " + s.name().toLowerCase()))));
                }
            }
            p.sendSystemMessage(line);
        }
        List<String> news = n.news(v.id);
        for (int i = 0; i < Math.min(3, news.size()); i++) p.sendSystemMessage(Component.literal("  " + news.get(i)).withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Starts a building a citizen chose, near them. Returns what to tell them. */
    public static String build(ServerLevel level, VillageRecord v, ServerPlayer p, String shortName) {
        if (!v.isCitizen(p.getUUID())) return "Only citizens of " + v.name + " can choose what gets built.";
        if (v.blueprint.isBuilding()) return "The maidens are still busy with the last building.";
        ResourceLocation t = templateNamed(level, v, shortName);
        if (t == null) return "There's no building called " + shortName + " in " + v.name + ".";
        Buildings.Plan plan = Buildings.build(level, v, t, p.blockPosition());
        if (plan == null) return "There's no flat, free spot for that near here. Try somewhere else.";
        return "The maidens will build " + Buildings.withArticle(Buildings.displayName(t, v.style)) + " at " + plan.box().getCenter().toShortString() + ".";
    }

    /** A citizen placed a building on the village screen. Returns what to tell them. */
    public static String buildAt(ServerLevel level, VillageRecord v, ServerPlayer p, String shortName, BlockPos corner, net.minecraft.world.level.block.Rotation rot) {
        if (!v.isCitizen(p.getUUID())) return "Only citizens of " + v.name + " can choose what gets built.";
        if (v.blueprint.isBuilding()) return "The maidens are still busy with the last building.";
        ResourceLocation t = templateNamed(level, v, shortName);
        if (t == null) return "There's no building called " + shortName + " in " + v.name + ".";
        String why = Buildings.buildAt(level, v, t, corner, rot);
        return why != null ? why : "The maidens will build " + Buildings.withArticle(Buildings.displayName(t, v.style)) + " there.";
    }

    static ResourceLocation templateNamed(ServerLevel level, VillageRecord v, String shortName) {
        for (ResourceLocation t : Buildings.templates(level, v.style)) if (Buildings.shortName(t).equals(shortName)) return t;
        return null;
    }
}

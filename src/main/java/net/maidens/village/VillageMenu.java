package net.maidens.village;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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

    /** Starts a building a citizen chose, near them. Returns what to tell them. */
    public static String build(ServerLevel level, VillageRecord v, ServerPlayer p, String shortName) {
        if (!v.isCitizen(p.getUUID())) return "Only citizens of " + v.name + " can choose what gets built.";
        if (v.blueprint.isBuilding()) return "The maidens are still busy with the last building.";
        ResourceLocation t = templateNamed(level, v, shortName);
        if (t == null) return "There's no building called " + shortName + " in " + v.name + ".";
        Buildings.Plan plan = Buildings.build(level, v, t, p.blockPosition());
        if (plan == null) return "There's no flat, free spot for that near here. Try somewhere else.";
        return "The maidens will build a " + Buildings.displayName(t, v.style) + " at " + plan.box().getCenter().toShortString() + ".";
    }

    static ResourceLocation templateNamed(ServerLevel level, VillageRecord v, String shortName) {
        for (ResourceLocation t : Buildings.templates(level, v.style)) if (Buildings.shortName(t).equals(shortName)) return t;
        return null;
    }
}

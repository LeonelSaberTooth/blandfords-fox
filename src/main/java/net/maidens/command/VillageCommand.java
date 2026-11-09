package net.maidens.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.maidens.politics.Diplomacy;
import net.maidens.politics.KnownVillage;
import net.maidens.politics.Network;
import net.maidens.politics.Stance;
import net.maidens.village.Buildings;
import net.maidens.village.VillageMenu;
import net.maidens.village.VillageView;
import net.maidens.village.VillageRecord;
import net.maidens.village.Villages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * For every player, in the maiden village they stand in: /village (the village menu, same as ringing its bell),
 * /village build &lt;building&gt; (citizens only: the maidens build it nearby).
 */
public final class VillageCommand {
    private VillageCommand() {}

    /** The nearest maiden village (fortified, or any), from the seed: name, where, how far, and a click to go there. */
    private static int find(CommandSourceStack src, boolean walled) {
        ServerLevel level = src.getLevel();
        net.minecraft.world.phys.Vec3 at = src.getPosition();
        int x = (int) at.x, z = (int) at.z;
        KnownVillage k = Network.findNearest(level, x, z, 6000, id -> Villages.rollsMaidens(level, id)
                && (!walled || net.maidens.village.Fortify.rolls(level, id) || isFortified(level, id)));
        if (k == null) {
            src.sendFailure(Component.literal("No " + (walled ? "walled " : "") + "maiden village within 6000 blocks."));
            return 0;
        }
        int dist = (int) Math.round(Math.sqrt((double) (k.x - x) * (k.x - x) + (double) (k.z - z) * (k.z - z)));
        Component where = Component.literal("[" + k.x + ", ~, " + k.z + "]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + k.x + " ~ " + k.z))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to teleport (operators)"))));
        src.sendSuccess(() -> Component.literal("The nearest " + (walled ? "walled " : "") + "maiden village is " + k.name + " (" + k.style + ") at ")
                .append(where).append(" (" + dist + " blocks away)"), false);
        return 1;
    }

    private static boolean isFortified(ServerLevel level, long id) {
        VillageRecord v = Villages.get(level).byId(id);
        return v != null && v.wallBox != null;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("village")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    VillageRecord v = Villages.get(p.serverLevel()).at(p.blockPosition());
                    if (v == null) return none(c.getSource());
                    VillageView.open(p.serverLevel(), v, p);
                    return 1;
                })
                .then(Commands.literal("find")
                        .then(Commands.literal("walled").executes(c -> find(c.getSource(), true)))
                        .then(Commands.literal("maiden").executes(c -> find(c.getSource(), false))))
                .then(Commands.literal("chat").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    VillageRecord v = Villages.get(p.serverLevel()).at(p.blockPosition());
                    if (v == null) return none(c.getSource());
                    VillageMenu.show(p.serverLevel(), v, p);
                    return 1;
                }))
                .then(Commands.literal("stance").then(Commands.argument("village", StringArgumentType.word())
                        .then(Commands.argument("stance", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"trade", "neutral", "aggressive"}, b))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    ServerLevel level = p.serverLevel();
                                    VillageRecord v = Villages.get(level).at(p.blockPosition());
                                    if (v == null) return none(c.getSource());
                                    String name = StringArgumentType.getString(c, "village");
                                    KnownVillage other = null;
                                    for (KnownVillage k : Network.get(level).all()) if (k.name.equalsIgnoreCase(name)) other = k;
                                    Stance s;
                                    try {
                                        s = Stance.valueOf(StringArgumentType.getString(c, "stance").toUpperCase());
                                    } catch (IllegalArgumentException e) {
                                        s = null;
                                    }
                                    if (other == null || s == null) {
                                        c.getSource().sendFailure(Component.literal("Usage: /village stance <village> <trade|neutral|aggressive>"));
                                        return 0;
                                    }
                                    String reply = Diplomacy.setStance(level, v, p, other, s);
                                    c.getSource().sendSuccess(() -> Component.literal(reply), false);
                                    return 1;
                                }))))
                .then(Commands.literal("build").then(Commands.argument("building", StringArgumentType.word())
                        .suggests((c, b) -> {
                            if (c.getSource().getEntity() instanceof ServerPlayer p) {
                                VillageRecord v = Villages.get(p.serverLevel()).at(p.blockPosition());
                                if (v != null) return SharedSuggestionProvider.suggest(Buildings.templates(p.serverLevel(), v.style).stream().map(Buildings::shortName), b);
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ServerLevel level = p.serverLevel();
                            VillageRecord v = Villages.get(level).at(p.blockPosition());
                            if (v == null) return none(c.getSource());
                            String reply = VillageMenu.build(level, v, p, StringArgumentType.getString(c, "building"));
                            c.getSource().sendSuccess(() -> Component.literal(reply), false);
                            return 1;
                        }))));
    }

    private static int none(CommandSourceStack s) {
        s.sendFailure(Component.literal("You're not in a maiden village."));
        return 0;
    }
}

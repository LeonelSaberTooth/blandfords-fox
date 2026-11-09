package net.maidens.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.maidens.politics.Diplomacy;
import net.maidens.politics.KnownVillage;
import net.maidens.politics.Network;
import net.maidens.politics.Stance;
import net.maidens.village.Buildings;
import net.maidens.village.VillageMenu;
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

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("village")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    VillageRecord v = Villages.get(p.serverLevel()).at(p.blockPosition());
                    if (v == null) return none(c.getSource());
                    VillageMenu.show(p.serverLevel(), v, p);
                    return 1;
                })
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

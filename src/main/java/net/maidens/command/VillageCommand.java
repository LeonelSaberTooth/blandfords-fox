package net.maidens.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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

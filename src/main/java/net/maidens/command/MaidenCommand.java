package net.maidens.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.maidens.maiden.Maiden;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;

/**
 * Testing helpers (operators only), acting on the nearest maiden within 10 blocks:
 * /maiden info, /maiden affection &lt;0-100&gt;.
 */
public final class MaidenCommand {
    private MaidenCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("maiden").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Maiden m = nearest(p);
                    if (m == null) return fail(c.getSource());
                    c.getSource().sendSuccess(() -> Component.literal(m.describe(p)), false);
                    return 1;
                }))
                .then(Commands.literal("affection").then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Maiden m = nearest(p);
                    if (m == null) return fail(c.getSource());
                    m.relationships().setAffection(p, IntegerArgumentType.getInteger(c, "value"));
                    c.getSource().sendSuccess(() -> Component.literal(m.describe(p)), false);
                    return 1;
                }))));
    }

    private static Maiden nearest(ServerPlayer p) {
        return p.level().getEntitiesOfClass(Maiden.class, p.getBoundingBox().inflate(10)).stream()
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(p))).orElse(null);
    }

    private static int fail(CommandSourceStack s) {
        s.sendFailure(Component.literal("No maiden within 10 blocks."));
        return 0;
    }
}

package net.maidens.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.maidens.maiden.Maiden;
import net.maidens.village.VillageRecord;
import net.maidens.village.VillageSocial;
import net.maidens.village.Villages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;

/**
 * Testing helpers (operators only), acting on the nearest maiden within 10 blocks:
 * /maiden info, /maiden affection &lt;0-100&gt;; and for the maiden village you're standing in:
 * /maiden village (its name, your standing, its blueprint), /maiden village citizen (makes you a citizen).
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
                })))
                .then(Commands.literal("village").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ServerLevel level = p.serverLevel();
                    VillageRecord v = Villages.get(level).at(p.blockPosition());
                    if (v == null) return noVillage(c.getSource());
                    String s = v.name + ": " + (v.isCitizen(p.getUUID()) ? "you're a citizen" : "you're not a citizen")
                            + ", " + v.tradesBy(p.getUUID()) + " trades here, residents like you " + VillageSocial.averageAffection(level, v, p.getUUID())
                            + "/100 on average; blueprint " + v.blueprint.recordedPieces() + "/" + v.blueprint.pieceCount() + " pieces, "
                            + v.blueprint.blockCount() + " blocks";
                    c.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                }).then(Commands.literal("citizen").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    VillageRecord v = Villages.get(p.serverLevel()).at(p.blockPosition());
                    if (v == null) return noVillage(c.getSource());
                    VillageSocial.makeCitizen(p.serverLevel(), v, p);
                    return 1;
                }))));
    }

    private static Maiden nearest(ServerPlayer p) {
        return p.level().getEntitiesOfClass(Maiden.class, p.getBoundingBox().inflate(10)).stream()
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(p))).orElse(null);
    }

    private static int noVillage(CommandSourceStack s) {
        s.sendFailure(Component.literal("You're not in a maiden village."));
        return 0;
    }

    private static int fail(CommandSourceStack s) {
        s.sendFailure(Component.literal("No maiden within 10 blocks."));
        return 0;
    }
}

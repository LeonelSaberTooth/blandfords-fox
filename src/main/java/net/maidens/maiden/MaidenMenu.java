package net.maidens.maiden;

import net.maidens.village.VillageSocial;

import net.maidens.core.MaidensConfig;
import net.maidens.maiden.work.Job;
import net.maidens.network.ActionPayload;
import net.maidens.network.MenuPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server side of a maiden's right-click menu: opening it, and what each button does.
 * Every player interaction with a maiden goes through {@link #handle}.
 */
public final class MaidenMenu {
    private MaidenMenu() {}

    public static void open(Maiden m, ServerPlayer p) {
        m.startTalking(p);
        String hello = m.relationships().greeting(p);
        if (m.relationships().introduce(p)) {
            hello = "Hi, I'm " + m.name() + "! " + hello;
            m.smile();
        }
        send(m, p, hello);
    }

    /** A button from her menu (see {@link ActionPayload}). */
    public static void handle(Maiden m, ServerPlayer p, int action, int arg) {
        if (!m.isAlive() || p.distanceToSqr(m) > 10 * 10) return;
        m.startTalking(p);
        String reply = switch (action) {
            case ActionPayload.CHAT, ActionPayload.JOKE, ActionPayload.COMPLIMENT, ActionPayload.FLIRT -> m.relationships().talk(p, action);
            case ActionPayload.GIFT -> {
                String done = m.wishes().fulfil(p);      // what she asked for on the requests board
                yield done != null ? done : m.relationships().gift(p);
            }
            case ActionPayload.TRADE -> {
                String closed = m.trading().closed(p);
                if (closed != null) yield closed;
                m.trading().open(p);
                yield null;
            }
            case ActionPayload.FOLLOW -> m.toggleFollow(p);
            case ActionPayload.BAG -> m.employment().openBag(p);
            case ActionPayload.HIRE -> m.employment().hire(p, Job.byId(arg));
            case ActionPayload.STOP -> m.employment().stopBy(p);
            default -> VillageSocial.greeting(m, p);
        };
        if (reply != null) {
            m.say(p, reply);
            send(m, p, reply);
        }
    }

    public static void send(Maiden m, ServerPlayer p, String message) {
        var rel = m.relationships();
        var job = m.employment();
        // married to a player (you or someone else), or to another villager
        int married = rel.isMarried() ? (rel.isMarriedTo(p) ? 1 : 2) : m.spouse() != null ? 2 : 0;
        String spouse = !rel.isMarried() && m.spouse() != null ? m.spouseName() : "";
        PacketDistributor.sendToPlayer(p, new MenuPayload(m.getId(), m.name(), m.title(), rel.affection(p), married, spouse,
                job.job().ordinal(), job.shiftLeft(), job.employerFor(p), job.employerName(), job.canOpenBag(p), rel.isMarriedTo(p),
                MaidensConfig.RENT_EMERALDS.get(), m.isFollowing(p), message));
    }
}

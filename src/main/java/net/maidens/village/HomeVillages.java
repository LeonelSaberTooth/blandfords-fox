package net.maidens.village;

import net.maidens.politics.Diplomacy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A player's home village keeps going while they're away, like the world's spawn: the village they have the best
 * standing in (a leader's, else the one they've been a citizen of longest) stays loaded while they're on the
 * server, so its maidens work, build and grow whether they're there or not. Only the village itself is held
 * (its houses and a little around them), and only that one village per player.
 */
public final class HomeVillages {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("maidens_home_village", (a, b) -> Long.compare(a.toLong(), b.toLong()));
    private static final int MAX_RADIUS = 10;
    /** The villages held now, and the ticket each was given (centre and radius in chunks). */
    private static final Map<Long, int[]> HELD = new HashMap<>();

    private HomeVillages() {}

    /** The village a player calls home, or null. */
    public static VillageRecord homeOf(ServerLevel level, Villages villages, ServerPlayer p) {
        VillageRecord best = null;
        long bestScore = 0;
        for (VillageRecord v : villages.all()) {
            long s = Diplomacy.reputation(level, v, p.getUUID());
            if (s > bestScore) {
                bestScore = s;
                best = v;
            }
        }
        return best;
    }

    /** Every 5 s: holds the home villages of everyone online, lets go of the others. */
    static void tick(ServerLevel level, Villages villages) {
        Set<Long> want = new HashSet<>();
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            VillageRecord v = homeOf(level, villages, p);
            if (v != null) want.add(v.id);
        }
        for (var it = HELD.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (want.contains(e.getKey())) continue;
            int[] t = e.getValue();
            ChunkPos c = new ChunkPos(t[0], t[1]);
            level.getChunkSource().removeRegionTicket(TICKET, c, t[2], c);
            it.remove();
        }
        for (long id : want) {
            if (HELD.containsKey(id)) continue;
            VillageRecord v = villages.byId(id);
            if (v == null) continue;
            ChunkPos c = new ChunkPos(v.centre());
            int half = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 16;
            // a region ticket of radius r keeps entities ticking out to r - 2 chunks
            int r = Math.min(MAX_RADIUS, (half + 15) / 16 + 2);
            level.getChunkSource().addRegionTicket(TICKET, c, r, c);
            HELD.put(id, new int[]{c.x, c.z, r});
        }
    }

    /** The server stopped: nothing is held any more. */
    static void clear() {
        HELD.clear();
    }
}

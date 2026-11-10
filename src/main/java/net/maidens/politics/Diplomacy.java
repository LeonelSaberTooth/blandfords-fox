package net.maidens.politics;

import net.maidens.maiden.social.Marriages;
import net.maidens.village.VillageRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who may make a village's decisions towards its neighbours, and making them. A village's royals decide on their own
 * (its temperament); a player may decide if they're married to one of its royals, helped beat back a raid on it,
 * or have been its citizen for {@link #CITIZEN_DAYS} days.
 */
public final class Diplomacy {
    public static final int CITIZEN_DAYS = 14;

    private Diplomacy() {}

    /** Why this player may decide for the village, or null if they may not. */
    @Nullable
    public static String rightToDecide(ServerLevel level, VillageRecord v, Player p) {
        UUID spouse = Marriages.get(level.getServer()).spouse(p.getUUID());
        if (spouse != null && v.isRoyal(spouse)) return "you married its royal";
        if (v.isDefender(p.getUUID())) return "you saved it from a raid";
        long days = v.citizenDays(p.getUUID(), level.getDayTime() / 24000L);
        if (days >= CITIZEN_DAYS) return "you've been its citizen for " + days + " days";
        return null;
    }

    /** Where a player stands with a village: a visitor, a citizen, or a leader (married its royal, or saved it). */
    public enum Rank {
        NONE("Visitor"), CITIZEN("Citizen"), LEADER("Leader");

        public final String label;

        Rank(String label) {
            this.label = label;
        }
    }

    public static Rank rank(ServerLevel level, VillageRecord v, UUID player) {
        UUID spouse = Marriages.get(level.getServer()).spouse(player);
        if (spouse != null && v.isRoyal(spouse) || v.isDefender(player)) return Rank.LEADER;
        return v.isCitizen(player) ? Rank.CITIZEN : Rank.NONE;
    }

    /** How well a village knows a player: leaders first, then the longest citizens. 0 for strangers. */
    public static long reputation(ServerLevel level, VillageRecord v, UUID player) {
        Rank r = rank(level, v, player);
        if (r == Rank.NONE) return 0;
        return (r == Rank.LEADER ? 100_000 : 0) + 1 + Math.max(0, v.citizenDays(player, level.getDayTime() / 24000L));
    }

    /** What a player who may not decide would need. */
    public static String howToEarnIt(ServerLevel level, VillageRecord v, Player p) {
        long days = v.citizenDays(p.getUUID(), level.getDayTime() / 24000L);
        String citizen = days < 0 ? "become a citizen for " + CITIZEN_DAYS + " days" : "stay a citizen " + (CITIZEN_DAYS - days) + " more days";
        return "To speak for " + v.name + ": marry its royal, save it from a raid, or " + citizen + ".";
    }

    /** A player sets their village's stance towards another. Returns what to tell them. */
    public static String setStance(ServerLevel level, VillageRecord v, Player p, KnownVillage other, Stance s) {
        if (rightToDecide(level, v, p) == null) return howToEarnIt(level, v, p);
        Network n = Network.get(level);
        KnownVillage self = n.know(level, v);
        if (other.id == self.id || !other.maiden) return "That's not a village " + v.name + " can deal with.";
        Stance old = n.stance(self, other);
        if (old == s) return v.name + " is already " + s.label + " towards " + other.name + ".";
        n.setStance(self, other, s);
        String line = switch (s) {
            case TRADE -> v.name + " opened trade with " + other.name + ".";
            case NEUTRAL -> v.name + " is keeping to itself with " + other.name + ".";
            case AGGRESSIVE -> v.name + " turned hostile towards " + other.name + "!";
        };
        n.news(level, self.id, line);
        n.news(level, other.id, line);
        return line;
    }
}

package net.maidens.village;

import net.maidens.maiden.Maiden;
import net.maidens.maiden.work.Job;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Who lives in a maiden village: women, men, old women and men, and children (who grow up in a few days; nobody
 * else ages). Grown-ups marry each other (half of them, four in five of the old), and some are guards: one per gate
 * (or two by the bell), plus one more for every eight villagers.
 */
public final class People {
    private People() {}

    /** What a new villager is: 15% children, then women, men, old women, old men. */
    public static void roll(Maiden m, RandomSource r) {
        double x = r.nextDouble();
        if (x < 0.15) {
            m.setKind(r.nextBoolean() ? Maiden.Kind.MAN : Maiden.Kind.WOMAN);
            m.makeChild();
        } else if (x < 0.58) m.setKind(Maiden.Kind.WOMAN);
        else if (x < 0.85) m.setKind(Maiden.Kind.MAN);
        else if (x < 0.93) m.setKind(Maiden.Kind.OLD_WOMAN);
        else m.setKind(Maiden.Kind.OLD_MAN);
    }

    /** Once a minute while the village is loaded: weddings, and guards. */
    static void tick(ServerLevel level, VillageRecord v, List<Maiden> residents) {
        marry(level, v, residents);
        guards(v, residents);
        jobs(level, v, residents);
    }

    /** How the last job search went (for the self test). */
    public static String lastJobs = "";

    /**
     * Work for the village: woodcutters (one for every eight villagers), miners (one for every ten) and, with water
     * nearby, fishers (one for every twelve), from the grown-ups who aren't guards, royals or hired. One more a minute.
     */
    public static void jobs(ServerLevel level, VillageRecord v, List<Maiden> residents) {
        int n = residents.size();
        int wood = 0, mine = 0, fish = 0;
        for (Maiden m : residents) {
            if (!m.employment().forVillage()) continue;
            switch (m.employment().job()) {
                case WOOD -> wood++;
                case MINE -> mine++;
                case FISH -> fish++;
                default -> {}
            }
        }
        lastJobs = n + " residents: " + wood + " woodcutters, " + mine + " miners, " + fish + " fishers";
        List<Job> wanted = new ArrayList<>();
        if (wood < Math.max(1, n / 8)) wanted.add(Job.WOOD);
        if (mine < Math.max(1, n / 10)) wanted.add(Job.MINE);
        if (fish < Math.max(1, n / 12)) wanted.add(Job.FISH);
        if (wanted.isEmpty()) return;
        Maiden pick = null;
        for (Maiden m : residents) {
            if (m.isBaby() || m.isOld() || m.isGuard() || m.isRoyal() || !m.hasVillageDuties() || m.employment().isWorking()) continue;
            // the ones with no trade of their own first; farmers stay in the fields
            var prof = m.profession();
            if (prof == net.minecraft.world.entity.npc.VillagerProfession.FARMER) continue;
            if (pick == null || prof == null && pick.profession() != null) pick = m;
        }
        if (pick == null) return;
        RandomSource r = level.getRandom();
        // the first job there's a place for (no trees nearby: no woodcutter, and so on)
        Job want = null;
        net.minecraft.core.BlockPos at = null;
        for (Job j : wanted) {
            at = switch (j) {
                case WOOD -> WorkSites.forest(level, v, r);
                case MINE -> WorkSites.mine(level, v, r);
                case FISH -> WorkSites.water(level, v, r);
                default -> null;
            };
            if (at != null) {
                want = j;
                break;
            }
            lastJobs += "; no spot for a " + j.button.toLowerCase();
        }
        if (want == null) return;
        net.minecraft.core.Direction dir = WorkSites.away(v, at);
        pick.employment().startForVillage(want, at, dir);
        lastJobs += "; " + pick.name() + " is now a " + want.button.toLowerCase() + " at " + at.toShortString();
    }

    private static boolean free(Maiden m) {
        return !m.isBaby() && m.spouse() == null && !m.relationships().isMarried();
    }

    private static void marry(ServerLevel level, VillageRecord v, List<Maiden> residents) {
        RandomSource r = level.getRandom();
        List<Maiden> looking = new ArrayList<>();
        for (Maiden m : residents) {
            if (!free(m)) continue;
            if (m.marriageState() == 0) m.setMarriageState(r.nextDouble() < (m.isOld() ? 0.8 : 0.5) ? 2 : 1);
            if (m.marriageState() == 2) looking.add(m);
        }
        for (Maiden a : looking) {
            if (!free(a)) continue;
            for (Maiden b : looking) {
                if (b == a || !free(b) || b.isMale() == a.isMale() || b.isOld() != a.isOld()) continue;
                a.setSpouse(b.getUUID());
                b.setSpouse(a.getUUID());
                net.maidens.politics.Network.get(level).news(level, v.id, a.name() + " and " + b.name() + " got married.");
                break;
            }
        }
    }

    private static void guards(VillageRecord v, List<Maiden> residents) {
        int want = Math.max(2, v.gates.size()) + residents.size() / 8;
        int have = 0;
        for (Maiden m : residents) {
            if (!m.isGuard()) continue;
            have++;
            if (m.hasVillageDuties() && m.getMainHandItem().isEmpty()) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }
        for (Maiden m : residents) {
            if (have >= want) break;
            if (m.isGuard() || m.isBaby() || m.isOld() || m.isRoyal() || !m.hasVillageDuties()) continue;
            m.setGuard(true);
            m.setGuardPost(have);
            // a sword and a helmet (her own, not from a bag)
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            have++;
        }
    }
}

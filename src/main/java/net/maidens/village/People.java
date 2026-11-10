package net.maidens.village;

import net.maidens.maiden.Maiden;
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

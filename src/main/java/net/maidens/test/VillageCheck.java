package net.maidens.test;

import com.mojang.datafixers.util.Pair;
import net.maidens.maiden.Maiden;
import net.maidens.maiden.home.Residence;
import net.maidens.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The self test's village part (run with every village set to be a maiden village): finds the nearest village,
 * loads it, and checks that its villagers became maidens who claim beds and workstations and trade by profession.
 */
final class VillageCheck {
    private final Consumer<String> log;
    private BlockPos village;

    VillageCheck(Consumer<String> log) {
        this.log = log;
    }

    /** One tick of the check, t ticks after it started; true when done. */
    boolean step(ServerLevel level, BlockPos from, int t) {
        if (t == 0) {
            HolderSet<Structure> villages = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(StructureTags.VILLAGE).orElseThrow();
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, villages, from, 100, false);
            if (found == null) {
                log.accept("village: NONE FOUND within 100 chunks");
                return true;
            }
            village = found.getFirst();
            log.accept("village: " + found.getSecond().unwrapKey().map(k -> k.location().getPath()).orElse("?") + " at " + village.toShortString());
            for (int cx = (village.getX() >> 4) - 5; cx <= (village.getX() >> 4) + 5; cx++)
                for (int cz = (village.getZ() >> 4) - 5; cz <= (village.getZ() >> 4) + 5; cz++) {
                    level.setChunkForced(cx, cz, true);
                    level.getChunk(cx, cz);
                }
            return false;
        }
        if (t == 100 || t == 600) {
            AABB area = new AABB(village).inflate(96, 64, 96);
            List<Maiden> maidens = level.getEntitiesOfClass(Maiden.class, area);
            List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
            int inVillage = 0, beds = 0, sites = 0;
            TreeMap<String, Integer> jobs = new TreeMap<>();
            for (Maiden m : maidens) {
                Residence r = m.residence();
                if (r.inVillage()) inVillage++;
                if (r.bed() != null) beds++;
                if (r.jobSite() != null) sites++;
                jobs.merge(r.professionName().isEmpty() ? "none" : r.professionName(), 1, Integer::sum);
            }
            log.accept("  after " + t + " ticks: " + maidens.size() + " maidens (" + inVillage + " know their village), "
                    + villagers.size() + " villagers; " + beds + " have a bed, " + sites + " a workstation; professions " + jobs);
            log.accept("  maiden villages on record: " + count(Villages.get(level).all()));
            if (t == 600) {
                for (Maiden m : maidens) {
                    if (m.residence().profession() == null) continue;
                    MerchantOffer o = m.getOffers().isEmpty() ? null : m.getOffers().get(0);
                    log.accept("  " + m.name() + ", " + m.title() + ", trades as " + m.trading().profession()
                            + (o == null ? ", no offers" : ", first offer " + o.getCostA().getCount() + " " + o.getCostA().getItem() + " -> " + o.getResult().getCount() + " " + o.getResult().getItem())
                            + ", bed " + (m.residence().bed() == null ? "none" : m.residence().bed().toShortString()));
                    break;
                }
                return true;
            }
        }
        return false;
    }

    private static int count(Iterable<?> it) {
        int n = 0;
        for (Object o : it) n++;
        return n;
    }
}

package net.maidens.village;

import net.maidens.maiden.Maiden;
import net.maidens.politics.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A maiden village's royals: they make its decisions towards other villages, wear a crown, and don't take hired
 * work. A village has one for now (the record keeps a set, so a royal family can come later); when there's
 * none, one of its maidens is crowned.
 */
public final class Royals {
    /** How many royals a village has. */
    public static final int PER_VILLAGE = 1;

    private Royals() {}

    /** Once a minute while the village is loaded: crowns someone if it needs a royal, and keeps the crowns right. */
    public static void tick(ServerLevel level, VillageRecord v, List<Maiden> residents) {
        for (Maiden m : residents) {
            boolean royal = v.isRoyal(m.getUUID());
            if (royal != m.isRoyal()) m.setRoyal(royal);
            if (royal) moveIntoCastle(level, v, m);
        }
        if (v.royals.size() >= PER_VILLAGE || residents.isEmpty()) return;
        // prefer someone free (not hired, not married to a player)
        List<Maiden> choice = new ArrayList<>();
        for (Maiden m : residents) if (!m.isRoyal() && !m.employment().isHired() && !m.relationships().isMarried()) choice.add(m);
        if (choice.isEmpty()) for (Maiden m : residents) if (!m.isRoyal()) choice.add(m);
        if (choice.isEmpty()) return;
        crown(level, v, choice.get(level.getRandom().nextInt(choice.size())));
    }

    /** A royal sleeps in the castle (a free bed there), once it has one. */
    private static void moveIntoCastle(ServerLevel level, VillageRecord v, Maiden m) {
        BoundingBox c = v.castle;
        if (c == null || !level.hasChunksAt(c.minX(), c.minZ(), c.maxX(), c.maxZ())) return;
        BlockPos bed = m.residence().bed();
        if (bed != null && c.isInside(bed)) return;
        BlockPos centre = c.getCenter();
        level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), c::isInside, centre, 24, PoiManager.Occupancy.HAS_SPACE)
                .findFirst().ifPresent(p -> m.residence().claimBed(level, p));
    }

    public static void crown(ServerLevel level, VillageRecord v, Maiden m) {
        v.royals.add(m.getUUID());
        m.setRoyal(true);
        m.employment().stop();
        Villages.get(level).setDirty();
        Network.get(level).news(level, v.id, m.name() + " was crowned the royal of " + v.name + ".");
    }

    /** A royal died: her village will crown someone else. */
    public static void onDeath(Maiden m) {
        if (!m.isRoyal() || !(m.level() instanceof ServerLevel level)) return;
        VillageRecord v = VillageSocial.villageOf(m);
        if (v == null || !v.royals.remove(m.getUUID())) return;
        Villages.get(level).setDirty();
        Network.get(level).news(level, v.id, "The royal " + m.name() + " has died. " + v.name + " mourns.");
    }
}

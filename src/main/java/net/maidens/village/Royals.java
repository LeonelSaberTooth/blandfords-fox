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
        // a child isn't royal (from before that was checked): someone else is crowned
        for (Maiden m : residents)
            if (m.isBaby() && v.royals.remove(m.getUUID())) {
                m.setRoyal(false);
                Villages.get(level).setDirty();
            }
        for (Maiden m : residents) {
            boolean royal = v.isRoyal(m.getUUID());
            if (royal != m.isRoyal()) m.setRoyal(royal);
            if (royal) moveIntoCastle(level, v, m);
        }
        if (v.royals.size() >= PER_VILLAGE || residents.isEmpty()) return;
        // prefer someone free (not hired, not married to a player)
        List<Maiden> choice = new ArrayList<>();
        for (Maiden m : residents) if (!m.isRoyal() && !m.isBaby() && !m.employment().isHired() && !m.relationships().isMarried()) choice.add(m);
        if (choice.isEmpty()) for (Maiden m : residents) if (!m.isRoyal() && !m.isBaby()) choice.add(m);
        if (choice.isEmpty()) return;
        crown(level, v, choice.get(level.getRandom().nextInt(choice.size())));
    }

    /**
     * A royal sleeps in the castle, once it has a bed, and so does her (or his) husband or wife: anyone else sleeping
     * there is moved out first (they find a bed elsewhere).
     */
    private static void moveIntoCastle(ServerLevel level, VillageRecord v, Maiden m) {
        BoundingBox c = v.castle;
        if (c == null || !level.hasChunksAt(c.minX(), c.minZ(), c.maxX(), c.maxZ())) return;
        List<Maiden> family = new ArrayList<>(List.of(m));
        if (m.spouse() != null && level.getEntity(m.spouse()) instanceof Maiden s && s.isAlive()) family.add(s);
        for (Maiden who : family) {
            BlockPos bed = who.residence().bed();
            if (bed != null && c.isInside(bed)) continue;
            BlockPos centre = c.getCenter();
            var free = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), c::isInside, centre, 32, PoiManager.Occupancy.HAS_SPACE).findFirst();
            if (free.isEmpty()) {
                // someone else has it: out they go
                for (Maiden o : VillageSocial.everyone(level, v)) {
                    BlockPos ob = o.residence().bed();
                    if (ob != null && c.isInside(ob) && !family.contains(o) && !o.isRoyal()) {
                        o.residence().leaveBed(level);
                        free = java.util.Optional.of(ob);
                        break;
                    }
                }
            }
            free.ifPresent(p -> who.residence().claimBed(level, p));
        }
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

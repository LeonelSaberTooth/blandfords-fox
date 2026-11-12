package net.maidens.village;

import net.maidens.core.MaidensConfig;
import net.maidens.core.ModEntities;
import net.maidens.maiden.Maiden;
import net.maidens.politics.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A maiden village's growth, checked once a minute while it's loaded:
 * <ul>
 *   <li>a resident who still has no bed gets a new house (one of the village style's houses with a bed),
 *       built by the maidens, with a road to it; one building at a time;</li>
 *   <li>a free bed brings a new maiden, walking in along a road, at most one a day.</li>
 * </ul>
 */
public final class Housing {
    private static final int MAX_RESIDENTS = 60, DAY = 24000, SETTLE_TICKS = 1200, ARRIVE_EVERY = 2400;

    /** When each village was first seen loaded (this session). */
    private static final java.util.Map<Long, Long> SEEN = new java.util.HashMap<>();

    private Housing() {}

    static void tick(ServerLevel level, Villages villages) {
        for (VillageRecord v : villages.all()) {
            // only while the whole village is loaded (otherwise beds and residents out of reach look missing)
            BlockPos c = v.centre();
            int r = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 48;
            if (!level.hasChunksAt(c.getX() - r, c.getZ() - r, c.getX() + r, c.getZ() + r)) continue;
            List<Maiden> residents = VillageSocial.residents(level, v);
            if (residents.isEmpty()) continue;
            // (and those out at work)
            List<Maiden> everyone = VillageSocial.everyone(level, v);
            v.population = everyone.size();
            Royals.tick(level, v, residents);
            People.tick(level, v, everyone);
            Economy.tick(level, v, everyone);
            // its neighbours, looked up from the seed a few at a time
            Network network = Network.get(level);
            network.know(level, v);
            network.lookAround(level, c.getX(), c.getZ(), Network.NEIGHBOUR_RANGE);
            // a village's houses are lived in: every free bed gets someone (once, when it's first seen; after that
            // newcomers arrive one at a time)
            int bedless = 0, settling = 0;
            for (Maiden m : residents) {
                if (m.residence().bed() == null && m.tickCount > SETTLE_TICKS) bedless++;
                if (m.residence().bed() == null) settling++;
            }
            // (once everyone already there has found a bed, a couple of minutes after it's first seen)
            long seen = SEEN.computeIfAbsent(v.id, k -> level.getGameTime());
            if (!v.peopled && settling == 0 && level.getGameTime() - seen > 2400) {
                v.peopled = true;
                fill(level, v);
                Villages.get(level).setDirty();
            }
            if (bedless > 0) {
                if (MaidensConfig.HOUSING.get() && !v.blueprint.isBuilding()) houseFor(level, v);
            } else if (residents.size() < MAX_RESIDENTS && level.getGameTime() - v.lastArrival >= ARRIVE_EVERY && arrive(level, v) != null) {
                // a free bed: someone new moves in (every couple of minutes while there are beds free)
            } else if (residents.size() < MAX_RESIDENTS && level.getGameTime() - v.lastArrival >= DAY && Economy.food(v) >= Economy.NEWCOMER_FOOD) {
                // no bed free: if there's food to spare and the logs and stone, the village builds a house for more
                if (MaidensConfig.HOUSING.get() && !v.blueprint.isBuilding() && Economy.canBuildHouse(v) && houseFor(level, v) != null) {
                    v.lastArrival = level.getGameTime();
                    Economy.payForHouse(v);
                    network.news(level, v.id, v.name + " is growing: a new house is going up.");
                }
            }
        }
    }

    /** Starts a house with a bed near the village centre. Returns what's being built, or null. */
    @Nullable
    public static Buildings.Plan houseFor(ServerLevel level, VillageRecord v) {
        List<ResourceLocation> houses = new ArrayList<>();
        for (ResourceLocation t : Buildings.templates(level, v.style)) if (Buildings.beds(level, t) > 0) houses.add(t);
        if (houses.isEmpty()) return null;
        ResourceLocation pick = houses.get(level.getRandom().nextInt(houses.size()));
        return Buildings.build(level, v, pick, homeArea(level, v));
    }

    /** Where the residents live: the middle of their beds (a village on a hillside may have its centre up a slope). */
    private static BlockPos homeArea(ServerLevel level, VillageRecord v) {
        long x = 0, y = 0, z = 0;
        int n = 0;
        for (Maiden m : VillageSocial.residents(level, v)) {
            BlockPos b = m.residence().bed();
            if (b == null) continue;
            x += b.getX();
            y += b.getY();
            z += b.getZ();
            n++;
        }
        return n == 0 ? v.centre() : new BlockPos((int) (x / n), (int) (y / n), (int) (z / n));
    }

    /**
     * Someone for every free bed in the village (not the castle's): in a house with two or more free beds, a married
     * couple first; she appears beside her bed. Returns how many came.
     */
    public static int fill(ServerLevel level, VillageRecord v) {
        PoiManager poi = level.getPoiManager();
        int radius = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 48;
        BlockPos c = v.centre();
        List<BlockPos> beds = poi.findAll(h -> h.is(PoiTypes.HOME), p -> v.castle == null || !v.castle.isInside(p), c, radius, PoiManager.Occupancy.HAS_SPACE)
                .sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).toList();
        java.util.Map<net.minecraft.world.level.levelgen.structure.BoundingBox, List<BlockPos>> houses = new java.util.LinkedHashMap<>();
        for (BlockPos b : beds) houses.computeIfAbsent(People.houseOf(v, b), k -> new ArrayList<>()).add(b);
        var r = level.getRandom();
        int came = 0;
        for (List<BlockPos> house : houses.values()) {
            Maiden first = null;
            for (int i = 0; i < house.size(); i++) {
                Maiden m = ModEntities.MAIDEN.get().create(level);
                if (m == null) return came;
                BlockPos bed = house.get(i);
                m.moveTo(bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 0.5, r.nextFloat() * 360, 0);
                m.ensureInitialised();
                if (i == 0 && house.size() >= 2) m.setKind(r.nextBoolean() ? Maiden.Kind.MAN : Maiden.Kind.WOMAN);
                else if (i == 1 && first != null) m.setKind(first.isMale() ? Maiden.Kind.WOMAN : Maiden.Kind.MAN);
                else People.roll(m, r);
                m.residence().joinVillage(v.id);
                level.addFreshEntity(m);
                m.residence().claimBed(level, bed);
                if (i == 0) first = m;
                if (i == 1 && first != null) {
                    m.setSpouse(first);
                    first.setSpouse(m);
                    m.setMarriageState(2);
                    first.setMarriageState(2);
                }
                came++;
            }
        }
        return came;
    }

    /** A new maiden moves in, if there's a free bed: she appears on a road near it. Returns her, or null. */
    @Nullable
    public static Maiden arrive(ServerLevel level, VillageRecord v) {
        PoiManager poi = level.getPoiManager();
        int radius = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 48;
        BlockPos c = v.centre();
        if (!level.hasChunksAt(c.getX() - radius, c.getZ() - radius, c.getX() + radius, c.getZ() + radius)) return null;
        Optional<BlockPos> bed = poi.findClosest(h -> h.is(PoiTypes.HOME), p -> v.castle == null || !v.castle.isInside(p), v.centre(), radius, PoiManager.Occupancy.HAS_SPACE);
        if (bed.isEmpty()) return null;
        BlockPos spot = roadNear(v, bed.get());
        Maiden m = ModEntities.MAIDEN.get().create(level);
        if (m == null) return null;
        m.moveTo(spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        m.ensureInitialised();
        People.roll(m, level.getRandom());
        m.residence().joinVillage(v.id);
        level.addFreshEntity(m);
        v.lastArrival = level.getGameTime();
        Villages.get(level).setDirty();
        return m;
    }

    /** The road block farthest out from the village centre within 32 blocks of the bed (as if she walked in), or the bed. */
    private static BlockPos roadNear(VillageRecord v, BlockPos bed) {
        BlockPos[] best = {null};
        double[] far = {-1};
        BlockPos c = v.centre();
        v.blueprint.forEachBlock((p, s) -> {
            if (!s.is(Upkeep.ROAD) || p.distSqr(bed) > 32 * 32) return;
            double d = p.distSqr(c);
            if (d > far[0]) {
                far[0] = d;
                best[0] = p;
            }
        });
        return best[0] != null ? best[0] : bed;
    }
}

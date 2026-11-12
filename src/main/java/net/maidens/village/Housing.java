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
    private static final int MAX_RESIDENTS = 60, DAY = 24000, SETTLE_TICKS = 1200;

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
            int bedless = 0;
            for (Maiden m : residents) if (m.residence().bed() == null && m.tickCount > SETTLE_TICKS) bedless++;
            if (bedless > 0) {
                if (MaidensConfig.HOUSING.get() && !v.blueprint.isBuilding()) houseFor(level, v);
            } else if (residents.size() < MAX_RESIDENTS && level.getGameTime() - v.lastArrival >= DAY && Economy.food(v) >= Economy.NEWCOMER_FOOD) {
                // someone new moves in if there's food to spare: into a free bed, or (with no bed free) the village
                // builds a house for her, if it has the logs and stone
                if (arrive(level, v) != null) v.take(Economy.FOOD, Economy.NEWCOMER_FOOD);
                else if (MaidensConfig.HOUSING.get() && !v.blueprint.isBuilding() && Economy.canBuildHouse(v) && houseFor(level, v) != null) {
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

    /** A new maiden moves in, if there's a free bed: she appears on a road near it. Returns her, or null. */
    @Nullable
    public static Maiden arrive(ServerLevel level, VillageRecord v) {
        PoiManager poi = level.getPoiManager();
        int radius = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 48;
        BlockPos c = v.centre();
        if (!level.hasChunksAt(c.getX() - radius, c.getZ() - radius, c.getX() + radius, c.getZ() + radius)) return null;
        Optional<BlockPos> bed = poi.findClosest(h -> h.is(PoiTypes.HOME), v.centre(), radius, PoiManager.Occupancy.HAS_SPACE);
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

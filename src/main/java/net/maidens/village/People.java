package net.maidens.village;

import net.maidens.maiden.Maiden;
import net.maidens.maiden.work.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Who lives in a maiden village: women, men, old women and men, and children (who grow up in a few days; nobody
 * else ages). Grown-ups marry each other (half of them, four in five of the old), and the grown men and women all
 * have a job for the village ({@link #jobs}).
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

    /** Once a minute while the village is loaded: weddings, and jobs. */
    static void tick(ServerLevel level, VillageRecord v, List<Maiden> residents) {
        marry(level, v, residents);
        together(level, v, residents);
        jobs(level, v, residents);
    }

    /** The building a bed is in (a piece of the village's blueprint), or just the bed's spot. */
    static net.minecraft.world.level.levelgen.structure.BoundingBox houseOf(VillageRecord v, BlockPos bed) {
        for (Blueprint.PieceInfo p : v.blueprint.pieces())
            if (p.box().isInside(bed) && !p.name().equals("road") && !p.name().equals(Walls.NAME) && !p.name().equals(Farms.NAME)) return p.box();
        return new net.minecraft.world.level.levelgen.structure.BoundingBox(bed).inflatedBy(6);
    }

    /**
     * A married couple sleeps in the same house: one moves into a free bed in the other's house, or swaps beds with
     * someone single who sleeps there.
     */
    private static void together(ServerLevel level, VillageRecord v, List<Maiden> residents) {
        for (Maiden a : residents) {
            if (a.spouse() == null || a.residence().bed() == null) continue;
            Maiden b = null;
            for (Maiden o : residents) if (o.getUUID().equals(a.spouse())) b = o;
            if (b == null || b.residence().bed() == null) continue;
            var house = houseOf(v, b.residence().bed());
            if (house.isInside(a.residence().bed())) continue;
            if (a.isRoyal() || b.isRoyal()) continue;          // (the royals have the castle)
            // a free bed in the house
            var free = level.getPoiManager().findAll(h -> h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), house::isInside,
                    house.getCenter(), 24, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE).findFirst();
            if (free.isPresent()) {
                a.residence().claimBed(level, free.get());
                continue;
            }
            // or someone single sleeping there moves to her bed
            for (Maiden c : residents) {
                BlockPos cb = c.residence().bed();
                if (c == a || c == b || cb == null || !house.isInside(cb) || c.spouse() != null || c.isRoyal()) continue;
                a.residence().swapBeds(level, c.residence());
                break;
            }
        }
    }

    /** How the last job search went (for the self test). */
    public static String lastJobs = "";

    /** The other jobs, and how many of each (to every guard there's this many farmers, woodcutters...). */
    private static final Job[] TRADES = {Job.FARM, Job.WOOD, Job.MINE, Job.FISH};
    private static final int[] SHARE = {4, 2, 2, 1};
    private static final int PER_MINUTE = 6;

    /** Can work for the village: a grown man or woman living there, not royal, not married to a player. */
    public static boolean worker(Maiden m) {
        return net.maidens.maiden.home.Schedule.adult(m) && !m.isRoyal() && m.residence().inVillage() && !m.relationships().isMarried();
    }

    /**
     * Every grown man and woman works for the village. Guards first: one per gate (or two), and one more for every
     * eight people. The rest farm, cut wood, mine or fish, four farmers to two woodcutters, two miners and a fisher,
     * as far as there are fields, woods, rock and water for them (a few more each minute).
     */
    public static void jobs(ServerLevel level, VillageRecord v, List<Maiden> residents) {
        int n = residents.size();
        int[] count = new int[TRADES.length];
        int guards = 0, idle = 0;
        List<Maiden> free = new ArrayList<>();
        for (Maiden m : residents) {
            if (!worker(m)) continue;
            Job j = m.villageWork().job();
            if (j == Job.GUARD) guards++;
            for (int i = 0; i < TRADES.length; i++) if (j == TRADES[i]) count[i]++;
            if (j == Job.NONE) free.add(m);
        }
        RandomSource r = level.getRandom();
        int want = Math.max(2, v.gates.size()) + n / 8, given = 0;
        boolean[] none = new boolean[TRADES.length];
        StringBuilder log = new StringBuilder();
        for (Maiden m : free) {
            if (given >= PER_MINUTE) break;
            if (guards < want) {
                m.villageWork().set(Job.GUARD, null, guards++);
                given++;
                continue;
            }
            // the job furthest behind its share that has somewhere to do it
            BlockPos at = null;
            int pick = -1;
            while (at == null) {
                pick = -1;
                for (int i = 0; i < TRADES.length; i++) {
                    if (none[i]) continue;
                    if (pick < 0 || count[i] * SHARE[pick] < count[pick] * SHARE[i]) pick = i;
                }
                if (pick < 0) break;
                at = switch (TRADES[pick]) {
                    case FARM -> Farms.spot(level, v, r);
                    case WOOD -> WorkSites.forest(level, v, r);
                    case MINE -> WorkSites.mine(level, v, r);
                    case FISH -> WorkSites.water(level, v, r);
                    default -> null;
                };
                if (at == null) {
                    none[pick] = true;
                    log.append("; no spot for a ").append(TRADES[pick].button.toLowerCase());
                }
            }
            if (at == null) {
                idle++;
                continue;
            }
            m.villageWork().set(TRADES[pick], at, 0);
            if (TRADES[pick] == Job.WOOD) m.villageWork().setLog(WorkSites.logNear(level, at));
            count[pick]++;
            given++;
            log.append("; ").append(m.name()).append(" is now a ").append(TRADES[pick].button.toLowerCase()).append(" at ").append(at.toShortString());
        }
        // no one new to give a job to: one worker a minute moves to the job furthest behind its share, from the one
        // furthest ahead (fields laid out after everyone found work, say)
        if (given == 0 && free.isEmpty()) {
            int behind = -1, ahead = -1;
            for (int i = 0; i < TRADES.length; i++) {
                if (none[i]) continue;
                if (behind < 0 || count[i] * SHARE[behind] < count[behind] * SHARE[i]) behind = i;
                if (ahead < 0 || count[i] * SHARE[ahead] > count[ahead] * SHARE[i]) ahead = i;
            }
            if (behind >= 0 && ahead >= 0 && behind != ahead && (count[ahead] - 1) * SHARE[behind] >= (count[behind] + 1) * SHARE[ahead]) {
                BlockPos at = switch (TRADES[behind]) {
                    case FARM -> Farms.spot(level, v, r);
                    case WOOD -> WorkSites.forest(level, v, r);
                    case MINE -> WorkSites.mine(level, v, r);
                    case FISH -> WorkSites.water(level, v, r);
                    default -> null;
                };
                for (Maiden m : residents) {
                    if (at == null || !worker(m) || m.villageWork().job() != TRADES[ahead] || m.employment().isHired()) continue;
                    m.villageWork().set(TRADES[behind], at, 0);
                    if (TRADES[behind] == Job.WOOD) m.villageWork().setLog(WorkSites.logNear(level, at));
                    count[ahead]--;
                    count[behind]++;
                    log.append("; ").append(m.name()).append(" moves from ").append(TRADES[ahead].button.toLowerCase()).append(" to ").append(TRADES[behind].button.toLowerCase());
                    break;
                }
            }
        }
        lastJobs = n + " residents: " + guards + " guards, " + count[0] + " farmers, " + count[1] + " woodcutters, " + count[2] + " miners, "
                + count[3] + " fishers" + (idle > 0 ? ", " + idle + " with nothing to do" : "") + log;
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
                a.setSpouse(b);
                b.setSpouse(a);
                net.maidens.politics.Network.get(level).news(level, v.id, a.name() + " and " + b.name() + " got married.");
                break;
            }
        }
    }

}

package net.maidens.maiden.home;

import net.maidens.maiden.Maiden;
import net.maidens.village.VillageRecord;
import net.maidens.village.VillageSocial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * A village resident's day (game time 0 is 6:00).
 * <ul>
 * <li>Grown men and women: up and at home 6-8 (in casual clothes), at work 8-15 (work clothes), at the market by the
 * bell 15-19 (their dress, or their usual clothes), home again 19-21 (casual), asleep 21-6 (casual).</li>
 * <li>Children: home, out playing round the village 8-19, home; the old: home, the market 8-19, home. Neither changes
 * clothes.</li>
 * </ul>
 * A maiden married to a player, off on a raid or following someone has a day of her own. One hired by a player works
 * for her instead, but still goes home to sleep at night.
 */
public final class Schedule {
    public enum Activity {
        HOME(3), WORK(5), MARKET(3), OUTSIDE(4), SLEEP(1);

        /** How close to the activity's place counts as "there". */
        public final int near;

        Activity(int near) {
            this.near = near;
        }
    }

    /** The day's hours, in game time: 8:00, 15:00, 19:00, 21:00. */
    public static final int WORK_FROM = 2000, MARKET_FROM = 9000, HOME_FROM = 13000, SLEEP_FROM = 15000;

    private Schedule() {}

    /** What her kind of people do at this time of day. */
    public static Activity now(Maiden m) {
        long t = m.level().getDayTime() % 24000L;
        if (t >= SLEEP_FROM) return Activity.SLEEP;
        if (t < WORK_FROM || t >= HOME_FROM) return Activity.HOME;
        if (m.isBaby()) return Activity.OUTSIDE;
        if (m.isOld() || m.isRoyal()) return Activity.MARKET;
        return t < MARKET_FROM ? Activity.WORK : Activity.MARKET;
    }

    /** A grown man or woman (not old, not a child): the ones who work and change clothes. */
    public static boolean adult(Maiden m) {
        return !m.isBaby() && !m.isOld();
    }

    /** Her day is the village's: a resident, not married to a player, not on a raid. */
    public static boolean resident(Maiden m) {
        return m.residence().inVillage() && !m.relationships().isMarried() && !m.raider().active();
    }

    /** Bedtime for a resident, hired or not (hiring doesn't take her sleep). */
    public static boolean bedtime(Maiden m) {
        return resident(m) && m.residence().bed() != null && now(m) == Activity.SLEEP;
    }

    /** She's keeping to her day right now (bedtime always; otherwise when she isn't hired or following anyone). */
    public static boolean keeps(Maiden m) {
        if (!resident(m)) return false;
        if (now(m) == Activity.SLEEP) return true;
        return !m.employment().isHired() && !m.isFollowing();
    }

    /** What she's doing, if she's keeping to her day; else null. */
    @Nullable
    public static Activity activity(Maiden m) {
        return keeps(m) ? now(m) : null;
    }

    /** Where she should be for an activity, or null to just potter about. */
    @Nullable
    public static BlockPos place(Maiden m, Activity a) {
        Residence r = m.residence();
        VillageRecord v = VillageSocial.villageOf(m);
        return switch (a) {
            case HOME, SLEEP -> r.bed();
            case WORK -> v == null ? null : m.villageWork().place(v);
            case MARKET -> v == null ? null : market(m, v);
            case OUTSIDE -> v == null ? null : playing(m, v);
        };
    }

    /** Her own place in the crowd at the market: round the bell, 3 to 9 blocks out. */
    @Nullable
    public static BlockPos market(Maiden m, VillageRecord v) {
        BlockPos bell = v.bell() != null ? v.bell() : v.centre();
        int h = m.getUUID().hashCode();
        double a = (h & 1023) / 1024.0 * Math.PI * 2, d = 3 + (h >>> 10 & 15) * 6 / 15.0;
        return around(m, bell, a, d);
    }

    /** Where a child is playing: somewhere round the bell, a new spot every hour or so. */
    @Nullable
    public static BlockPos playing(Maiden m, VillageRecord v) {
        BlockPos bell = v.bell() != null ? v.bell() : v.centre();
        long hour = m.level().getDayTime() / 1000;
        long h = m.getUUID().getLeastSignificantBits() * 31 + hour * 0x9E3779B97F4A7C15L;
        double a = (h & 1023) / 1024.0 * Math.PI * 2, d = 4 + (h >>> 10 & 15);
        return around(m, bell, a, d);
    }

    /** The ground at a direction and distance from a spot (the spot itself if that's up a roof or down a hole). */
    private static BlockPos around(Maiden m, BlockPos from, double a, double d) {
        int x = from.getX() + (int) Math.round(Math.cos(a) * d), z = from.getZ() + (int) Math.round(Math.sin(a) * d);
        if (!m.level().hasChunkAt(new BlockPos(x, 0, z))) return from;
        int y = m.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - from.getY()) > 3 || !m.level().getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) return from;
        return new BlockPos(x, y, z);
    }

    /** Twice a second: lies down in her bed at night when she's next to it, gets up when it's time (or someone needs her). */
    public static void tick(Maiden m) {
        boolean night = bedtime(m) && m.isFree() && m.getTarget() == null;
        BlockPos bed = m.residence().bed();
        if (m.isSleeping()) {
            if (!night) m.stopSleeping();
            return;
        }
        if (!night || bed == null || m.distanceToSqr(bed.getX() + 0.5, bed.getY() + 0.5, bed.getZ() + 0.5) > 2.5 * 2.5) return;
        goToBed(m);
    }

    /** Lies down in her bed if it's there and free. */
    public static boolean goToBed(Maiden m) {
        BlockPos bed = m.residence().bed();
        if (bed == null) return false;
        BlockState s = m.level().getBlockState(bed);
        if (!(s.getBlock() instanceof BedBlock) || s.getValue(BedBlock.OCCUPIED)) return false;
        m.getNavigation().stop();
        m.startSleeping(bed);
        return true;
    }
}

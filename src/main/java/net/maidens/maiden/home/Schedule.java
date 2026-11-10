package net.maidens.maiden.home;

import net.maidens.maiden.Maiden;
import net.maidens.village.VillageRecord;
import net.maidens.village.VillageSocial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A free village maiden's day (not hired, not married: those follow the player instead):
 * morning at home, work at her workstation, an evening by the village bell, home at dusk, and bed at night.
 */
public final class Schedule {
    public enum Activity {
        MORNING(4), WORK(4), GATHER(6), HOME(4), SLEEP(1);

        /** How close to the activity's place counts as "there". */
        public final int near;

        Activity(int near) {
            this.near = near;
        }
    }

    private Schedule() {}

    /**
     * The day: up at dawn and at home for a while (23000-1500), work until mid afternoon, the bell until early
     * evening, then home again, awake, until night falls (10500-13000), and asleep until dawn.
     */
    public static Activity now(Maiden m) {
        long t = m.level().getDayTime() % 24000L;
        if (t >= 23000 || t < 1500) return Activity.MORNING;
        if (t < 9000) return Activity.WORK;
        if (t < 10500) return Activity.GATHER;
        if (t < 13000) return Activity.HOME;
        return Activity.SLEEP;
    }

    /** Where she should be for an activity, or null to just potter about. */
    @Nullable
    public static BlockPos place(Maiden m, Activity a) {
        Residence r = m.residence();
        return switch (a) {
            case MORNING -> r.bed();
            case WORK -> r.jobSite();
            case GATHER -> {
                VillageRecord v = VillageSocial.villageOf(m);
                yield v == null ? null : v.bell();
            }
            case HOME, SLEEP -> r.bed();
        };
    }

    /** Twice a second: lies down in her bed at night when she's next to it, gets up when it's time (or someone needs her). */
    public static void tick(Maiden m) {
        boolean night = m.hasVillageDuties() && m.isFree() && m.getTarget() == null && now(m) == Activity.SLEEP;
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

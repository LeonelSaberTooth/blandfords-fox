package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.maidens.maiden.home.Schedule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** Walks a free village maiden to where her day says she should be (see {@link Schedule}). */
final class ScheduleGoal extends Goal {
    private final Maiden m;
    private int cooldown;
    @Nullable private BlockPos to;
    private Schedule.Activity activity = Schedule.Activity.MORNING;
    private int repath, ticks;
    private double closest;
    private int lastCloser;

    ScheduleGoal(Maiden m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 10;     // (goals are only checked every other tick, so not tickCount % n)
        if (!m.hasVillageDuties() || !m.isFree() || m.isSleeping() || m.getTarget() != null) return false;
        activity = Schedule.now(m);
        to = Schedule.place(m, activity);
        return to != null && !there();
    }

    @Override
    public boolean canContinueToUse() {
        return to != null && m.hasVillageDuties() && m.isFree() && m.getTarget() == null && Schedule.now(m) == activity && !there() && ticks < 1200;
    }

    private boolean there() {
        return m.distanceToSqr(Vec3.atBottomCenterOf(to)) <= (activity.near + 0.5) * (activity.near + 0.5);
    }

    @Override
    public void start() {
        repath = 0;
        ticks = 0;
        closest = Double.MAX_VALUE;
        lastCloser = 0;
    }

    @Override
    public void tick() {
        ticks++;
        double d = m.distanceToSqr(Vec3.atBottomCenterOf(to));
        if (d < closest - 1) {
            closest = d;
            lastCloser = ticks;
        }
        // at bedtime, if she's close but can't quite get to her bed (a door, a step...), she gets in anyway
        if (activity == Schedule.Activity.SLEEP && ticks - lastCloser > 100 && d < 8 * 8 && Schedule.goToBed(m)) return;
        if (--repath > 0) return;
        repath = 40;
        Vec3 c = Vec3.atBottomCenterOf(to);
        Vec3 step = m.distanceToSqr(c) > 30 * 30 ? LandRandomPos.getPosTowards(m, 20, 7, c) : c;
        if (step == null) step = c;
        m.getNavigation().moveTo(step.x, step.y, step.z, 0.5);
    }

    @Override
    public void stop() {
        m.getNavigation().stop();
    }
}

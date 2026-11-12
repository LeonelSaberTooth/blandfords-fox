package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.maidens.maiden.home.Schedule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** Walks a village resident to where her day says she should be (see {@link Schedule}). */
final class ScheduleGoal extends Goal {
    private final Maiden m;
    private int cooldown;
    @Nullable private BlockPos to;
    private Schedule.Activity activity = Schedule.Activity.HOME;
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
        if (!Schedule.keeps(m) || !m.isFree() || m.isSleeping() || m.getTarget() != null) return false;
        activity = Schedule.now(m);
        to = Schedule.place(m, activity);
        return to != null && !there();
    }

    @Override
    public boolean canContinueToUse() {
        return to != null && !m.isSleeping() && Schedule.keeps(m) && m.isFree() && m.getTarget() == null && Schedule.now(m) == activity
                && !there() && ticks < 2400;
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
        // stuck far away for 10 s: she finds her own way there
        if (ticks - lastCloser > 200 && m.arriveNear(to)) {
            lastCloser = ticks;
            closest = m.distanceToSqr(Vec3.atBottomCenterOf(to));
            return;
        }
        if (--repath > 0 && !m.getNavigation().isDone()) return;
        Vec3 c = Vec3.atBottomCenterOf(to);
        if (d > 36 * 36) {
            // a long way (out to the woods, the shore...): the whole way planned at once now and then (further than
            // she usually plans), else a stretch at a time
            repath = 200;
            var range = m.getAttribute(Attributes.FOLLOW_RANGE);
            double was = range.getBaseValue();
            range.setBaseValue(160);
            Path path = m.getNavigation().createPath(to, 2);
            range.setBaseValue(was);
            if (path != null && path.getNodeCount() > 1) {
                m.getNavigation().moveTo(path, 0.55);
                return;
            }
        }
        repath = 40;
        Vec3 step = d > 30 * 30 ? LandRandomPos.getPosTowards(m, 20, 7, c) : c;
        if (step == null) step = c;
        m.getNavigation().moveTo(step.x, step.y, step.z, 0.5);
    }

    @Override
    public void stop() {
        m.getNavigation().stop();
    }
}

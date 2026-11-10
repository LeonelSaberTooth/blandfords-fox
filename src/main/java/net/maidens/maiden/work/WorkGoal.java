package net.maidens.maiden.work;

import net.maidens.core.SelfTestFlag;

import net.maidens.maiden.Maiden;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Runs the {@link WorkTask} for her current job (farm, wood, mine, fish) while she isn't busy talking or trading. */
public final class WorkGoal extends Goal {
    private final Maiden m;
    private final Map<Job, WorkTask> tasks = new EnumMap<>(Job.class);
    private Job running = Job.NONE;

    public WorkGoal(Maiden m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        tasks.put(Job.FARM, new FarmTask(m));
        tasks.put(Job.WOOD, new WoodTask(m));
        tasks.put(Job.MINE, new MineTask(m));
        tasks.put(Job.FISH, new FishTask(m));
    }

    @Override
    public boolean canUse() {
        Employment e = m.employment();
        // her village's work: in work hours (a miner still underground climbs out first)
        boolean time = !e.forVillage() || e.busyNow() || e.inMine();
        return e.job().atWorkSpot() && !e.isDelivering() && m.isFree() && time;
    }

    private int commute;

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        m.getNavigation().stop();
        WorkTask task = tasks.get(running);
        if (task != null) task.stop();
    }

    @Override
    public void tick() {
        if (!(m.level() instanceof ServerLevel level)) return;
        Job j = m.employment().job();
        WorkTask task = tasks.get(j);
        if (task == null) return;
        if (j != running) {
            WorkTask old = tasks.get(running);
            if (old != null) old.stop();
            running = j;
            task.reset();
        }
        // a long way to her village's work spot (further than she can plan a path): there a stretch at a time
        Employment e = m.employment();
        if (e.forVillage() && e.workPos() != null && !e.inMine() && !m.blockPosition().closerThan(e.workPos(), 36)) {
            if (--commute <= 0 || m.getNavigation().isDone()) {
                commute = 200;
                // the whole way planned at once (further than she usually plans: only now and then, for the long walk)
                var range = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
                double was = range.getBaseValue();
                range.setBaseValue(160);
                net.minecraft.world.level.pathfinder.Path path = m.getNavigation().createPath(e.workPos(), 2);
                range.setBaseValue(was);
                if (path != null && path.getNodeCount() > 1) m.getNavigation().moveTo(path, 0.6);
                else {
                    Vec3 to = Vec3.atBottomCenterOf(e.workPos());
                    Vec3 step = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosTowards(m, 24, 8, to, Math.PI / 2);
                    if (step != null) m.getNavigation().moveTo(step.x, step.y, step.z, 0.6);
                }
            }
            if (SelfTestFlag.ON && m.tickCount % 20 == 0) e.note = j + " on her way, " + (int) Math.sqrt(m.blockPosition().distSqr(e.workPos())) + " blocks to go";
            return;
        }
        if (SelfTestFlag.ON && m.tickCount % 20 == 0) {
            m.employment().note = j + " at " + m.blockPosition().toShortString() + " " + task.debug()
                    + " nav " + (m.getNavigation().isDone() ? "idle" : "moving");
        }
        task.tick(level);
    }
}

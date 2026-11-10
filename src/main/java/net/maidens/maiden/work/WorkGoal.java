package net.maidens.maiden.work;

import net.maidens.core.SelfTestFlag;

import net.maidens.maiden.Maiden;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

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
        if (SelfTestFlag.ON && m.tickCount % 20 == 0) {
            m.employment().note = j + " at " + m.blockPosition().toShortString() + " " + task.debug()
                    + " nav " + (m.getNavigation().isDone() ? "idle" : "moving");
        }
        task.tick(level);
    }
}

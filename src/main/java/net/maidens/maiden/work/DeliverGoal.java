package net.maidens.maiden.work;

import net.maidens.block.MarkedChestBlockEntity;
import net.maidens.maiden.Maiden;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Walks her to the marked chest she's delivering to, opens it, unloads her bag, and closes it. */
public final class DeliverGoal extends Goal {
    private final Maiden m;
    private int ticks, repath, lastCloser, closeLid;
    private double closest;

    public DeliverGoal(Maiden m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return m.employment().deliverTo != null && m.isFree();
    }

    @Override
    public boolean canContinueToUse() {
        return (m.employment().deliverTo != null || closeLid > 0) && m.isFree();
    }

    @Override
    public void start() {
        ticks = repath = lastCloser = closeLid = 0;
        closest = Double.MAX_VALUE;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Employment e = m.employment();
        if (!(m.level() instanceof ServerLevel level)) return;
        if (closeLid > 0) {
            if (--closeLid == 0 && e.lastChest != null) level.blockEvent(e.lastChest, level.getBlockState(e.lastChest).getBlock(), 1, 0);
            return;
        }
        // done (the goal can be ticked once more before it notices it's finished)
        if (e.deliverTo == null) return;
        ticks++;
        if (!(level.getBlockEntity(e.deliverTo) instanceof MarkedChestBlockEntity chest)) {
            // the chest is gone: look for another one, or give up on it
            if (!Delivery.start(e, e.resumeAfterDelivery)) e.deliverTo = null;
            return;
        }
        Vec3 c = Vec3.atCenterOf(e.deliverTo);
        double d = m.distanceToSqr(c);
        m.getLookControl().setLookAt(c);
        if (d < closest - 1) {
            closest = d;
            lastCloser = ticks;
        }
        if (d > 2.5 * 2.5 && !(ticks - lastCloser > 60 && d < 5 * 5)) {
            if (ticks - lastCloser > 200 && m.arriveNear(e.deliverTo)) {
                lastCloser = ticks;
                closest = m.distanceToSqr(c);
                return;
            }
            if (--repath <= 0) {
                repath = 20;
                m.getNavigation().moveTo(c.x, e.deliverTo.getY(), c.z, 0.6);
            }
            return;
        }
        m.getNavigation().stop();
        level.blockEvent(e.deliverTo, level.getBlockState(e.deliverTo).getBlock(), 1, 1);     // open the lid
        Delivery.unload(e, chest);
        e.lastChest = e.deliverTo;
        e.deliverTo = null;
        closeLid = 20;
    }

    @Override
    public void stop() {
        m.getNavigation().stop();
        Employment e = m.employment();
        if (closeLid > 0 && e.lastChest != null && m.level() instanceof ServerLevel level) {
            level.blockEvent(e.lastChest, level.getBlockState(e.lastChest).getBlock(), 1, 0);
        }
        closeLid = 0;
    }
}

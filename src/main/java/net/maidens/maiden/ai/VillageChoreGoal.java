package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.maidens.maiden.home.Schedule;
import net.maidens.village.Chore;
import net.maidens.village.Upkeep;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A free village maiden (not hired, not married) takes one of her village's chores, carries the block there
 * (or a shovel, to clear a road) and does it.
 */
final class VillageChoreGoal extends Goal {
    private static final double REACH = 3.5, MAX_REACH = 8;
    private static final int GIVE_UP = 600;

    private final Maiden m;
    private int cooldown;
    @Nullable private Chore chore;
    private int ticks, repath, noPath;
    /** Closest she got so far, and when she last got closer. */
    private double closest;
    private int lastCloser;

    VillageChoreGoal(Maiden m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 10;     // (goals are only checked every other tick, so not tickCount % n)
        if (!m.hasVillageDuties() || m.employment().busyNow() || m.isRoyal() || m.isGuard() || !m.isFree() || m.isSleeping() || !choreTime(m)) return false;
        chore = Upkeep.take(m);
        return chore != null;
    }

    @Override
    public boolean canContinueToUse() {
        return chore != null && m.hasVillageDuties() && !m.employment().busyNow() && m.isFree() && ticks < GIVE_UP && choreTime(m)
                && Upkeep.stillNeeded(m, chore);
    }

    @Override
    public void start() {
        ticks = 0;
        repath = 0;
        noPath = 0;
        closest = Double.MAX_VALUE;
        lastCloser = 0;
        if (chore.stubborn()) m.arriveNear(chore.pos);     // others couldn't get to it: she goes straight there
        ItemStack held = chore.kind == Chore.Kind.CLEAR ? new ItemStack(Items.IRON_SHOVEL) : new ItemStack(chore.block.getBlock().asItem());
        m.setItemSlot(EquipmentSlot.MAINHAND, held);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (chore == null) return;     // done; the goal ends at its next check
        ticks++;
        Vec3 c = Vec3.atCenterOf(chore.pos);
        m.getLookControl().setLookAt(c.x, c.y, c.z);
        double d = m.distanceToSqr(c);
        if (d < closest - 1) {
            closest = d;
            lastCloser = ticks;
        }
        // close enough, or as close as she can get (a spot inside a wall or a hole): she reaches over
        boolean stalled = ticks - lastCloser > 60 && d <= MAX_REACH * MAX_REACH;
        // stuck far away for 10 s: she finds her own way there
        if (!stalled && ticks - lastCloser > 200 && m.arriveNear(chore.pos)) {
            lastCloser = ticks;
            closest = m.distanceToSqr(c);
            return;
        }
        if (d > REACH * REACH && !stalled) {
            if (--repath <= 0) {
                repath = 20;
                // far away: walk there in stages (a path can only be planned so far ahead)
                Vec3 to = d > 30 * 30 ? LandRandomPos.getPosTowards(m, 20, 7, c) : c;
                if (to == null) to = c;
                if (!m.getNavigation().moveTo(to.x, to == c ? chore.pos.getY() : to.y, to.z, 0.6)) noPath++;
            }
            return;
        }
        m.getNavigation().stop();
        if (Upkeep.doIt(m, chore)) chore = null;
    }

    @Override
    public void stop() {
        if (chore != null) Upkeep.giveBack(chore, ticks >= GIVE_UP || noPath >= 6, m.level().getGameTime());
        chore = null;
        m.getNavigation().stop();
        if (!m.employment().isWorking() && !m.isGuard()) m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** Chores are for the working day and the evening at the bell, not when she should be at home or asleep. */
    private static boolean choreTime(Maiden m) {
        Schedule.Activity a = Schedule.now(m);
        return a == Schedule.Activity.WORK || a == Schedule.Activity.GATHER;
    }
}

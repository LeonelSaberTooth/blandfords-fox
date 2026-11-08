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
    private static final double REACH = 3.5;
    private static final int GIVE_UP = 1200;

    private final Maiden m;
    @Nullable private Chore chore;
    private int ticks, repath;

    VillageChoreGoal(Maiden m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (m.tickCount % 20 != 0 || !m.hasVillageDuties() || !m.isFree() || m.isSleeping() || Schedule.now(m) == Schedule.Activity.SLEEP) return false;
        chore = Upkeep.take(m);
        return chore != null;
    }

    @Override
    public boolean canContinueToUse() {
        return chore != null && m.hasVillageDuties() && m.isFree() && ticks < GIVE_UP && Upkeep.stillNeeded(m, chore);
    }

    @Override
    public void start() {
        ticks = 0;
        repath = 0;
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
        if (m.distanceToSqr(c) > REACH * REACH) {
            if (--repath <= 0) {
                repath = 20;
                // far away: walk there in stages (a path can only be planned so far ahead)
                Vec3 to = m.distanceToSqr(c) > 30 * 30 ? LandRandomPos.getPosTowards(m, 20, 7, c) : c;
                if (to == null) to = c;
                m.getNavigation().moveTo(to.x, to == c ? chore.pos.getY() : to.y, to.z, 0.6);
            }
            return;
        }
        m.getNavigation().stop();
        if (Upkeep.doIt(m, chore)) chore = null;
    }

    @Override
    public void stop() {
        if (chore != null) Upkeep.giveBack(chore, ticks >= GIVE_UP, m.level().getGameTime());
        chore = null;
        m.getNavigation().stop();
        if (!m.employment().isWorking()) m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }
}

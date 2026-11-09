package net.maidens.maiden.war;

import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * A maiden sent to raid another village (see {@link net.maidens.politics.WarParties}): which village she raids, where
 * she came from (and goes back to), whether she's given up, and how much she's stolen. Raiders don't die from a
 * beating: below 30% health they surrender, drop their sword and run home.
 */
public final class Raider {
    private static final long NONE = Long.MIN_VALUE;

    private final Maiden m;
    private long target = NONE, from = NONE;
    @Nullable private BlockPos retreat;
    private boolean surrendered;
    private int stolen;

    public Raider(Maiden m) {
        this.m = m;
    }

    public boolean active() {
        return target != NONE;
    }

    public long target() {
        return target;
    }

    public long from() {
        return from;
    }

    @Nullable
    public BlockPos retreat() {
        return retreat;
    }

    public boolean surrendered() {
        return surrendered;
    }

    public int stolen() {
        return stolen;
    }

    public void start(long targetVillage, long fromVillage, BlockPos retreatTo) {
        target = targetVillage;
        from = fromVillage;
        retreat = retreatTo;
    }

    /** Beaten (or done stealing): sword down, off home. */
    public void surrender() {
        if (surrendered) return;
        surrendered = true;
        m.setTarget(null);
        // the village's maidens let her go
        for (Maiden o : m.level().getEntitiesOfClass(Maiden.class, m.getBoundingBox().inflate(24), o -> o.getTarget() == m)) o.setTarget(null);
        m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        var bag = m.employment().bag();
        for (int i = 0; i < bag.getContainerSize(); i++) {
            if (bag.getItem(i).getItem() instanceof net.minecraft.world.item.SwordItem) bag.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    public void stole(int stacks) {
        stolen += stacks;
    }

    public void save(CompoundTag tag) {
        if (!active()) return;
        tag.putLong("RaidTarget", target);
        tag.putLong("RaidFrom", from);
        if (retreat != null) tag.putLong("RaidRetreat", retreat.asLong());
        tag.putBoolean("Surrendered", surrendered);
    }

    public void load(CompoundTag tag) {
        target = tag.contains("RaidTarget") ? tag.getLong("RaidTarget") : NONE;
        from = tag.contains("RaidFrom") ? tag.getLong("RaidFrom") : NONE;
        retreat = tag.contains("RaidRetreat") ? BlockPos.of(tag.getLong("RaidRetreat")) : null;
        surrendered = tag.getBoolean("Surrendered");
    }
}

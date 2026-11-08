package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What the mod remembers about one maiden village. Its id is its structure start's chunk position (as a long),
 * which never changes. Later phases add the name, blueprint, citizens and so on here.
 */
public final class VillageRecord {
    public final long id;
    /** The village's whole area (all its buildings and roads). */
    public BoundingBox box;

    VillageRecord(long id, BoundingBox box) {
        this.id = id;
        this.box = box;
    }

    public BlockPos centre() {
        return box.getCenter();
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("Id", id);
        t.putIntArray("Box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        return t;
    }

    static VillageRecord load(CompoundTag t) {
        int[] b = t.getIntArray("Box");
        BoundingBox box = b.length == 6 ? new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]) : new BoundingBox(0, 0, 0, 0, 0, 0);
        return new VillageRecord(t.getLong("Id"), box);
    }
}

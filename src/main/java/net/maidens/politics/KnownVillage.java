package net.maidens.politics;

import net.maidens.village.VillageRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

/**
 * A village the network knows about, found from the world seed (it may not have generated yet):
 * where it is, its name and style, whether it's a maiden village, and its temperament (its default stance).
 */
public final class KnownVillage {
    /** Its structure start chunk (the same id as its {@link VillageRecord}). */
    public final long id;
    public final int x, z;
    public final String name, style;
    public final boolean maiden;
    public final Stance temperament;

    KnownVillage(long id, String style, boolean maiden, Stance temperament) {
        this.id = id;
        ChunkPos c = new ChunkPos(id);
        this.x = c.getMiddleBlockX();
        this.z = c.getMiddleBlockZ();
        this.name = VillageRecord.nameFor(id);
        this.style = style;
        this.maiden = maiden;
        this.temperament = temperament;
    }

    public double distanceTo(KnownVillage o) {
        double dx = x - o.x, dz = z - o.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("Id", id);
        t.putString("Style", style);
        t.putBoolean("Maiden", maiden);
        t.putInt("Temper", temperament.ordinal());
        return t;
    }

    static KnownVillage load(CompoundTag t) {
        return new KnownVillage(t.getLong("Id"), t.getString("Style"), t.getBoolean("Maiden"), Stance.byId(t.getInt("Temper")));
    }
}

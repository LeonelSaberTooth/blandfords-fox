package net.maidens.village;

import net.maidens.core.MaidensConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Every maiden village in a dimension, saved with the world. The one place that answers
 * "which village is this spot in?" and "is that village a maiden village?".
 */
public final class Villages extends SavedData {
    private static final String NAME = "maidens_villages";
    private static final long SALT = 0x6D61696465AL;

    private final Map<Long, VillageRecord> records = new HashMap<>();

    public static Villages get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Villages::new, Villages::load, null), NAME);
    }

    /** The vanilla (or modded) village whose buildings or roads contain this spot, if any. */
    @Nullable
    public static StructureStart villageAt(ServerLevel level, BlockPos pos) {
        StructureStart s = level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);
        return s.isValid() ? s : null;
    }

    /** The coin flip for a village, fixed by the world seed and where the village starts. */
    public static boolean rollsMaidens(ServerLevel level, StructureStart start) {
        long seed = level.getSeed() ^ start.getChunkPos().toLong() * 0x9E3779B97F4A7C15L ^ SALT;
        return RandomSource.create(seed).nextDouble() < MaidensConfig.MAIDEN_VILLAGE_CHANCE.get();
    }

    /** The record for a maiden village, made the first time it's needed. */
    public VillageRecord record(StructureStart start) {
        long id = start.getChunkPos().toLong();
        VillageRecord r = records.get(id);
        if (r == null) {
            Blueprint bp = new Blueprint();
            for (StructurePiece piece : start.getPieces()) bp.addPiece(piece.getBoundingBox());
            r = new VillageRecord(id, start.getBoundingBox(), bp);
            records.put(id, r);
            setDirty();
        }
        return r;
    }

    /** The maiden village whose area contains this spot, or null. */
    @Nullable
    public VillageRecord at(BlockPos pos) {
        for (VillageRecord r : records.values()) if (r.box.isInside(pos)) return r;
        return null;
    }

    /** Records any blueprint pieces whose chunks have loaded since last time. */
    public void recordLoadedPieces(ServerLevel level) {
        for (VillageRecord r : records.values()) if (r.blueprint.recordLoadedPieces(level)) setDirty();
    }

    @Nullable
    public VillageRecord byId(long id) {
        return records.get(id);
    }

    public Iterable<VillageRecord> all() {
        return records.values();
    }

    private static Villages load(CompoundTag tag, HolderLookup.Provider registries) {
        Villages v = new Villages();
        ListTag list = tag.getList("Villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            VillageRecord r = VillageRecord.load(list.getCompound(i), registries);
            v.records.put(r.id, r);
        }
        return v;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (VillageRecord r : records.values()) list.add(r.save());
        tag.put("Villages", list);
        return tag;
    }
}

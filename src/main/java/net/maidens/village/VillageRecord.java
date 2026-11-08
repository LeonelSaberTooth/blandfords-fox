package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the mod remembers about one maiden village: its name, area, blueprint, citizens, and how much each
 * player has traded there. Its id is its structure start's chunk position (as a long), which never changes.
 */
public final class VillageRecord {
    private static final String[] FIRST = {"Ash", "Brook", "Willow", "Elder", "Rose", "Thorn", "Maple", "Fern", "Briar", "Clover",
            "Amber", "Hazel", "Lark", "Moss", "Bramble", "Honey", "Wren", "Linden", "Sorrel", "Juniper"};
    private static final String[] LAST = {"haven", "field", "dale", "wick", "mere", "ford", "brook", "stead", "vale", "holm",
            "ley", "combe", "hollow", "bury", "glen"};

    public final long id;
    public final String name;
    /** The village's whole area (all its buildings and roads). */
    public BoundingBox box;
    public final Blueprint blueprint;
    final Set<UUID> citizens = new HashSet<>();
    final Map<UUID, Integer> trades = new HashMap<>();
    /** The village style ("plains", "desert"...), which decides what it can build. */
    public String style = "plains";
    /** The last building started (its short name), for the village info. */
    public String project = "";
    /** The village bell, where maidens gather in the evening (found in the blueprint; not saved). */
    private BlockPos bell;
    private boolean bellLooked;
    /** When the last new maiden moved in (game time). */
    long lastArrival = Long.MIN_VALUE / 2;
    /** What needs fixing right now (not saved: found again by the scan after a restart). */
    final Map<Long, Chore> chores = new LinkedHashMap<>();

    VillageRecord(long id, BoundingBox box, Blueprint blueprint) {
        this.id = id;
        this.box = box;
        this.blueprint = blueprint;
        RandomSource r = RandomSource.create(id);
        this.name = FIRST[r.nextInt(FIRST.length)] + LAST[r.nextInt(LAST.length)];
    }

    public BlockPos centre() {
        return box.getCenter();
    }

    public BlockPos bell() {
        if (!bellLooked || bell == null && blueprint.recordedPieces() < blueprint.pieceCount()) {
            bellLooked = blueprint.recordedPieces() == blueprint.pieceCount();
            BlockPos[] found = {null};
            blueprint.forEachBlock((p, s) -> {
                if (found[0] == null && s.is(net.minecraft.world.level.block.Blocks.BELL)) found[0] = p;
            });
            bell = found[0];
        }
        return bell;
    }

    public boolean isCitizen(UUID player) {
        return citizens.contains(player);
    }

    public int tradesBy(UUID player) {
        return trades.getOrDefault(player, 0);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("Id", id);
        t.putIntArray("Box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        t.put("Blueprint", blueprint.save());
        ListTag cit = new ListTag();
        for (UUID u : citizens) cit.add(NbtUtils.createUUID(u));
        t.put("Citizens", cit);
        CompoundTag tr = new CompoundTag();
        trades.forEach((u, n) -> tr.putInt(u.toString(), n));
        t.put("Trades", tr);
        t.putString("Style", style);
        t.putString("Project", project);
        t.putLong("LastArrival", lastArrival);
        return t;
    }

    static VillageRecord load(CompoundTag t, HolderLookup.Provider registries) {
        int[] b = t.getIntArray("Box");
        BoundingBox box = b.length == 6 ? new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]) : new BoundingBox(0, 0, 0, 0, 0, 0);
        VillageRecord r = new VillageRecord(t.getLong("Id"), box, Blueprint.load(t.getCompound("Blueprint"), registries));
        ListTag cit = t.getList("Citizens", Tag.TAG_INT_ARRAY);
        for (Tag u : cit) r.citizens.add(NbtUtils.loadUUID(u));
        if (t.contains("Style")) r.style = t.getString("Style");
        r.project = t.getString("Project");
        if (t.contains("LastArrival")) r.lastArrival = t.getLong("LastArrival");
        CompoundTag tr = t.getCompound("Trades");
        for (String k : tr.getAllKeys()) {
            try {
                r.trades.put(UUID.fromString(k), tr.getInt(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return r;
    }
}

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
    /** How many blueprint pieces were recorded when the bell was last looked for (-1: never). */
    private int bellLookedAt = -1;
    /** When the last new maiden moved in (game time). */
    long lastArrival = Long.MIN_VALUE / 2;
    /** What needs fixing right now (not saved: found again by the scan after a restart). */
    final Map<Long, Chore> chores = new LinkedHashMap<>();

    VillageRecord(long id, BoundingBox box, Blueprint blueprint) {
        this.id = id;
        this.box = box;
        this.blueprint = blueprint;
        this.name = nameFor(id);
    }

    /** A village's name, from its id alone (so villages not yet visited have their name too). */
    public static String nameFor(long id) {
        RandomSource r = RandomSource.create(id);
        return FIRST[r.nextInt(FIRST.length)] + LAST[r.nextInt(LAST.length)];
    }

    /** Its royals (one for now; kept as a set so a royal family can come later). */
    public final Set<UUID> royals = new HashSet<>();
    /** Players who helped beat back a raid here. */
    final Set<UUID> defenders = new HashSet<>();
    /** The day each citizen became one. */
    final Map<UUID, Long> citizenSince = new HashMap<>();

    public boolean isRoyal(UUID maiden) {
        return royals.contains(maiden);
    }

    public void addDefender(UUID player) {
        defenders.add(player);
    }

    public boolean isDefender(UUID player) {
        return defenders.contains(player);
    }

    /** Days since this player became a citizen, or -1 if they aren't one. */
    public long citizenDays(UUID player, long today) {
        if (!citizens.contains(player)) return -1;
        return today - citizenSince.getOrDefault(player, today);
    }

    public BlockPos centre() {
        return box.getCenter();
    }

    public BlockPos bell() {
        int recorded = blueprint.recordedPieces();
        if (bell == null && recorded != bellLookedAt) {      // look again only when more of the village was recorded
            bellLookedAt = recorded;
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
        ListTag roy = new ListTag();
        for (UUID u : royals) roy.add(NbtUtils.createUUID(u));
        t.put("Royals", roy);
        ListTag def = new ListTag();
        for (UUID u : defenders) def.add(NbtUtils.createUUID(u));
        t.put("Defenders", def);
        CompoundTag since = new CompoundTag();
        citizenSince.forEach((u, d) -> since.putLong(u.toString(), d));
        t.put("CitizenSince", since);
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
        for (Tag u : t.getList("Royals", Tag.TAG_INT_ARRAY)) r.royals.add(NbtUtils.loadUUID(u));
        for (Tag u : t.getList("Defenders", Tag.TAG_INT_ARRAY)) r.defenders.add(NbtUtils.loadUUID(u));
        CompoundTag since = t.getCompound("CitizenSince");
        for (String k : since.getAllKeys()) {
            try {
                r.citizenSince.put(UUID.fromString(k), since.getLong(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
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

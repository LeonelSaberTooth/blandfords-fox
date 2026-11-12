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
    /** Its castle, if it has one. */
    @org.jetbrains.annotations.Nullable public BoundingBox castle;
    /** The area its walls enclose (their bounding box), if it has walls. */
    @org.jetbrains.annotations.Nullable public BoundingBox wallBox;
    /** Whether a fortified-by-nature village has had its castle and walls planned (or tried to). */
    public boolean fortified;
    /** Where its castle will go (corner, and the turn as x of the second), chosen before the land there loads. */
    @org.jetbrains.annotations.Nullable public BlockPos castleCorner;
    public int castleTurn;
    /** Whether its banner stands by the bell, and whether its fields are laid out. */
    public boolean bannerUp, farmsPlanned;
    /** Most of the trees inside the village (its walls) have been taken out. */
    public boolean treesCleared;
    /** The field's ground (its farmland, water and path) and the path through it (for the farmers). */
    public long[] fieldCells = new long[0], fieldPath = new long[0];
    /** How many live there (counted once a minute while it's loaded; not saved). */
    public int population;
    /** What the village has gathered (item id, count): food from its fields and boats, logs, stone. */
    public final Map<String, Integer> store = new java.util.TreeMap<>();
    /** Whether it's been given its first stock, and the last day its people ate. */
    public boolean stocked;
    public long lastMeal = -1;
    /** The gates in its walls. */
    public final java.util.List<Gate> gates = new java.util.ArrayList<>();

    /** A gate: where it is, which way is out, and whether a path joins it to the streets yet. */
    public static final class Gate {
        public final BlockPos pos;
        public final int dx, dz;
        public boolean joined;

        public Gate(BlockPos pos, int dx, int dz) {
            this.pos = pos;
            this.dx = dx;
            this.dz = dz;
        }
    }
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

    // ------------------------------------------------------------------ the store

    private static String key(net.minecraft.world.item.Item i) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString();
    }

    public void storeAdd(net.minecraft.world.item.ItemStack s) {
        if (!s.isEmpty()) storeAdd(s.getItem(), s.getCount());
    }

    public void storeAdd(net.minecraft.world.item.Item i, int n) {
        if (n > 0) store.merge(key(i), n, Integer::sum);
    }

    public int count(net.minecraft.world.item.Item i) {
        return store.getOrDefault(key(i), 0);
    }

    /** How many of the items matching a test are in store. */
    public int count(java.util.function.Predicate<net.minecraft.world.item.Item> test) {
        int n = 0;
        for (var e : store.entrySet()) if (test.test(itemOf(e.getKey()))) n += e.getValue();
        return n;
    }

    /** Takes n of the items matching a test (the most plentiful first). False (and nothing taken) if there aren't that many. */
    public boolean take(java.util.function.Predicate<net.minecraft.world.item.Item> test, int n) {
        if (count(test) < n) return false;
        while (n > 0) {
            String most = null;
            for (var e : store.entrySet()) if (test.test(itemOf(e.getKey())) && (most == null || e.getValue() > store.get(most))) most = e.getKey();
            int have = store.get(most), t = Math.min(have, n);
            if (have == t) store.remove(most);
            else store.put(most, have - t);
            n -= t;
        }
        return true;
    }

    private static net.minecraft.world.item.Item itemOf(String k) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(k));
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

    private static int[] boxArray(BoundingBox b) {
        return new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
    }

    @org.jetbrains.annotations.Nullable
    private static BoundingBox boxOf(int[] a) {
        return a.length == 6 ? new BoundingBox(a[0], a[1], a[2], a[3], a[4], a[5]) : null;
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
        if (castle != null) t.putIntArray("Castle", boxArray(castle));
        if (wallBox != null) t.putIntArray("WallBox", boxArray(wallBox));
        t.putBoolean("Fortified", fortified);
        t.putBoolean("BannerUp", bannerUp);
        t.putBoolean("FarmsPlanned", farmsPlanned);
        t.putBoolean("TreesCleared", treesCleared);
        t.putLongArray("FieldCells", fieldCells);
        t.putLongArray("FieldPath", fieldPath);
        CompoundTag st = new CompoundTag();
        store.forEach(st::putInt);
        t.put("Store", st);
        t.putBoolean("Stocked", stocked);
        t.putLong("LastMeal", lastMeal);
        if (castleCorner != null) {
            t.putLong("CastleCorner", castleCorner.asLong());
            t.putInt("CastleTurn", castleTurn);
        }
        net.minecraft.nbt.ListTag gl = new net.minecraft.nbt.ListTag();
        for (Gate g : gates) {
            CompoundTag gt = new CompoundTag();
            gt.putLong("Pos", g.pos.asLong());
            gt.putInt("Dx", g.dx);
            gt.putInt("Dz", g.dz);
            gt.putBoolean("Joined", g.joined);
            gl.add(gt);
        }
        t.put("Gates", gl);
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
        r.castle = boxOf(t.getIntArray("Castle"));
        r.wallBox = boxOf(t.getIntArray("WallBox"));
        r.fortified = t.getBoolean("Fortified");
        r.bannerUp = t.getBoolean("BannerUp");
        r.farmsPlanned = t.getBoolean("FarmsPlanned");
        r.treesCleared = t.getBoolean("TreesCleared");
        r.fieldCells = t.getLongArray("FieldCells");
        r.fieldPath = t.getLongArray("FieldPath");
        CompoundTag st = t.getCompound("Store");
        for (String k : st.getAllKeys()) r.store.put(k, st.getInt(k));
        r.stocked = t.getBoolean("Stocked");
        if (t.contains("LastMeal")) r.lastMeal = t.getLong("LastMeal");
        if (t.contains("CastleCorner")) {
            r.castleCorner = BlockPos.of(t.getLong("CastleCorner"));
            r.castleTurn = t.getInt("CastleTurn");
        }
        for (net.minecraft.nbt.Tag e : t.getList("Gates", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag gt = (CompoundTag) e;
            Gate g = new Gate(BlockPos.of(gt.getLong("Pos")), gt.getInt("Dx"), gt.getInt("Dz"));
            g.joined = gt.getBoolean("Joined");
            r.gates.add(g);
        }
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

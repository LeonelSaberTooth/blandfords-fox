package net.maidens.politics;

import net.maidens.village.VillageRecord;
import net.maidens.village.Villages;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every village in a dimension the mod knows of, found from the world seed (the way /locate finds them, without
 * generating anything), and how the maiden villages among them stand towards each other. Villages are looked up
 * a few at a time around maiden villages and wherever the world map is opened. Saved with the world.
 */
public final class Network extends SavedData {
    private static final String NAME = "maidens_network";
    /** Maiden villages this close know each other. */
    public static final int NEIGHBOUR_RANGE = 1500;
    private static final int NEWS_KEPT = 30;

    private final Map<Long, KnownVillage> villages = new HashMap<>();
    /** Chunks already checked for a village start. */
    private final Set<Long> checked = new HashSet<>();
    /** Stances set by decisions: village -> (other village -> stance). Without one, a village's temperament holds. */
    private final Map<Long, Map<Long, Stance>> stances = new HashMap<>();
    private final Map<Long, ArrayDeque<String>> news = new HashMap<>();
    /** Stances a player chose (the village's royal won't overrule them): "from:to". */
    private final Set<String> decided = new HashSet<>();
    /** The day an aggressive village may next raid another: "from:to" -> day. */
    private final Map<String, Long> nextRaid = new HashMap<>();
    /** Roads between villages: "a:b" with a < b. */
    private final Map<String, Road> roads = new HashMap<>();
    /** Villages a player has been to (they have a record). */
    private final Set<Long> visited = new HashSet<>();
    private long lastDay = -1;

    /** Chunks waiting to be checked (not saved: asked for again as needed). */
    private final ArrayDeque<Candidate> pending = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();

    private record Candidate(ChunkPos chunk, StructureSet set, RandomSpreadStructurePlacement placement) {}

    public static Network get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Network::new, Network::load, null), NAME);
    }

    // ------------------------------------------------------------------ finding villages from the seed

    /** Queues every possible village spot within a radius to be checked (a few each tick, see {@link #tick}). */
    public void lookAround(ServerLevel level, int x, int z, int radius) {
        long seed = level.getSeed();
        for (Holder.Reference<StructureSet> h : level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).holders().toList()) {
            StructureSet set = h.value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement spread)) continue;
            if (set.structures().stream().noneMatch(e -> e.structure().is(StructureTags.VILLAGE))) continue;
            int spacing = spread.spacing();
            int minRx = Math.floorDiv((x - radius) >> 4, spacing), maxRx = Math.floorDiv((x + radius) >> 4, spacing);
            int minRz = Math.floorDiv((z - radius) >> 4, spacing), maxRz = Math.floorDiv((z + radius) >> 4, spacing);
            for (int rx = minRx; rx <= maxRx; rx++)
                for (int rz = minRz; rz <= maxRz; rz++) {
                    ChunkPos c = spread.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);   // takes a chunk in the region, not the region
                    long key = c.toLong();
                    if (checked.contains(key) || !queued.add(key)) continue;
                    pending.add(new Candidate(c, set, spread));
                }
        }
    }

    /**
     * The nearest village (from the seed, visited or not) whose start chunk passes a test, within a radius: the
     * candidates passing the test are checked nearest first, at most 64 of them. Null if none.
     */
    @Nullable
    public static KnownVillage findNearest(ServerLevel level, int x, int z, int radius, java.util.function.LongPredicate test) {
        long seed = level.getSeed();
        List<Candidate> found = new ArrayList<>();
        for (Holder.Reference<StructureSet> h : level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).holders().toList()) {
            StructureSet set = h.value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement spread)) continue;
            if (set.structures().stream().noneMatch(e -> e.structure().is(StructureTags.VILLAGE))) continue;
            int spacing = spread.spacing();
            int minRx = Math.floorDiv((x - radius) >> 4, spacing), maxRx = Math.floorDiv((x + radius) >> 4, spacing);
            int minRz = Math.floorDiv((z - radius) >> 4, spacing), maxRz = Math.floorDiv((z + radius) >> 4, spacing);
            for (int rx = minRx; rx <= maxRx; rx++)
                for (int rz = minRz; rz <= maxRz; rz++) {
                    ChunkPos c = spread.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
                    if (test.test(c.toLong())) found.add(new Candidate(c, set, spread));
                }
        }
        found.sort(java.util.Comparator.comparingDouble(c -> sq(c.chunk().getMiddleBlockX() - x) + sq(c.chunk().getMiddleBlockZ() - z)));
        Network network = get(level);
        int checks = 0;
        for (Candidate c : found) {
            if (++checks > 64) break;
            KnownVillage known = network.village(c.chunk().toLong());
            if (known != null) return known;
            if (!c.placement().isStructureChunk(level.getChunkSource().getGeneratorState(), c.chunk().x, c.chunk().z)) continue;
            for (StructureSet.StructureSelectionEntry e : c.set().structures()) {
                StructureCheckResult r = level.structureManager().checkStructurePresence(c.chunk(), e.structure().value(), c.placement(), false);
                if (r == StructureCheckResult.START_PRESENT || r == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                    long key = c.chunk().toLong();
                    String path = e.structure().unwrapKey().map(k -> k.location().getPath()).orElse("village_plains");
                    KnownVillage k = new KnownVillage(key, path.startsWith("village_") ? path.substring(8) : "plains", Villages.rollsMaidens(level, key), temperament(level, key));
                    network.villages.put(key, k);
                    network.setDirty();
                    return k;
                }
            }
        }
        return null;
    }

    private static double sq(double d) {
        return d * d;
    }

    /** For the self-test: forget which spots were checked, so they're checked again (and timed). */
    public void forgetCheckedForTest() {
        checked.clear();
    }

    /** Until when (game time) spots are checked as fast as the tick allows: someone has the world map open. */
    private long boostUntil;

    public void boost(ServerLevel level) {
        boostUntil = level.getGameTime() + 1200;
    }

    public boolean boosted(ServerLevel level) {
        return level.getGameTime() < boostUntil;
    }

    /** Whether spots are still waiting to be checked. */
    public boolean busy() {
        return !pending.isEmpty();
    }

    /** Checks one waiting spot (the same check /locate does: biomes and terrain, nothing generated). */
    public void tick(ServerLevel level) {
        Candidate c = pending.poll();
        if (c == null) return;
        long key = c.chunk().toLong();
        queued.remove(key);
        checked.add(key);
        setDirty();
        if (!c.placement().isStructureChunk(level.getChunkSource().getGeneratorState(), c.chunk().x, c.chunk().z)) return;
        for (StructureSet.StructureSelectionEntry e : c.set().structures()) {
            Holder<Structure> s = e.structure();
            StructureCheckResult r = level.structureManager().checkStructurePresence(c.chunk(), s.value(), c.placement(), false);
            // START_PRESENT: generated there already. CHUNK_LOAD_NEEDED: not generated yet, but the structure
            // fits there (biomes and the whole layout checked, as /locate does), so a village will be there.
            if (r == StructureCheckResult.START_PRESENT || r == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                String path = s.unwrapKey().map(k -> k.location().getPath()).orElse("village_plains");
                String style = path.startsWith("village_") ? path.substring(8) : "plains";
                boolean maiden = Villages.rollsMaidens(level, key);
                villages.put(key, new KnownVillage(key, style, maiden, temperament(level, key)));
                return;
            }
        }
    }

    /** A village's default stance, fixed by the seed: 40% traders, 40% neutral, 20% aggressive. */
    private static Stance temperament(ServerLevel level, long id) {
        double r = RandomSource.create(level.getSeed() ^ id * 0x632BE59BD9B4E019L).nextDouble();
        return r < 0.4 ? Stance.TRADE : r < 0.8 ? Stance.NEUTRAL : Stance.AGGRESSIVE;
    }

    /** Makes sure a visited village is known (in case it wasn't looked up yet). */
    public KnownVillage know(ServerLevel level, VillageRecord v) {
        if (visited.add(v.id)) setDirty();
        return villages.computeIfAbsent(v.id, id -> {
            setDirty();
            checked.add(id);
            return new KnownVillage(id, v.style, true, temperament(level, id));
        });
    }

    // ------------------------------------------------------------------ villages and stances

    @Nullable
    public KnownVillage village(long id) {
        return villages.get(id);
    }

    public Collection<KnownVillage> all() {
        return villages.values();
    }

    /** The maiden villages within reach of this one, nearest first. */
    public List<KnownVillage> neighbours(KnownVillage v) {
        List<KnownVillage> out = new ArrayList<>();
        for (KnownVillage o : villages.values()) {
            if (o.id != v.id && o.maiden && v.distanceTo(o) <= NEIGHBOUR_RANGE) out.add(o);
        }
        out.sort((a, b) -> Double.compare(v.distanceTo(a), v.distanceTo(b)));
        return out;
    }

    /** How a village treats another: what its rulers decided, or else its temperament. */
    public Stance stance(KnownVillage from, KnownVillage to) {
        Map<Long, Stance> m = stances.get(from.id);
        Stance s = m == null ? null : m.get(to.id);
        return s != null ? s : from.temperament;
    }

    /** A player's decision: it stands until a player changes it. */
    void setStance(KnownVillage from, KnownVillage to, Stance s) {
        stances.computeIfAbsent(from.id, k -> new HashMap<>()).put(to.id, s);
        decided.add(pair(from, to));
        setDirty();
    }

    /** A royal's own decision: only where no player has decided. Returns whether it changed anything. */
    public boolean setStanceByRoyal(KnownVillage from, KnownVillage to, Stance s) {
        if (decided.contains(pair(from, to)) || stance(from, to) == s) return false;
        stances.computeIfAbsent(from.id, k -> new HashMap<>()).put(to.id, s);
        setDirty();
        return true;
    }

    private static String pair(KnownVillage from, KnownVillage to) {
        return from.id + ":" + to.id;
    }

    /** The day an aggressive village may next raid another (a few days after first meeting, or after its last raid). */
    public long nextRaidDay(KnownVillage from, KnownVillage to, long today, net.minecraft.util.RandomSource r) {
        return nextRaid.computeIfAbsent(pair(from, to), k -> {
            setDirty();
            return today + 1 + r.nextInt(3);
        });
    }

    public void raided(KnownVillage from, KnownVillage to, long today, net.minecraft.util.RandomSource r) {
        nextRaid.put(pair(from, to), today + 3 + r.nextInt(3));
        setDirty();
    }

    public boolean isVisited(long id) {
        return visited.contains(id);
    }

    // ------------------------------------------------------------------ roads and the daily round

    @Nullable
    public Road road(KnownVillage x, KnownVillage y) {
        return roads.get(roadKey(x.id, y.id));
    }

    Road addRoad(KnownVillage x, KnownVillage y) {
        Road r = x.id < y.id ? new Road(x, y) : new Road(y, x);
        roads.put(roadKey(x.id, y.id), r);
        setDirty();
        return r;
    }

    public Collection<Road> roads() {
        return roads.values();
    }

    private static String roadKey(long x, long y) {
        return Math.min(x, y) + ":" + Math.max(x, y);
    }

    /** Every 5 s (from the village events): roads get laid; once a day, the network's daily round. */
    public void slowTick(ServerLevel level) {
        long day = level.getDayTime() / 24000L;
        if (day != lastDay) {
            boolean first = lastDay < 0;
            lastDay = day;
            if (!first) Roads.daily(level, this);
        }
        Roads.tick(level, this);
    }

    /** For the self test: a day of road building now. */
    public void buildRoadsForTest(ServerLevel level) {
        Roads.daily(level, this);
    }

    // ------------------------------------------------------------------ news

    public void news(ServerLevel level, long village, String text) {
        ArrayDeque<String> q = news.computeIfAbsent(village, k -> new ArrayDeque<>());
        q.addFirst("Day " + (level.getDayTime() / 24000L + 1) + ": " + text);
        while (q.size() > NEWS_KEPT) q.removeLast();
        setDirty();
    }

    public List<String> news(long village) {
        ArrayDeque<String> q = news.get(village);
        return q == null ? List.of() : List.copyOf(q);
    }

    // ------------------------------------------------------------------ saving

    private static Network load(CompoundTag tag, HolderLookup.Provider registries) {
        Network n = new Network();
        for (Tag t : tag.getList("Villages", Tag.TAG_COMPOUND)) {
            KnownVillage v = KnownVillage.load((CompoundTag) t);
            n.villages.put(v.id, v);
        }
        // spots checked before version 2 missed every village not generated yet: check those again
        if (tag.getInt("Version") >= 2) for (long c : tag.getLongArray("Checked")) n.checked.add(c);
        CompoundTag st = tag.getCompound("Stances");
        for (String from : st.getAllKeys()) {
            CompoundTag row = st.getCompound(from);
            Map<Long, Stance> m = new HashMap<>();
            for (String to : row.getAllKeys()) m.put(Long.parseLong(to), Stance.byId(row.getInt(to)));
            n.stances.put(Long.parseLong(from), m);
        }
        for (Tag d : tag.getList("Decided", Tag.TAG_STRING)) n.decided.add(d.getAsString());
        for (Tag rt : tag.getList("Roads", Tag.TAG_COMPOUND)) {
            Road r = Road.load((CompoundTag) rt);
            n.roads.put(roadKey(r.a, r.b), r);
        }
        for (long v : tag.getLongArray("Visited")) n.visited.add(v);
        n.lastDay = tag.contains("LastDay") ? tag.getLong("LastDay") : -1;
        CompoundTag nr = tag.getCompound("NextRaid");
        for (String k : nr.getAllKeys()) n.nextRaid.put(k, nr.getLong(k));
        CompoundTag nw = tag.getCompound("News");
        for (String k : nw.getAllKeys()) {
            ArrayDeque<String> q = new ArrayDeque<>();
            for (Tag line : nw.getList(k, Tag.TAG_STRING)) q.addLast(line.getAsString());
            n.news.put(Long.parseLong(k), q);
        }
        return n;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (KnownVillage v : villages.values()) list.add(v.save());
        tag.put("Villages", list);
        tag.putInt("Version", 2);
        tag.put("Checked", new LongArrayTag(checked.stream().mapToLong(Long::longValue).toArray()));
        CompoundTag st = new CompoundTag();
        stances.forEach((from, m) -> {
            CompoundTag row = new CompoundTag();
            m.forEach((to, s) -> row.putInt(Long.toString(to), s.ordinal()));
            st.put(Long.toString(from), row);
        });
        tag.put("Stances", st);
        ListTag rl = new ListTag();
        for (Road r : roads.values()) rl.add(r.save());
        tag.put("Roads", rl);
        tag.put("Visited", new LongArrayTag(visited.stream().mapToLong(Long::longValue).toArray()));
        tag.putLong("LastDay", lastDay);
        ListTag dec = new ListTag();
        for (String d : decided) dec.add(StringTag.valueOf(d));
        tag.put("Decided", dec);
        CompoundTag nr = new CompoundTag();
        nextRaid.forEach(nr::putLong);
        tag.put("NextRaid", nr);
        CompoundTag nw = new CompoundTag();
        news.forEach((k, q) -> {
            ListTag lines = new ListTag();
            for (String line : q) lines.add(StringTag.valueOf(line));
            nw.put(Long.toString(k), lines);
        });
        tag.put("News", nw);
        return tag;
    }
}

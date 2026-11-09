package net.maidens.politics;

import net.maidens.network.VillageViewPayload;
import net.maidens.village.VillageRecord;
import net.maidens.village.Villages;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The world map: biomes worked out from the seed (like a seed map: no chunk is generated or loaded), the villages
 * the network knows in that area with their stances, and the roads between them. The biome picture is made on a
 * background thread and kept for reuse; villages in view are looked up from the seed as it's opened.
 */
public final class WorldMap {
    /** Blocks per pixel, and pixels across. */
    public static final int SCALE = 8, PIXELS = 256;
    private static final int SAMPLE_Y = 96;
    private static final Map<String, byte[]> PICTURES = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return size() > 24;
        }
    };
    private static final Map<String, Integer> PALETTE_INDEX = new HashMap<>();
    private static final List<Integer> PALETTE = new ArrayList<>();

    private WorldMap() {}

    /**
     * Sends a player the map around x, z (snapped to a grid, so pictures can be reused), as seen from a village.
     * With villagesOnly the picture is left out (the screen just wants the villages it has found since).
     */
    public static void send(ServerLevel level, ServerPlayer p, int x, int z, boolean villagesOnly, long viewer) {
        int span = SCALE * PIXELS;
        final int fx = Math.floorDiv(x - span / 2, 64) * 64, fz = Math.floorDiv(z - span / 2, 64) * 64;
        int x0 = fx, z0 = fz;
        Network network = Network.get(level);
        network.lookAround(level, x0 + span / 2, z0 + span / 2, span / 2 + 200);
        if (villagesOnly) {
            reply(level, p, fx, fz, null, viewer);
            return;
        }
        // per world (seed) and dimension too: the cache outlives a world in single player
        String key = level.getSeed() + "/" + level.dimension().location() + "/" + fx + "/" + fz;
        byte[] cached;
        synchronized (PICTURES) {
            cached = PICTURES.get(key);
        }
        if (cached != null) {
            reply(level, p, fx, fz, cached, viewer);
            return;
        }
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        CompletableFuture.supplyAsync(() -> picture(source, sampler, fx, fz), Util.backgroundExecutor())
                .thenAccept(pic -> level.getServer().execute(() -> {
                    synchronized (PICTURES) {
                        PICTURES.put(key, pic);
                    }
                    if (!p.hasDisconnected()) reply(level, p, fx, fz, pic, viewer);
                }));
    }

    /** The picture around x, z, made right here (for the self-test: how long it takes, how many colours). */
    public static byte[] pictureForTest(ServerLevel level, int x, int z) {
        int span = SCALE * PIXELS;
        return picture(level.getChunkSource().getGenerator().getBiomeSource(), level.getChunkSource().randomState().sampler(), x - span / 2, z - span / 2);
    }

    /** One biome sample per pixel, as palette indices. */
    private static byte[] picture(BiomeSource source, Climate.Sampler sampler, int x0, int z0) {
        byte[] out = new byte[PIXELS * PIXELS];
        for (int pz = 0; pz < PIXELS; pz++)
            for (int px = 0; px < PIXELS; px++) {
                Holder<Biome> b = source.getNoiseBiome((x0 + px * SCALE) >> 2, SAMPLE_Y >> 2, (z0 + pz * SCALE) >> 2, sampler);
                out[pz * PIXELS + px] = (byte) colourIndex(b);
            }
        return out;
    }

    private static synchronized int colourIndex(Holder<Biome> b) {
        String key = b.unwrapKey().map(k -> k.location().toString()).orElse("?");
        return PALETTE_INDEX.computeIfAbsent(key, k -> {
            PALETTE.add(BiomeColours.of(k, b));
            return PALETTE.size() - 1;
        });
    }

    private static void reply(ServerLevel level, ServerPlayer p, int x0, int z0, @Nullable byte[] pic, long viewer) {
        CompoundTag t = new CompoundTag();
        if (pic != null) {
            t.putIntArray("Area", new int[]{x0, z0, SCALE, PIXELS});
            t.putByteArray("Pixels", pic);
            synchronized (WorldMap.class) {
                t.putIntArray("Palette", PALETTE.stream().mapToInt(Integer::intValue).toArray());
            }
        }
        Network network = Network.get(level);
        KnownVillage self = network.village(viewer);
        VillageRecord home = Villages.get(level).byId(viewer);
        boolean mayDecide = home != null && Diplomacy.rightToDecide(level, home, p) != null;
        t.putLong("Viewer", viewer);
        t.putBoolean("MayDecide", mayDecide);
        int span = SCALE * PIXELS;
        ListTag villages = new ListTag();
        for (KnownVillage v : network.all()) {
            if (v.x < x0 - 200 || v.z < z0 - 200 || v.x > x0 + span + 200 || v.z > z0 + span + 200) continue;
            CompoundTag vt = new CompoundTag();
            vt.putLong("Id", v.id);
            vt.putString("Name", v.name);
            vt.putIntArray("Pos", new int[]{v.x, v.z});
            vt.putBoolean("Maiden", v.maiden);
            vt.putBoolean("Visited", network.isVisited(v.id));
            vt.putString("Style", v.style);
            vt.putInt("Temper", v.temperament.ordinal());
            VillageRecord rec = Villages.get(level).byId(v.id);
            vt.putBoolean("Walled", v.maiden && (rec != null && rec.wallBox != null || net.maidens.village.Fortify.rolls(level, v.id)));
            if (self != null && v.maiden && v.id != self.id) {
                vt.putInt("Ours", network.stance(self, v).ordinal());
                vt.putInt("Theirs", network.stance(v, self).ordinal());
                vt.putInt("Distance", (int) Math.round(self.distanceTo(v)));
                vt.putBoolean("Neighbour", self.distanceTo(v) <= Network.NEIGHBOUR_RANGE);
            }
            List<String> news = network.news(v.id);
            if (!news.isEmpty()) vt.putString("News", news.get(0));
            villages.add(vt);
        }
        t.put("Villages", villages);
        ListTag roads = new ListTag();
        for (Road r : network.roads()) {
            CompoundTag rt = new CompoundTag();
            rt.putIntArray("Ends", r.ends());
            rt.putIntArray("Built", new int[]{r.builtFromA(), r.builtFromB(), r.length()});
            roads.add(rt);
        }
        t.put("Roads", roads);
        PacketDistributor.sendToPlayer(p, new VillageViewPayload(VillageViewPayload.WORLD, t));
    }
}

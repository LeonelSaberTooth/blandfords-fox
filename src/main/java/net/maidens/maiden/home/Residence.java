package net.maidens.maiden.home;

import net.maidens.maiden.Maiden;
import net.maidens.village.VillageRecord;
import net.maidens.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Where a village maiden belongs: her village, the bed she sleeps in, and the workstation that gives her a
 * profession (and so her trades). Beds and workstations are claimed the same way villagers claim them,
 * so a maiden and a villager never share one. Once she has traded, her profession stays even if her
 * workstation is broken (as for villagers); she then looks for another of the same kind. She looks all over her
 * village, so a house built at its edge still finds its maiden.
 * Maidens outside villages (the lone house) don't use any of this.
 */
public final class Residence {
    private static final int SEARCH = 48;
    private static final long NO_VILLAGE = Long.MIN_VALUE;

    private final Maiden m;
    private long village = NO_VILLAGE;
    @Nullable private BlockPos bed;
    @Nullable private BlockPos jobSite;
    @Nullable private VillagerProfession profession;
    private long nextJobSearch, nextBedSearch;

    public Residence(Maiden m) {
        this.m = m;
    }

    public boolean inVillage() {
        return village != NO_VILLAGE;
    }

    public long village() {
        return village;
    }

    @Nullable
    public BlockPos bed() {
        return bed;
    }

    @Nullable
    public BlockPos jobSite() {
        return jobSite;
    }

    @Nullable
    public VillagerProfession profession() {
        return profession;
    }

    public void joinVillage(long id) {
        village = id;
    }

    /** Every few seconds: keep a bed and a workstation if she can. */
    public void tick() {
        if (!inVillage() || m.tickCount % 100 != 37 || !(m.level() instanceof ServerLevel level)) return;
        PoiManager poi = level.getPoiManager();
        long now = level.getGameTime();
        // her bed (only checked while its chunk is loaded: looking at unloaded ones reads them from disk)
        if (bed != null && level.isLoaded(bed) && !poi.exists(bed, h -> h.is(PoiTypes.HOME))) bed = null;
        if (bed == null && now >= nextBedSearch) {
            bed = take(level, poi, h -> h.is(PoiTypes.HOME));
            if (bed != null) m.setHome(bed);
            else nextBedSearch = now + 600;                // no free bed: look again in 30 s
        }
        // her workstation
        if (jobSite != null && level.isLoaded(jobSite) && !poi.exists(jobSite, h -> profession != null && profession.heldJobSite().test(h))) jobSite = null;
        if (jobSite == null && now >= nextJobSearch) {
            nextJobSearch = now + 1200;                    // no free workstation: look again in a minute
            boolean locked = profession != null && m.trading().xp() > 0;
            Predicate<Holder<PoiType>> wanted = locked ? profession.acquirableJobSite() : VillagerProfession.ALL_ACQUIRABLE_JOBS;
            jobSite = take(level, poi, wanted);
            if (jobSite != null && !locked) setProfession(professionAt(poi, jobSite));
            else if (jobSite == null && !locked && profession != null) setProfession(null);
        }
    }

    private void setProfession(@Nullable VillagerProfession p) {
        if (p == profession) return;
        profession = p;
        m.trading().resetForNewProfession();
    }

    /**
     * Claims the nearest free one: anywhere in her village (including buildings added at its edge) when all of it
     * is loaded, else within 48 blocks of her. Never searches unloaded land (that would read it from disk).
     */
    @Nullable
    private BlockPos take(ServerLevel level, PoiManager poi, Predicate<Holder<PoiType>> type) {
        BlockPos from = m.blockPosition();
        int radius = SEARCH;
        VillageRecord v = Villages.get(level).byId(village);
        if (v != null) {
            int r = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 48;
            BlockPos c = v.centre();
            if (level.hasChunksAt(c.getX() - r, c.getZ() - r, c.getX() + r, c.getZ() + r)) {
                from = c;
                radius = r;
            }
        }
        if (radius == SEARCH && !level.hasChunksAt(from.getX() - SEARCH, from.getZ() - SEARCH, from.getX() + SEARCH, from.getZ() + SEARCH)) return null;
        Optional<BlockPos> found = poi.take(type, (h, pos) -> true, from, radius);
        return found.orElse(null);
    }

    @Nullable
    private static VillagerProfession professionAt(PoiManager poi, BlockPos pos) {
        Optional<Holder<PoiType>> type = poi.getType(pos);
        if (type.isEmpty()) return null;
        for (VillagerProfession p : BuiltInRegistries.VILLAGER_PROFESSION) {
            if (p != VillagerProfession.NONE && p.heldJobSite().test(type.get())) return p;
        }
        return null;
    }

    /** She died: her bed and workstation are free for someone else. */
    public void release() {
        if (!(m.level() instanceof ServerLevel level)) return;
        PoiManager poi = level.getPoiManager();
        if (bed != null) poi.release(bed);
        if (jobSite != null) poi.release(jobSite);
        bed = jobSite = null;
    }

    /** Her profession's name for the menu ("Librarian"), or "" if she has none. */
    public String professionName() {
        if (profession == null) return "";
        String s = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).getPath().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public void save(CompoundTag tag) {
        if (!inVillage()) return;
        tag.putLong("Village", village);
        if (bed != null) tag.putLong("Bed", bed.asLong());
        if (jobSite != null) tag.putLong("JobSite", jobSite.asLong());
        if (profession != null) tag.putString("Profession", BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).toString());
    }

    public void load(CompoundTag tag) {
        village = tag.contains("Village") ? tag.getLong("Village") : NO_VILLAGE;
        bed = tag.contains("Bed") ? BlockPos.of(tag.getLong("Bed")) : null;
        jobSite = tag.contains("JobSite") ? BlockPos.of(tag.getLong("JobSite")) : null;
        ResourceLocation key = tag.contains("Profession") ? ResourceLocation.tryParse(tag.getString("Profession")) : null;
        profession = key == null ? null : BuiltInRegistries.VILLAGER_PROFESSION.getOptional(key).orElse(null);
    }
}

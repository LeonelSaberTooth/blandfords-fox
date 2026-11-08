package net.maidens.test;

import com.mojang.datafixers.util.Pair;
import net.maidens.maiden.Maiden;
import net.maidens.maiden.home.Residence;
import net.maidens.core.MaidensConfig;
import net.maidens.maiden.MaidenMenu;
import net.maidens.network.ActionPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.maidens.village.Buildings;
import net.maidens.village.Housing;
import net.maidens.village.Upkeep;
import net.maidens.village.VillageEvents;
import net.maidens.village.VillageRecord;
import net.maidens.village.VillageSocial;
import net.maidens.village.Villages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The self test's village part (run with every village set to be a maiden village): finds the nearest village,
 * loads it, and checks that its villagers became maidens who claim beds and workstations and trade by profession.
 */
final class VillageCheck {
    private final Consumer<String> log;
    private BlockPos village;
    private ServerPlayer tester;

    VillageCheck(Consumer<String> log) {
        this.log = log;
    }

    void setTester(ServerPlayer p) {
        tester = p;
    }

    private int ticks;

    /** One tick of the check; true when done. */
    boolean step(ServerLevel level, BlockPos from) {
        int t = ticks++;
        if (t == 0) {
            HolderSet<Structure> villages = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(StructureTags.VILLAGE).orElseThrow();
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, villages, from, 100, false);
            if (found == null) {
                log.accept("village: NONE FOUND within 100 chunks");
                return true;
            }
            village = found.getFirst();
            log.accept("village: " + found.getSecond().unwrapKey().map(k -> k.location().getPath()).orElse("?") + " at " + village.toShortString());
            for (int cx = (village.getX() >> 4) - 5; cx <= (village.getX() >> 4) + 5; cx++)
                for (int cz = (village.getZ() >> 4) - 5; cz <= (village.getZ() >> 4) + 5; cz++) {
                    level.setChunkForced(cx, cz, true);
                    level.getChunk(cx, cz);
                }
            return false;
        }
        if (t == 100 || t == 600) {
            AABB area = new AABB(village).inflate(96, 64, 96);
            List<Maiden> maidens = level.getEntitiesOfClass(Maiden.class, area);
            List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area, v -> !v.isRemoved());
            int inVillage = 0, beds = 0, sites = 0;
            TreeMap<String, Integer> jobs = new TreeMap<>();
            for (Maiden m : maidens) {
                Residence r = m.residence();
                if (r.inVillage()) inVillage++;
                if (r.bed() != null) beds++;
                if (r.jobSite() != null) sites++;
                jobs.merge(r.professionName().isEmpty() ? "none" : r.professionName(), 1, Integer::sum);
            }
            log.accept("  after " + t + " ticks: " + maidens.size() + " maidens (" + inVillage + " know their village), "
                    + villagers.size() + " villagers; " + beds + " have a bed, " + sites + " a workstation; professions " + jobs);
            log.accept("  maiden villages on record: " + count(Villages.get(level).all()));
            for (Villager vi : villagers) {
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                vi.addAdditionalSaveData(tag);
                log.accept("  villager left at " + vi.blockPosition().toShortString() + ": in a village piece " + (Villages.villageAt(level, vi.blockPosition()) != null)
                        + ", baby " + vi.isBaby() + ", profession " + vi.getVillagerData().getProfession() + ", age " + vi.tickCount
                        + ", removed " + vi.getRemovalReason() + ", entity-ticking " + level.isPositionEntityTicking(vi.blockPosition()) + ", noAI " + vi.isNoAi() + ", flag " + tag.getBoolean("AssignProfessionWhenSpawned"));
            }
            for (Maiden m : maidens) if (!m.residence().inVillage()) log.accept("  maiden without a village at " + m.blockPosition().toShortString() + ", age " + m.tickCount + ", noAI " + m.isNoAi() + ", name " + m.name());
            if (t == 600) log.accept("  free workstations in the area: " + level.getPoiManager().getCountInRange(
                    h -> net.minecraft.world.entity.npc.VillagerProfession.ALL_ACQUIRABLE_JOBS.test(h), village, 96,
                    net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
                    + ", taken " + level.getPoiManager().getCountInRange(h -> net.minecraft.world.entity.npc.VillagerProfession.ALL_ACQUIRABLE_JOBS.test(h), village, 96,
                    net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.IS_OCCUPIED));
            if (t == 600) {
                for (Maiden m : maidens) {
                    if (m.residence().profession() == null) continue;
                    MerchantOffer o = m.getOffers().isEmpty() ? null : m.getOffers().get(0);
                    log.accept("  " + m.name() + ", " + m.title() + ", trades as " + m.trading().profession()
                            + (o == null ? ", no offers" : ", first offer " + o.getCostA().getCount() + " " + o.getCostA().getItem() + " -> " + o.getResult().getCount() + " " + o.getResult().getItem())
                            + ", bed " + (m.residence().bed() == null ? "none" : m.residence().bed().toShortString()));
                    break;
                }
            }
        }
        if (t == 620) {
            social(level);
            damage(level);
            stage = 1;
            stageStart = t;
            return false;
        }
        int st = t - stageStart;
        if (t < 620 || st == 0 || st % 400 != 0) return false;
        switch (stage) {
            case 1 -> {
                if (repairs(level, st)) next(t, () -> startHouse(level));
            }
            case 2 -> {
                if (house(level, st)) next(t, () -> arrival(level));
            }
            case 3 -> {
                arrived(level);
                next(t, () -> night(level));
            }
            case 4 -> {
                if (st < 1600) return false;
                bedtime(level);
                wishes(level);
                next(t, () -> raid(level));
            }
            case 5 -> {
                if (raidCheck(level, st)) return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    private int stage, stageStart;

    private void next(int t, Runnable start) {
        stage++;
        stageStart = t;
        start.run();
    }

    // ------------------------------------------------------------------ Phase 4: a new house, a newcomer

    private Buildings.Plan plan;
    private Maiden newcomer;

    private void startHouse(ServerLevel level) {
        if (record == null) return;
        level.setDayTime(1000);
        plan = Housing.houseFor(level, record);
        if (plan == null) {
            log.accept("housing: no site found for a house (style " + record.style + ", " + Buildings.templates(level, record.style).size() + " buildings known); " + Buildings.lastSearch);
            return;
        }
        log.accept("housing: " + record.style + " buildings known: " + Buildings.templates(level, record.style).size() + "; building "
                + Buildings.shortName(plan.template()) + " at " + plan.box().getCenter().toShortString() + ", " + plan.blocks().size() + " blocks, "
                + plan.clear().size() + " to clear, " + plan.beds() + " bed(s), door " + (plan.door() == null ? "none" : plan.door().toShortString())
                + ", pieces now " + record.blueprint.pieceCount() + "; " + Buildings.lastSearch);
    }

    private boolean house(ServerLevel level, int t) {
        if (plan == null) return true;
        int left = 0, inWay = 0;
        for (var e : plan.blocks().entrySet()) if (!level.getBlockState(e.getKey()).is(e.getValue().getBlock())) left++;
        for (BlockPos p : plan.clear()) if (!level.getBlockState(p).isAir()) inWay++;
        log.accept("  after " + t + " ticks: " + left + " blocks still to place, " + inWay + " to clear, " + Upkeep.choreSummary(level, record)
                + ", still building: " + record.blueprint.isBuilding() + "; " + goals(level));
        return (left == 0 && !record.blueprint.isBuilding()) || t >= 6000;
    }

    private void arrival(ServerLevel level) {
        if (record == null) return;
        newcomer = Housing.arrive(level, record);
        log.accept("newcomer: " + (newcomer == null ? "none (no free bed)" : newcomer.name() + " arrived at " + newcomer.blockPosition().toShortString()));
    }

    // ------------------------------------------------------------------ Phase 5: their day, requests, raids

    private void night(ServerLevel level) {
        level.setDayTime(11000);
        log.accept("schedule: evening (time 11000): home at 11000, bed at 12000");
    }

    private void bedtime(ServerLevel level) {
        List<Maiden> rs = VillageSocial.residents(level, record);
        int free = 0, withBed = 0, asleep = 0;
        for (Maiden m : rs) {
            if (!m.hasVillageDuties()) continue;
            free++;
            if (m.residence().bed() != null) withBed++;
            if (m.isSleeping()) asleep++;
        }
        log.accept("  at time 12600: " + asleep + " asleep of " + free + " free residents (" + withBed + " with a bed)");
        int shown = 0;
        for (Maiden m : rs) {
            if (!m.hasVillageDuties() || m.isSleeping() || m.residence().bed() == null || shown++ >= 4) continue;
            BlockPos b = m.residence().bed();
            log.accept("    " + m.name() + " at " + m.blockPosition().toShortString() + ", bed " + b.toShortString() + " ("
                    + Math.round(Math.sqrt(m.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5))) + " away), bed block "
                    + level.getBlockState(b).getBlock() + ", goals: " + m.runningGoals() + ", free " + m.isFree() + ", target " + m.getTarget());
        }
        level.setDayTime(1000);
    }

    private void wishes(ServerLevel level) {
        List<Maiden> rs = VillageSocial.residents(level, record);
        if (rs.isEmpty()) return;
        int open = 0;
        for (Maiden m : rs) if (m.wishes().item() != null) open++;
        Maiden m = rs.get(0);
        tester.moveTo(m.getX() + 1, m.getY(), m.getZ());
        m.wishes().setForTest(Items.BREAD, 5);
        tester.getInventory().clearContent();
        tester.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 3));
        int before = m.relationships().affection(tester);
        MaidenMenu.handle(m, tester, ActionPayload.GIFT, 0);
        String shortReply = "bread x3 -> still wants " + (m.wishes().item() != null);
        tester.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 5));
        MaidenMenu.handle(m, tester, ActionPayload.GIFT, 0);
        log.accept("requests: " + open + " open requests on the board; " + shortReply + "; bread x5 -> still wants " + (m.wishes().item() != null)
                + ", emeralds " + tester.getInventory().countItem(Items.EMERALD) + ", affection " + before + " -> " + m.relationships().affection(tester));
    }

    private void raid(ServerLevel level) {
        BlockPos at = record.bell() != null ? record.bell() : record.centre();
        tester.moveTo(at.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ() + 0.5);
        Raid raid = level.getRaids().createOrExtendRaid(tester, at);
        log.accept("raid: " + (raid == null ? "couldn't start one here" : "started at " + at.toShortString()));
    }

    private boolean raidCheck(ServerLevel level, int t) {
        List<Maiden> rs = VillageSocial.residents(level, record);
        int fighters = 0, armed = 0;
        for (Maiden m : rs) {
            if (m.isFighter()) fighters++;
            if (!m.getMainHandItem().isEmpty()) armed++;
        }
        Raid raid = level.getRaidAt(record.centre());
        int raiders = raid == null ? 0 : raid.getTotalRaidersAlive();
        log.accept("  after " + t + " ticks: " + fighters + " of " + rs.size() + " residents fighting (" + armed + " armed), "
                + (raid == null ? "no raid" : "raid " + (raid.isActive() ? "active" : "over") + ", wave " + raid.getGroupsSpawned() + ", " + raiders + " raiders alive"));
        return t >= 1200;
    }

    private void arrived(ServerLevel level) {
        if (newcomer != null) log.accept("  newcomer's bed after 400 ticks: " + (newcomer.residence().bed() == null ? "none" : newcomer.residence().bed().toShortString()));
    }

    // ------------------------------------------------------------------ Phase 3: repairs and road clearing

    private VillageRecord record;
    private BlockPos blast, obstacle;

    private void damage(ServerLevel level) {
        record = Villages.get(level).at(village);
        if (record == null) for (VillageRecord r : Villages.get(level).all()) record = r;
        if (record == null) return;
        level.setDayTime(1000);
        BoundingBox b = record.box;
        for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
            BlockState e = record.blueprint.expected(p);
            if (e == null) continue;
            BlockState now = level.getBlockState(p);
            if (blast == null && e.is(BlockTags.PLANKS) && now == e) blast = p.immutable();
            if (obstacle == null && e.is(Upkeep.ROAD) && now == e && level.getBlockState(p.above()).isAir() && record.blueprint.expected(p.above()) == null) obstacle = p.above().immutable();
            if (blast != null && obstacle != null) break;
        }
        if (blast != null) {
            level.explode(null, blast.getX() + 0.5, blast.getY() + 0.5, blast.getZ() + 0.5, 2.0F, Level.ExplosionInteraction.TNT);
            log.accept("repairs: exploded at " + blast.toShortString() + ", " + Upkeep.missingNear(level, record, blast, 4) + " blueprint blocks missing nearby");
        } else log.accept("repairs: no plank block found to blow up");
        if (obstacle != null) {
            level.setBlock(obstacle, Blocks.COBBLESTONE.defaultBlockState(), 3);
            log.accept("  cobblestone put on the road at " + obstacle.toShortString());
        } else log.accept("  no road block found");
    }

    private boolean repairs(ServerLevel level, int t) {
        if (record == null) return true;
        int missing = blast == null ? 0 : Upkeep.missingNear(level, record, blast, 4);
        boolean blocked = obstacle != null && !level.getBlockState(obstacle).isAir();
        log.accept("  after " + t + " ticks: " + missing + " missing near the blast, road " + (obstacle == null ? "-" : blocked ? "still blocked" : "cleared")
                + ", " + Upkeep.choreSummary(level, record) + "; " + goals(level));
        return (missing == 0 && !blocked) || t >= 4000;
    }

    /** Phase 2: the village's name and blueprint, anger, gossip and citizenship. */
    private void social(ServerLevel level) {
        Villages villages = Villages.get(level);
        VillageRecord v = villages.at(village);
        if (v == null) {
            for (VillageRecord r : villages.all()) v = r;
        }
        if (v == null) {
            log.accept("social: NO VILLAGE RECORD");
            return;
        }
        List<Maiden> rs = VillageSocial.residents(level, v);
        log.accept("social: " + v.name + ", " + rs.size() + " residents loaded, blueprint " + v.blueprint.recordedPieces() + "/"
                + v.blueprint.pieceCount() + " pieces, " + v.blueprint.blockCount() + " blocks");
        if (rs.size() < 2) return;
        tester.moveTo(v.centre().getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, v.centre().getX(), v.centre().getZ()), v.centre().getZ() + 0.5);
        log.accept("  greeting a stranger: " + VillageSocial.greeting(rs.get(0), tester));
        // anger: break blueprint blocks
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(v.box.minX(), v.box.minY(), v.box.minZ(), v.box.maxX(), v.box.maxY(), v.box.maxZ())) {
            BlockState s = level.getBlockState(p);
            BlockState e = v.blueprint.expected(p);
            if (e != null && e == s && s.isCollisionShapeFullBlock(level, p) && !s.hasBlockEntity()) blocks.add(p.immutable());
            if (blocks.size() >= 17) break;
        }
        for (Maiden m : rs) m.relationships().setAffection(tester, 30);
        BlockPos first = blocks.isEmpty() ? null : blocks.get(0);
        if (first != null) {
            log.accept("  breaking " + level.getBlockState(first).getBlock() + " at " + first.toShortString());
            breakAs(level, first);
        }
        log.accept("  anger after 1 block: " + affections(rs) + " (from 30, -" + MaidensConfig.ANGER_PER_BLOCK.get() + " each expected)");
        for (int i = 1; i < blocks.size(); i++) breakAs(level, blocks.get(i));
        log.accept("  anger after " + blocks.size() + " blocks: " + affections(rs) + " (capped at -" + MaidensConfig.ANGER_PER_DAY.get() + " a day)");
        // gossip: one maiden likes the tester more, some others hear of it
        for (Maiden m : rs) m.relationships().setAffection(tester, 10);
        rs.get(0).relationships().change(tester, 8);
        log.accept("  gossip: maiden 0 +8, everyone now " + affections(rs) + " (others hear with " + MaidensConfig.GOSSIP_SHARE_PERCENT.get() + "% chance)");
        // citizenship by being liked
        for (Maiden m : rs) m.relationships().setAffection(tester, 60);
        rs.get(0).relationships().change(tester, 1);
        log.accept("  citizen after everyone likes the tester 60: " + v.isCitizen(tester.getUUID()));
        log.accept("  greeting a citizen: " + VillageSocial.greeting(rs.get(1), tester));
        if (blocks.size() > 17 - 1) {
            BlockPos last = blocks.get(blocks.size() - 1);
            level.setBlock(last, v.blueprint.expected(last) == null ? level.getBlockState(last) : v.blueprint.expected(last), 3);
            String before = affections(rs);
            breakAs(level, last);
            log.accept("  citizen breaks a block: affection " + before + " -> " + affections(rs) + ", still in blueprint: " + (v.blueprint.expected(last) != null));
        }
    }

    /** What the residents are doing: how many run each goal. */
    private String goals(ServerLevel level) {
        TreeMap<String, Integer> n = new TreeMap<>();
        for (Maiden m : VillageSocial.residents(level, record)) {
            String g = m.runningGoals();
            for (String s : (g.isEmpty() ? "idle" : g).split(" ")) n.merge(s, 1, Integer::sum);
        }
        return n.toString();
    }

    private void breakAs(ServerLevel level, BlockPos p) {
        VillageEvents.playerBroke(level, tester, p, level.getBlockState(p));
        level.destroyBlock(p, false);
    }

    private String affections(List<Maiden> rs) {
        StringBuilder s = new StringBuilder();
        for (Maiden m : rs) s.append(m.relationships().affection(tester)).append(' ');
        return s.toString().trim();
    }

    private static int count(Iterable<?> it) {
        int n = 0;
        for (Object o : it) n++;
        return n;
    }
}

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
import net.maidens.politics.Diplomacy;
import net.maidens.politics.KnownVillage;
import net.maidens.politics.Network;
import net.maidens.politics.Stance;
import net.maidens.village.Royals;
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
import net.minecraft.world.phys.Vec3;

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
            // a wide area, so maidens keep ticking wherever they go (new houses can be built well outside)
            for (int cx = (village.getX() >> 4) - 8; cx <= (village.getX() >> 4) + 8; cx++)
                for (int cz = (village.getZ() >> 4) - 8; cz <= (village.getZ() >> 4) + 8; cz++) {
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
            politics(level);
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
                if (raidCheck(level, st)) next(t, () -> warRaid(level));
            }
            case 6 -> {
                if (warCheck(level, st)) next(t, () -> view(level));
            }
            case 7 -> next(t, () -> fortify(level));
            case 8 -> {
                if (fortified(level, st)) {
                    finder(level);
                    people(level);
                    next(t, () -> economyStart(level));
                }
            }
            case 9 -> {
                // a day: at work, at the market, at home; then the day's harvest comes in
                if (st == 1200) {
                    economyWorkers(level, "at work");
                    day(level, "at work");
                    level.setDayTime(level.getDayTime() / 24000 * 24000 + 9300);
                }
                if (st == 1600) {
                    day(level, "at the market");
                    level.setDayTime(level.getDayTime() / 24000 * 24000 + 13300);
                }
                if (st == 2000) day(level, "at home");
                if (st < 2800) return false;
                economyCheck(level);
                raidLead(level);
                return true;
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
        for (BlockPos p : plan.clear()) if (!level.getBlockState(p).isAir() && record.blueprint.expected(p) == null) inWay++;
        log.accept("  after " + t + " ticks: " + left + " blocks still to place, " + inWay + " to clear, " + Upkeep.choreSummary(level, record)
                + ", still building: " + record.blueprint.isBuilding() + "; " + goals(level));
        boolean done = (left == 0 && !record.blueprint.isBuilding()) || t >= 6000;
        if (done) {
            // where the new house is, for the client test to photograph
            BlockPos c = plan.box().getCenter();
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("maidens.selftest"), "village.txt"), c.getX() + " " + c.getY() + " " + c.getZ());
            } catch (java.io.IOException ignored) {
            }
        }
        return done;
    }

    private void arrival(ServerLevel level) {
        if (record == null) return;
        newcomer = Housing.arrive(level, record);
        log.accept("newcomer: " + (newcomer == null ? "none (no free bed)" : newcomer.name() + " arrived at " + newcomer.blockPosition().toShortString()));
    }

    // ------------------------------------------------------------------ Phase 5: their day, requests, raids

    private void night(ServerLevel level) {
        level.setDayTime(14000);
        log.accept("schedule: evening (time 14000, 20:00): home until 15000, then bed");
    }

    private void bedtime(ServerLevel level) {
        List<Maiden> rs = VillageSocial.residents(level, record);
        int free = 0, withBed = 0, asleep = 0;
        for (Maiden m : rs) {
            if (!net.maidens.maiden.home.Schedule.keeps(m)) continue;
            free++;
            if (m.residence().bed() != null) withBed++;
            if (m.isSleeping()) asleep++;
        }
        log.accept("  at time " + level.getDayTime() % 24000 + ": " + asleep + " asleep of " + free + " free residents (" + withBed + " with a bed)");
        int shown = 0;
        for (Maiden m : rs) {
            if (!net.maidens.maiden.home.Schedule.keeps(m) || m.isSleeping() || m.residence().bed() == null || shown++ >= 4) continue;
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

    // ------------------------------------------------------------------ a raid from an aggressive neighbour

    private final java.util.List<Maiden> raiders = new java.util.ArrayList<>();

    private void warRaid(ServerLevel level) {
        level.setDayTime(1000);
        Network n = Network.get(level);
        KnownVillage self = n.know(level, record);
        java.util.List<KnownVillage> nb = n.neighbours(self);
        if (nb.isEmpty()) {
            log.accept("war: no neighbour to raid us");
            return;
        }
        // a chest of bread by the bell, for them to steal from
        BlockPos bell = record.bell() != null ? record.bell() : record.centre();
        BlockPos spot = null;
        for (BlockPos p : BlockPos.betweenClosed(bell.offset(-4, -1, -4), bell.offset(4, 1, 4))) {
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                spot = p.immutable();
                break;
            }
        }
        if (spot != null) {
            level.setBlock(spot, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(spot) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c)
                for (int i = 0; i < 6; i++) c.setItem(i, new ItemStack(Items.BREAD, 16));
            loot = spot;
        }
        Integer count = net.maidens.politics.WarParties.start(level, nb.get(0), record);
        log.accept("war: " + nb.get(0).name + " raids " + record.name + ": " + (count == null ? "couldn't start (no loaded edge)" : count + " raiders"));
        raiders.clear();
        raiders.addAll(level.getEntitiesOfClass(Maiden.class, AABB.of(record.box).inflate(64), m -> m.raider().active()));
        if (!raiders.isEmpty()) {
            Maiden r = raiders.get(0);
            log.accept("  a raider: " + r.name() + " at " + r.blockPosition().toShortString() + ", holding " + r.getMainHandItem() + ", fighter " + r.isFighter());
        }
    }

    private BlockPos loot;

    private boolean warCheck(ServerLevel level, int t) {
        int alive = 0, beaten = 0;
        StringBuilder states = new StringBuilder();
        for (Maiden r : raiders) {
            if (!r.isRemoved() && r.isAlive()) alive++;
            if (r.raider().surrendered()) beaten++;
            states.append(r.name()).append(' ').append(r.isRemoved() ? String.valueOf(r.getRemovalReason())
                            : (int) r.getHealth() + "hp " + (int) Math.sqrt(r.distanceToSqr(Vec3.atCenterOf(record.bell() != null ? record.bell() : record.centre()))) + " from the bell"
                            + (r.getTarget() != null ? " fighting " + r.getTarget().getName().getString() : "") + " " + r.runningGoals())
                    .append(r.raider().surrendered() ? " beaten" : "").append(", ");
        }
        log.accept("  raiders: " + states);
        if (loot != null && level.getBlockEntity(loot) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c) {
            int left = 0;
            for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) left++;
            log.accept("  bread chest by the bell: " + left + " of 6 stacks left");
        }
        float lowest = 100;
        int dead = 0;
        for (Maiden m : VillageSocial.residents(level, record)) lowest = Math.min(lowest, m.getHealth());
        log.accept("  after " + t + " ticks: " + alive + " raiders still here, " + beaten + " beaten; raid on " + net.maidens.politics.WarParties.raidOn(record.id)
                + "; weakest resident " + lowest + " hp");
        if (!net.maidens.politics.WarParties.raidOn(record.id) || t >= 2400) {
            log.accept("  news: " + Network.get(level).news(record.id).subList(0, Math.min(4, Network.get(level).news(record.id).size())));
            return true;
        }
        return false;
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

    /** Royals, the network of villages from the seed, stances and the right to decide. */
    private void politics(ServerLevel level) {
        VillageRecord v = Villages.get(level).at(village);
        if (v == null) for (VillageRecord r : Villages.get(level).all()) v = r;
        if (v == null) return;
        List<Maiden> rs = VillageSocial.residents(level, v);
        Royals.tick(level, v, rs);
        Maiden royal = null;
        for (Maiden m : rs) if (m.isRoyal()) royal = m;
        log.accept("politics: royal " + (royal == null ? "NONE" : royal.name() + " (" + royal.title() + ")") + ", royals on record " + v.royals.size());
        if (royal != null) log.accept("  hiring the royal: \"" + royal.employment().hire(tester, net.maidens.maiden.work.Job.FARM) + "\"");
        Network n = Network.get(level);
        KnownVillage self = n.know(level, v);
        n.forgetCheckedForTest();
        long start = System.nanoTime();
        n.lookAround(level, self.x, self.z, Network.NEIGHBOUR_RANGE);
        int checks = 0;
        long slowest = 0;
        while (n.busy() && checks < 2000) {
            long s0 = System.nanoTime();
            n.tick(level);
            slowest = Math.max(slowest, System.nanoTime() - s0);
            checks++;
        }
        log.accept("  slowest single check: " + slowest / 1_000_000 + " ms");
        log.accept("  network: " + checks + " spots checked in " + (System.nanoTime() - start) / 1_000_000 + " ms; " + n.all().size() + " villages known, "
                + n.all().stream().filter(k -> k.maiden).count() + " of them maiden villages; " + v.name + " is " + self.temperament.label + " by temperament");
        List<KnownVillage> nb = n.neighbours(self);
        for (KnownVillage o : nb) log.accept("    " + o.name + " (" + o.style + ") " + Math.round(self.distanceTo(o)) + " blocks: we're "
                + n.stance(self, o).label + ", they're " + n.stance(o, self).label);
        log.accept("  may the tester decide? " + Diplomacy.rightToDecide(level, v, tester) + " - " + Diplomacy.howToEarnIt(level, v, tester));
        v.addDefender(tester.getUUID());
        log.accept("  after defending it: " + Diplomacy.rightToDecide(level, v, tester));
        if (!nb.isEmpty()) {
            log.accept("  set stance: \"" + Diplomacy.setStance(level, v, tester, nb.get(0), Stance.AGGRESSIVE) + "\" -> now " + n.stance(self, nb.get(0)).label);
            log.accept("  news: " + n.news(v.id));
        }
        // roads: three days of building
        for (int d = 0; d < 3; d++) n.buildRoadsForTest(level);
        for (int i = 0; i < 40; i++) n.slowTick(level);
        for (net.maidens.politics.Road r : n.roads()) {
            KnownVillage a = n.village(r.a), b = n.village(r.b);
            log.accept("  road " + (a == null ? "?" : a.name) + " - " + (b == null ? "?" : b.name) + ": " + r.length() + " blocks, "
                    + Math.round(r.progress() * 100) + "% built, " + r.laidCount() + " steps laid in loaded land");
        }
    }

    /** The bell screen's data, the world map's picture, and building where a citizen clicks on the map. */
    private void view(ServerLevel level) {
        VillageRecord v = Villages.get(level).at(village);
        if (v == null) for (VillageRecord r : Villages.get(level).all()) v = r;
        if (v == null) return;
        long start = System.nanoTime();
        net.minecraft.nbt.CompoundTag t = net.maidens.village.VillageView.build(level, v, tester);
        long ms = (System.nanoTime() - start) / 1_000_000;
        int beds = 0;
        for (net.minecraft.nbt.Tag e : t.getList("Residents", 10)) if (((net.minecraft.nbt.CompoundTag) e).contains("Bed")) beds++;
        java.util.Set<String> named = new java.util.TreeSet<>();
        for (net.minecraft.nbt.Tag e : t.getList("Pieces", 10)) named.add(((net.minecraft.nbt.CompoundTag) e).getString("Name"));
        log.accept("view: built in " + ms + " ms, " + t.toString().length() / 1024 + " KB; map " + java.util.Arrays.toString(t.getIntArray("Map"))
                + "; " + t.getList("Pieces", 10).size() + " pieces " + named + "; " + t.getList("Residents", 10).size() + " residents, " + beds
                + " with a bed; " + t.getList("Templates", 10).size() + " buildings to choose from; " + t.getList("Neighbours", 10).size()
                + " neighbours; royal \"" + t.getString("Royal") + "\", citizen " + t.getBoolean("Citizen") + ", right \"" + t.getString("Right") + "\"");
        start = System.nanoTime();
        byte[] pic = net.maidens.politics.WorldMap.pictureForTest(level, v.centre().getX(), v.centre().getZ());
        java.util.Set<Byte> colours = new java.util.HashSet<>();
        for (byte b : pic) colours.add(b);
        log.accept("  world map picture: " + (System.nanoTime() - start) / 1_000_000 + " ms, " + colours.size() + " biome colours");
        // building where clicked: on the bell (refused), then around the village until a spot is taken
        String first = t.getList("Templates", 10).isEmpty() ? "?" : t.getList("Templates", 10).getCompound(0).getString("Name");
        log.accept("  build " + first + " on the bell: \"" + net.maidens.village.VillageMenu.buildAt(level, v, tester, first, v.centre(), net.minecraft.world.level.block.Rotation.NONE) + "\"");
        if (v.blueprint.isBuilding()) {
            log.accept("  (still building " + v.project + ", can't try a free spot)");
            return;
        }
        int tries = 0;
        String last = "";
        outer:
        for (int r = 14; r <= 44; r += 6)
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4;
                BlockPos c = v.centre().offset((int) (Math.cos(ang) * (v.box.getXSpan() / 2.0 + r)), 0, (int) (Math.sin(ang) * (v.box.getZSpan() / 2.0 + r)));
                tries++;
                String why = net.maidens.village.VillageMenu.buildAt(level, v, tester, first, c, net.minecraft.world.level.block.Rotation.values()[a % 4]);
                if (why.startsWith("The maidens will build")) {
                    log.accept("  clicked spot " + c.toShortString() + " after " + tries + " tries: \"" + why + "\", project " + v.project + ", building " + v.blueprint.isBuilding());
                    break outer;
                }
                last = why;
            }
        if (!v.blueprint.isBuilding()) log.accept("  no spot found in " + tries + " tries, last: \"" + last + "\"");
    }

    // ------------------------------------------------------------------ castle and walls

    private void fortify(ServerLevel level) {
        VillageRecord v = record;
        if (v == null) return;
        // all the land around it, loaded and kept loaded (the walls stand well outside the houses)
        int r = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 96;
        BlockPos c = v.centre();
        long start = System.nanoTime();
        for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++)
            for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
            }
        log.accept("fortify: land loaded in " + (System.nanoTime() - start) / 1_000_000 + " ms");
        MaidensConfig.FORTIFIED_VILLAGE_CHANCE.set(0.35);
        start = System.nanoTime();
        net.maidens.village.Fortify.Report rep = net.maidens.village.Fortify.fortify(level, v, true);
        log.accept("  planned in " + (System.nanoTime() - start) / 1_000_000 + " ms: castle " + (rep.castle() == null ? "NONE" : rep.castle().getCenter().toShortString())
                + ", " + rep.houses() + " houses beside it; walls " + (rep.walls() == null ? "NONE" : rep.walls().columns() + " columns, " + rep.walls().blocks() + " blocks, "
                + rep.walls().gates() + " gates, " + rep.walls().towers() + " towers, " + rep.walls().pieces() + " pieces"));
        log.accept("  castle site search: " + net.maidens.village.Castle.lastSearch);
        // for the client test's pictures (the village centre if there's no castle)
        BlockPos cc = rep.castle() != null ? rep.castle().getCenter() : c;
        int cy = rep.castle() != null ? rep.castle().minY() : level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        int vy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("maidens.selftest"), "castle.txt"), cc.getX() + " " + cy + " " + cc.getZ()
                    + " " + c.getX() + " " + vy + " " + c.getZ());
        } catch (java.io.IOException ignored) {
        }
        fortifyStart = System.nanoTime();
    }

    private long fortifyStart;

    private boolean fortified(ServerLevel level, int t) {
        VillageRecord v = record;
        int[] wrong = {0, 0};
        v.blueprint.forEachBlock((p, s) -> {
            if (!level.isLoaded(p)) return;
            wrong[1]++;
            if (Upkeep.missing(level.getBlockState(p), s)) wrong[0]++;
        });
        log.accept("  after " + t + " ticks: still to place all at once " + v.blueprint.hasInstant() + "; " + wrong[0] + " of " + wrong[1]
                + " blueprint blocks missing; " + VillageSocial.residents(level, v).size() + " residents; " + goals(level));
        if (v.castle != null) {
            BlockPos throne = null;
            for (BlockPos p : BlockPos.betweenClosed(v.castle.minX(), v.castle.minY(), v.castle.minZ(), v.castle.maxX(), v.castle.maxY(), v.castle.maxZ())) {
                if (level.getBlockState(p).is(Blocks.RED_BED)) {
                    throne = p.immutable();
                    break;
                }
            }
            log.accept("  castle bed found: " + (throne == null ? "no" : throne.toShortString()));
        }
        return !v.blueprint.hasInstant() || t >= 1200;
    }

    /** Who lives there now, their fields and banner, and a raid led by the tester. */
    private void people(ServerLevel level) {
        VillageRecord v = record;
        List<Maiden> rs = VillageSocial.residents(level, v);
        java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
        int children = 0, married = 0, guards = 0, hats = 0, casual = 0;
        for (Maiden m : rs) {
            kinds.merge(m.kind().name(), 1, Integer::sum);
            if (m.isBaby()) children++;
            if (m.spouse() != null) married++;
            if (m.isGuard()) guards++;
            if (!m.hat().isEmpty()) hats++;
            if (m.isCasual()) casual++;
        }
        log.accept("people: " + rs.size() + " " + kinds + ", " + children + " children, " + married + " married to each other, " + guards
                + " guards, " + hats + " wearing a job hat, " + casual + " in casual clothes; banner by the bell " + v.bannerUp
                + " (" + net.maidens.village.Heraldry.base(v.id).getName() + " with " + net.maidens.village.Heraldry.pattern(v.id).location().getPath()
                + " in " + net.maidens.village.Heraldry.accent(v.id).getName() + ")");
        if (!v.farmsPlanned) net.maidens.village.Farms.plan(level, v);
        int[] n = net.maidens.village.Farms.count(level, v.fieldCells);
        log.accept("  field: " + net.maidens.village.Farms.lastSearch + "; now " + n[0] + " farmland, " + n[1] + " with ripe wheat, " + n[2]
                + " with no crop, " + n[3] + " water, " + n[4] + " path; " + v.fieldPath.length + " path spots for farmers");
        writeField("fields.txt", v.fieldCells);
        {
            // a field on open flat ground as well, off to one side up in the air, with a few trees and a pond on it
            int ix = v.centre().getX() + 260, iz = v.centre().getZ(), iy = 199;
            for (int cx = (ix - 4) >> 4; cx <= (ix + 110) >> 4; cx++)
                for (int cz = (iz - 60) >> 4; cz <= (iz + 60) >> 4; cz++) {
                    level.setChunkForced(cx, cz, true);
                    level.getChunk(cx, cz);
                }
            for (int x = ix - 2; x <= ix + 106; x++)
                for (int z = iz - 56; z <= iz + 56; z++) {
                    level.setBlock(new BlockPos(x, iy - 2, z), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, iy - 1, z), Blocks.DIRT.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, iy, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int y = iy + 1; y <= iy + 12; y++) if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    if ((x * 31 + z * 17) % 11 == 0) level.setBlock(new BlockPos(x, iy + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
            // a pond, a low step and a few trees
            for (int x = ix + 60; x <= ix + 70; x++)
                for (int z = iz - 8; z <= iz + 2; z++) level.setBlock(new BlockPos(x, iy, z), Blocks.WATER.defaultBlockState(), 2);
            for (int x = ix + 20; x <= ix + 40; x++)
                for (int z = iz + 20; z <= iz + 56; z++) level.setBlock(new BlockPos(x, iy, z), Blocks.AIR.defaultBlockState(), 2);
            for (int x = ix + 20; x <= ix + 40; x++)
                for (int z = iz + 20; z <= iz + 56; z++) level.setBlock(new BlockPos(x, iy - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            int[][] trees = {{ix + 30, iz - 20}, {ix + 45, iz + 10}, {ix + 15, iz - 5}, {ix + 50, iz - 30}};
            for (int[] t : trees) {
                for (int y = 1; y <= 5; y++) level.setBlock(new BlockPos(t[0], iy + y, t[1]), Blocks.OAK_LOG.defaultBlockState(), 2);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        for (int y = 4; y <= 6; y++) {
                            BlockPos q = new BlockPos(t[0] + dx, iy + y, t[1] + dz);
                            if (level.getBlockState(q).isAir() && Math.abs(dx) + Math.abs(dz) + (y - 4) < 5) level.setBlock(q, Blocks.OAK_LEAVES.defaultBlockState(), 2);
                        }
            }
            // (the village's own field, if it has none: a gate on this ground for its path, and its farmers work there)
            boolean theirs = v.fieldCells.length == 0;
            var f = net.maidens.village.Farms.fieldAt(level, v, ix + 40, iz, new VillageRecord.Gate(new BlockPos(ix + 6, iy + 1, iz), 1, 0), theirs);
            if (theirs) writeField("fields.txt", v.fieldCells);
            long[] cells = net.maidens.village.Farms.cells(f);
            int[] m = net.maidens.village.Farms.count(level, cells);
            int logs = 0;
            for (int[] t : trees) if (level.getBlockState(new BlockPos(t[0], iy + 2, t[1])).is(net.minecraft.tags.BlockTags.LOGS)) logs++;
            log.accept("  (test) a field on flat ground at " + ix + ", " + iy + ", " + iz + ": " + m[0] + " farmland, " + m[1] + " with ripe wheat, " + m[2]
                    + " with no crop, " + m[3] + " water, " + m[4] + " path; trees still standing " + logs + " of " + trees.length);
            writeField("fields_flat.txt", cells);
        }
        if (!v.treesCleared) net.maidens.village.Trees.clear(level, v);
        log.accept("  trees: " + net.maidens.village.Trees.lastClearing);
        {
            // what's left inside: trees, and leaves with no log near them (floating), snow and vines hanging in the air
            var area = v.wallBox != null ? v.wallBox : v.box;
            int trees = 0, floating = 0, loose = 0;
            java.util.Set<BlockPos> seenTops = new java.util.HashSet<>();
            for (int x = area.minX(); x <= area.maxX(); x++)
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                    for (int y = top; y > top - 24; y--) {
                        BlockPos p = new BlockPos(x, y, z);
                        var st = level.getBlockState(p);
                        if (st.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock && !st.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) {
                            boolean log = false;
                            for (BlockPos q : BlockPos.betweenClosed(p.offset(-5, -5, -5), p.offset(5, 5, 5)))
                                if (level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) { log = true; break; }
                            if (!log) floating++;
                        }
                        if ((st.is(Blocks.SNOW) || st.is(Blocks.VINE)) && !st.canSurvive(level, p)) loose++;
                        if (st.is(net.minecraft.tags.BlockTags.LOGS) && level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.DIRT)) trees++;
                    }
                }
            log.accept("  inside the village now: " + trees + " trunks on the ground, " + floating + " leaves with no log within 5, " + loose + " snow or vines hanging in the air");
            // the castle: its road, its glass joined up, who sleeps there
            if (v.castle != null) {
                int panes = 0, joined = 0;
                for (BlockPos p : BlockPos.betweenClosed(v.castle.minX(), v.castle.minY(), v.castle.minZ(), v.castle.maxX(), v.castle.maxY(), v.castle.maxZ())) {
                    var st = level.getBlockState(p);
                    if (!st.is(Blocks.GLASS_PANE) && !(st.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock)) continue;
                    panes++;
                    if (st.getValue(net.minecraft.world.level.block.CrossCollisionBlock.NORTH) || st.getValue(net.minecraft.world.level.block.CrossCollisionBlock.SOUTH)
                            || st.getValue(net.minecraft.world.level.block.CrossCollisionBlock.EAST) || st.getValue(net.minecraft.world.level.block.CrossCollisionBlock.WEST)) joined++;
                }
                log.accept("  castle: road to its gate " + v.castleRoad + "; " + panes + " glass panes, " + joined + " joined to the walls");
            }
            List<Maiden> all = VillageSocial.everyone(level, v);
            int couples = 0, together = 0, royalChildren = 0;
            String royal = "-";
            for (Maiden m : all) {
                if (m.isRoyal()) {
                    royal = m.name() + (m.isBaby() ? " (a child!)" : "") + " sleeping " + (m.residence().bed() == null ? "nowhere" : v.castle != null && v.castle.isInside(m.residence().bed()) ? "in the castle" : "at " + m.residence().bed().toShortString());
                    if (m.isBaby()) royalChildren++;
                }
                if (m.spouse() == null || m.residence().bed() == null || m.getUUID().compareTo(m.spouse()) > 0) continue;
                for (Maiden o : all)
                    if (o.getUUID().equals(m.spouse()) && o.residence().bed() != null) {
                        couples++;
                        if (m.residence().bed().closerThan(o.residence().bed(), 8)) together++;
                    }
            }
            log.accept("  people: " + all.size() + " (filled: " + v.peopled + "); " + couples + " married couples with beds, " + together + " sleeping under one roof; the royal " + royal);
        }
        log.accept("  rank of the tester: " + net.maidens.politics.Diplomacy.rank(level, v, tester.getUUID()).label);
    }

    /** Where a field is (its middle, height and size) for the client test's pictures. */
    private void writeField(String name, long[] cells) {
        if (cells.length == 0) return;
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE, y = 0;
        for (long c : cells) {
            BlockPos p = BlockPos.of(c);
            x0 = Math.min(x0, p.getX());
            x1 = Math.max(x1, p.getX());
            z0 = Math.min(z0, p.getZ());
            z1 = Math.max(z1, p.getZ());
            y += p.getY();
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("maidens.selftest"), name),
                    (x0 + x1) / 2 + " " + y / cells.length + " " + (z0 + z1) / 2 + " " + Math.max(x1 - x0, z1 - z0));
        } catch (java.io.IOException e) {
            log.accept("  (couldn't write " + name + ": " + e + ")");
        }
    }

    private java.util.Map<String, Integer> storeBefore;

    /** The village's workers: jobs given out, then a working day. */
    private void economyStart(ServerLevel level) {
        VillageRecord v = record;
        level.setDayTime(level.getDayTime() / 24000 * 24000 + 24000 + 2600);
        List<Maiden> rs = VillageSocial.everyone(level, v);
        for (int i = 0; i < 6; i++) {
            net.maidens.village.People.jobs(level, v, rs);
            log.accept("economy: jobs " + net.maidens.village.People.lastJobs);
        }
        // a worker for each job there's a spot for, if the village didn't give one out (a small test village)
        net.minecraft.util.RandomSource r = level.getRandom();
        net.maidens.maiden.work.Job[] jobs = {net.maidens.maiden.work.Job.FARM, net.maidens.maiden.work.Job.WOOD, net.maidens.maiden.work.Job.MINE, net.maidens.maiden.work.Job.FISH};
        BlockPos[] spots = {net.maidens.village.Farms.spot(level, v, r), net.maidens.village.WorkSites.forest(level, v, r),
                net.maidens.village.WorkSites.mine(level, v, r), net.maidens.village.WorkSites.water(level, v, r)};
        log.accept("  spots: farm " + spots[0] + ", wood " + spots[1] + ", mine " + spots[2] + ", water " + spots[3] + " (bell " + v.bell() + ")");
        for (int i = 0; i < jobs.length; i++) {
            net.maidens.maiden.work.Job j = jobs[i];
            if (spots[i] == null || rs.stream().anyMatch(m -> m.villageWork().job() == j)) continue;
            for (Maiden m : rs) {
                if (!net.maidens.village.People.worker(m) || m.villageWork().job() != net.maidens.maiden.work.Job.NONE && m.villageWork().job() != net.maidens.maiden.work.Job.GUARD) continue;
                m.villageWork().set(j, spots[i], 0);

                log.accept("  (test) " + m.name() + " made a " + j.button.toLowerCase() + " at " + spots[i].toShortString());
                break;
            }
        }
        // (the test's field may be far off, up in the air: a farmer is put by it)
        for (Maiden m : rs)
            if (m.villageWork().job() == net.maidens.maiden.work.Job.FARM && m.villageWork().spot() != null) {
                farmer = m;
                BlockPos at = m.villageWork().spot();
                m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                break;
            }
        storeBefore = new java.util.TreeMap<>(v.store);
        log.accept("  store at the start: " + v.store + "; food " + net.maidens.village.Economy.food(v));
    }

    private Maiden farmer;

    private void economyWorkers(ServerLevel level, String when) {
        VillageRecord v = record;
        if (farmer != null) log.accept("  " + when + ": the farmer " + farmer.name() + " alive " + farmer.isAlive() + " at " + farmer.blockPosition().toShortString()
                + ", job " + farmer.villageWork().job() + ", hired " + farmer.employment().isHired() + ", raiding " + farmer.raider().active()
                + ", village " + farmer.residence().village() + " (" + v.id + "), activity " + net.maidens.maiden.home.Schedule.activity(farmer) + ", goals [" + farmer.runningGoals() + "]");
        for (Maiden m : VillageSocial.everyone(level, v)) {
            var w = m.villageWork();
            if (w.job() == net.maidens.maiden.work.Job.NONE) continue;
            BlockPos at = w.place(v);
            log.accept("  " + when + ": " + m.name() + " (" + w.job().button + (w.job() == net.maidens.maiden.work.Job.GUARD ? w.patrols(v) ? ", walks round" : ", keeps a gate" : "")
                    + ") " + (at == null ? "-" : (int) Math.sqrt(m.blockPosition().distSqr(at)) + " blocks from her spot")
                    + ", on duty " + w.onDuty() + ", hat '" + m.hat() + "', casual " + m.isCasual() + ", holds " + m.getMainHandItem().getItem()
                    + ", wears " + m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() + "/" + m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()
                    + ", goals [" + m.runningGoals() + "]");
        }
    }

    /** Where everyone is and what they wear at a time of day. */
    private void day(ServerLevel level, String when) {
        VillageRecord v = record;
        BlockPos bell = v.bell() != null ? v.bell() : v.centre();
        java.util.Map<String, Integer> what = new java.util.TreeMap<>();
        int nearBell = 0, adults = 0, casual = 0, hats = 0, asleep = 0, home = 0;
        for (Maiden m : VillageSocial.everyone(level, v)) {
            var a = net.maidens.maiden.home.Schedule.activity(m);
            what.merge(String.valueOf(a), 1, Integer::sum);
            if (m.blockPosition().closerThan(bell, 12)) nearBell++;
            if (m.residence().bed() != null && m.blockPosition().closerThan(m.residence().bed(), 6)) home++;
            if (m.isSleeping()) asleep++;
            if (!net.maidens.maiden.home.Schedule.adult(m)) continue;
            adults++;
            if (m.isCasual()) casual++;
            if (!m.hat().isEmpty()) hats++;
        }
        log.accept("day " + when + ": doing " + what + "; " + nearBell + " by the bell, " + home + " at home, " + asleep + " asleep; of "
                + adults + " grown-ups " + casual + " in casual clothes, " + hats + " in work clothes");
        // trading: only at the market
        for (Maiden m : VillageSocial.everyone(level, v)) {
            if (net.maidens.maiden.home.Schedule.activity(m) == null || m.isBaby()) continue;
            String closed = m.trading().closed(tester);
            log.accept("  trade with " + m.name() + ": " + (closed == null ? "open" : "\"" + closed + "\""));
            break;
        }
    }

    private void economyCheck(ServerLevel level) {
        VillageRecord v = record;
        // the day's harvest
        net.maidens.village.Economy.produce(level, v, VillageSocial.everyone(level, v));
        log.accept("economy: a day's work brought in " + net.maidens.village.Economy.lastHarvest);
        int[] crops = net.maidens.village.Farms.count(level, v.fieldCells);
        log.accept("  field now: " + crops[0] + " farmland, " + crops[1] + " with ripe wheat, " + crops[2] + " with no crop");
        java.util.Map<String, Integer> gained = new java.util.TreeMap<>();
        v.store.forEach((k, n) -> {
            int d = n - storeBefore.getOrDefault(k, 0);
            if (d != 0) gained.put(k.replace("minecraft:", ""), d);
        });
        log.accept("economy: store changed by " + gained + "; food " + net.maidens.village.Economy.food(v) + ", logs " + v.count(net.maidens.village.Economy.LOGS)
                + ", stone " + v.count(net.maidens.village.Economy.STONE));
        // hiring and following take hearts; married couples show in the menu
        List<Maiden> rs = VillageSocial.everyone(level, v);
        Maiden w = null, wed = null;
        for (Maiden m : rs) {
            if (w == null && net.maidens.village.People.worker(m) && !m.employment().isHired()) w = m;
            if (wed == null && m.spouse() != null) wed = m;
        }
        if (wed != null) log.accept("  married: " + wed.name() + " to " + wed.spouseName());
        if (w != null) {
            level.setDayTime(level.getDayTime() / 24000 * 24000 + 24000 + 3000);
            tester.moveTo(w.getX() + 1, w.getY(), w.getZ());
            w.relationships().setAffection(tester, 5);
            log.accept("  hire at 5: \"" + w.employment().hire(tester, net.maidens.maiden.work.Job.FARM) + "\"");
            log.accept("  follow at 5: \"" + w.toggleFollow(tester) + "\"");
            w.relationships().setAffection(tester, 25);
            log.accept("  follow at 25: \"" + w.toggleFollow(tester) + "\" following " + w.isFollowing(tester) + ", keeps her day " + net.maidens.maiden.home.Schedule.keeps(w));
            log.accept("  stop: \"" + w.toggleFollow(tester) + "\"");
            tester.moveTo(w.getX() + 400, w.getY(), w.getZ());
            log.accept("  follow at 25 from far outside: \"" + w.toggleFollow(tester) + "\"");
            w.relationships().setAffection(tester, 85);
            log.accept("  follow at 85 from far outside: \"" + w.toggleFollow(tester) + "\"");
            if (w.isFollowing(tester)) w.toggleFollow(tester);
            tester.moveTo(w.getX() + 1, w.getY(), w.getZ());
        }
        economyWorkers(level, "next morning");
    }

    /** A raid led by the tester. */
    private void raidLead(ServerLevel level) {
        VillageRecord v = record;
        Network n = Network.get(level);
        KnownVillage self = n.know(level, v);
        List<KnownVillage> nb = n.neighbours(self);
        if (!nb.isEmpty()) {
            net.maidens.politics.Diplomacy.setStance(level, v, tester, nb.get(0), net.maidens.politics.Stance.AGGRESSIVE);
            log.accept("  lead a raid on " + nb.get(0).name + ": " + net.maidens.politics.WarParties.lead(level, v, nb.get(0), tester));
            int following = 0;
            for (Maiden m : VillageSocial.residents(level, v)) if (m.raider().active() && m.raider().resident()) following++;
            log.accept("  " + following + " residents are on the raid");
        }
    }

    /** /village find walled, as the command does it. */
    private void finder(ServerLevel level) {
        long start = System.nanoTime();
        KnownVillage k = Network.findNearest(level, village.getX(), village.getZ(), 6000,
                id -> Villages.rollsMaidens(level, id) && net.maidens.village.Fortify.rolls(level, id));
        log.accept("find walled: " + (k == null ? "none" : k.name + " at " + k.x + ", " + k.z + ", " + Math.round(Math.sqrt(Math.pow(k.x - village.getX(), 2) + Math.pow(k.z - village.getZ(), 2))) + " blocks")
                + " in " + (System.nanoTime() - start) / 1_000_000 + " ms");
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

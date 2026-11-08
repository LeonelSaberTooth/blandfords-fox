package net.maidens.test;

import net.maidens.core.ModEntities;
import net.maidens.maiden.MaidenMenu;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import net.maidens.MaidensMod;
import net.maidens.maiden.work.Job;
import net.maidens.maiden.Maiden;
import net.maidens.network.ActionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Development check, off unless the server is started with -Dmaidens.selftest=&lt;folder&gt;.
 * Finds a maiden's house in a fresh world, checks she spawned in it, then tries everything:
 * talking, gifts, a proposal, hiring, and each job for a minute of game time, and writes what happened
 * to report.txt (and the house's position to house.txt, for the client test). Then stops the server.
 */
@EventBusSubscriber(modid = MaidensMod.MOD_ID)
public final class SelfTest {
    private static final Logger LOG = LoggerFactory.getLogger("maidens/selftest");
    private static final String OUT = System.getProperty("maidens.selftest");

    private static int tick = -1;
    private static final StringBuilder report = new StringBuilder();
    private static BlockPos house;
    private static Maiden maiden;
    private static FakePlayer tester;
    private static Zombie zombie;
    private static int phaseStart;

    private SelfTest() {}

    private static void log(String s) {
        LOG.info("[selftest] {}", s);
        report.append(s).append('\n');
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (OUT == null) return;
        MinecraftServer server = event.getServer();
        tick++;
        try {
            step(server);
        } catch (Throwable t) {
            LOG.error("Self test failed", t);
            log("ERROR " + t);
            for (StackTraceElement e : t.getStackTrace()) report.append("    at ").append(e).append('\n');
            finish(server);
        }
    }

    private static void step(MinecraftServer server) throws IOException {
        ServerLevel level = server.overworld();
        if (tick == 1) {
            Holder<Structure> h = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "maiden_house")));
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(h), BlockPos.ZERO, 200, false);
            if (found == null) {
                log("house: NONE FOUND within 200 chunks");
                house = level.getSharedSpawnPos();
            } else {
                house = found.getFirst();
                log("house: found at " + house.toShortString() + " (" + Math.round(Math.sqrt(house.distSqr(BlockPos.ZERO))) + " blocks from 0,0)");
            }
            for (int cx = (house.getX() >> 4) - 3; cx <= (house.getX() >> 4) + 3; cx++)
                for (int cz = (house.getZ() >> 4) - 3; cz <= (house.getZ() >> 4) + 3; cz++) {
                    level.setChunkForced(cx, cz, true);
                    level.getChunk(cx, cz);
                }
            level.setDayTime(1000);
            return;
        }
        if (tick == 80) {
            int hy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, house.getX(), house.getZ());
            house = new BlockPos(house.getX(), hy, house.getZ());
            List<Maiden> near = level.getEntitiesOfClass(Maiden.class, new AABB(house).inflate(48, 64, 48));
            log("maidens near the house: " + near.size());
            int beds = 0, doors = 0, wheat = 0, ripe = 0;
            for (BlockPos p : BlockPos.betweenClosed(house.offset(-24, -16, -24), house.offset(24, 16, 24))) {
                var s = level.getBlockState(p);
                if (s.is(Blocks.RED_BED)) beds++;
                if (s.is(Blocks.OAK_DOOR)) doors++;
                if (s.is(Blocks.WHEAT)) { wheat++; if (((CropBlock) s.getBlock()).isMaxAge(s)) ripe++; }
            }
            log("house blocks: bed halves " + beds + ", door halves " + doors + ", wheat " + wheat + " (" + ripe + " ripe)");
            if (near.isEmpty()) {
                maiden = ModEntities.MAIDEN.get().create(level);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, house.getX(), house.getZ());
                maiden.moveTo(house.getX() + 0.5, y, house.getZ() + 0.5, 0, 0);
                level.addFreshEntity(maiden);
                log("  (spawned one by hand to test the rest)");
            } else {
                maiden = near.get(0);
                house = maiden.blockPosition();
                log("  " + maiden.getName().getString() + " at " + maiden.blockPosition().toShortString() + ", skin " + (maiden.getSkin() + 1)
                        + ", " + maiden.getPersonality().label + ", home " + (maiden.getHome() == null ? "none" : maiden.getHome().toShortString()));
            }
            Files.createDirectories(Path.of(OUT));
            Files.writeString(Path.of(OUT, "house.txt"), house.getX() + " " + house.getY() + " " + house.getZ());
            return;
        }
        if (tick == 100) {
            tester = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("3f2b5b9e-6c7a-4d52-9c3a-1e2f3a4b5c6d"), "Tester"));
            tester.moveTo(maiden.getX() + 1, maiden.getY(), maiden.getZ(), 0, 0);
            log("talking (" + maiden.getPersonality().label + "), affection starts at " + maiden.relationships().affection(tester));
            String[] names = {"chat", "joke", "compliment", "flirt"};
            for (int round = 0; round < 4; round++)
                for (int a = 0; a < 4; a++) {
                    int before = maiden.relationships().affection(tester);
                    MaidenMenu.handle(maiden, tester, a, 0);
                    log("  " + names[a] + " #" + (round + 1) + ": " + before + " -> " + maiden.relationships().affection(tester));
                }
            Item[] gifts = {Items.CAKE, Items.DIAMOND, Items.GOAT_HORN, Items.SADDLE, Items.POPPY, Items.ROTTEN_FLESH, Items.APPLE, Items.APPLE, Items.STICK};
            for (Item it : gifts) {
                int before = maiden.relationships().affection(tester);
                tester.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(it));
                MaidenMenu.handle(maiden, tester, ActionPayload.GIFT, 0);
                log("  gift " + it + ": " + before + " -> " + maiden.relationships().affection(tester) + (tester.getMainHandItem().isEmpty() ? " (taken)" : " (kept)"));
            }
            var proposal = maiden.getPersonality().proposal;
            maiden.relationships().setAffection(tester, 99);
            tester.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(proposal));
            MaidenMenu.handle(maiden, tester, ActionPayload.GIFT, 0);
            log("proposal with " + proposal + " at 99: married " + maiden.relationships().isMarried());
            // hiring for money (before the wedding)
            tester.getInventory().clearContent();
            tester.getInventory().add(new ItemStack(Items.EMERALD, 7));
            MaidenMenu.handle(maiden, tester, ActionPayload.HIRE, Job.FARM.ordinal());
            log("hired to farm: job " + maiden.employment().job() + ", emeralds left " + tester.getInventory().countItem(Items.EMERALD) + " (7 - 5 = 2 expected)");
            maiden.employment().bag().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
            maiden.relationships().setAffection(tester, 100);
            tester.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(proposal));
            MaidenMenu.handle(maiden, tester, ActionPayload.GIFT, 0);
            log("proposal with " + proposal + " at 100: married " + maiden.relationships().isMarried() + ", to the tester " + maiden.relationships().isMarriedTo(tester));
            log("trades offered: " + maiden.getOffers().size() + " (trade level " + maiden.trading().level() + ")");
            log("farming: " + maiden.employment().job() + " around " + maiden.getHome());
            phase = 1;
            phaseStart = tick;
            return;
        }
        if (phase == 0) return;
        if (tick > 12000 && phase < 6) {
            log("TIMED OUT in phase " + phase);
            phase = 6;
            finish(server);
            return;
        }
        int t = tick - phaseStart;
        if (t > 0 && t % 200 == 0 && phase < 5) log("    t=" + t + " " + maiden.employment().status());
        boolean quit = maiden.employment().job() == Job.NONE && t > 20;   // she gave up by herself
        switch (phase) {
            case 1 -> {   // farming: the wheat plot by her house
                if (t < 1200 && !quit) return;
                report("farm");
                // a small clearing beside her, at her own height, with a tree in it
                BlockPos base = maiden.blockPosition().offset(5, 0, 0);
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = -3; dz <= 3; dz++) {
                        level.setBlock(base.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                        for (int dy = 0; dy < 9; dy++) level.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                    }
                for (int dx = -5; dx <= 0; dx++) level.setBlock(base.offset(dx, -1, 0), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int dy = 0; dy < 6; dy++) level.setBlock(base.above(dy), Blocks.OAK_LOG.defaultBlockState(), 3);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        for (int dy = 3; dy <= 6; dy++) {
                            if (dy == 6 && (Math.abs(dx) == 2 || Math.abs(dz) == 2)) continue;
                            BlockPos q = base.offset(dx, dy, dz);
                            if (level.getBlockState(q).isAir()) level.setBlock(q, Blocks.OAK_LEAVES.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false), 3);
                        }
                log("woodcutting: a 6-log oak built at " + base.toShortString());
                maiden.employment().bag().addItem(new ItemStack(Items.OAK_SAPLING, 2));
                maiden.employment().hireForTest(Job.WOOD, tester);
                next();
            }
            case 2 -> {   // woodcutting
                if (t < 1200 && !quit) return;
                report("woodcutting");
                maiden.employment().bag().addItem(new ItemStack(Items.TORCH, 8));
                mineStart = maiden.blockPosition();
                maiden.employment().hireForTest(Job.MINE, tester);
                log("mining: from " + maiden.blockPosition().toShortString() + ", heading " + Direction.NORTH);
                next();
            }
            case 3 -> {   // mining
                if (t < 2000 && !quit) return;
                log("mining: reached step " + maiden.employment().mineStep() + (quit ? " (she stopped by herself)" : ""));
                report("mining");
                // a pond beside where she started digging (on open ground, outside the house)
                maiden.moveTo(mineStart.getX() + 0.5, mineStart.getY(), mineStart.getZ() + 0.5, 0, 0);
                BlockPos c = mineStart;
                for (int dx = 3; dx <= 5; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        level.setBlock(c.offset(dx, -1, dz), Blocks.WATER.defaultBlockState(), 3);
                        level.setBlock(c.offset(dx, -2, dz), Blocks.WATER.defaultBlockState(), 3);
                        level.setBlock(c.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(c.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 3);
                    }
                maiden.employment().hireForTest(Job.FISH, tester);
                log("fishing: pond dug next to " + c.toShortString());
                next();
            }
            case 4 -> {   // fishing
                if (t < 2400 && !quit) return;
                report("fishing");
                level.setDayTime(18000);
                zombie = EntityType.ZOMBIE.create(level);
                zombie.moveTo(maiden.getX() + 5, maiden.getY(), maiden.getZ(), 0, 0);
                zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                zombie.setPersistenceRequired();
                level.addFreshEntity(zombie);
                maiden.employment().bag().addItem(new ItemStack(Items.IRON_SWORD));
                maiden.employment().hireForTest(Job.GUARD, tester);
                log("guard: a zombie 5 blocks away, an iron sword in her bag");
                next();
            }
            case 5 -> {   // guarding
                if (t == 0 || t % 100 != 0) return;
                log("  after " + t + " ticks: zombie " + (zombie.isAlive() ? "alive, " + zombie.getHealth() + " hp" : "dead")
                        + ", maiden " + maiden.getHealth() + " hp, holding " + maiden.getMainHandItem());
                if (!zombie.isAlive() || t >= 600) {
                    phase = 6;
                    finish(server);
                }
            }
            default -> { }
        }
    }

    private static int phase;
    private static BlockPos mineStart;

    private static void next() {
        phase++;
        phaseStart = tick;
    }


    private static void report(String what) {
        StringBuilder s = new StringBuilder();
        var bag = maiden.employment().bag();
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack st = bag.getItem(i);
            if (!st.isEmpty()) s.append(st.getCount()).append(' ').append(st.getItem()).append(", ");
        }
        log(what + ": job now " + maiden.employment().job() + ", maiden at " + maiden.blockPosition().toShortString() + ", bag: " + (s.isEmpty() ? "empty" : s));
        bag.clearContent();
    }

    private static void finish(MinecraftServer server) {
        try {
            Files.createDirectories(Path.of(OUT));
            Files.writeString(Path.of(OUT, "report.txt"), report);
        } catch (IOException e) {
            LOG.error("Couldn't write the report", e);
        }
        server.halt(false);
    }
}

package net.maidens.politics;

import net.maidens.core.ModEntities;
import net.maidens.maiden.Maiden;
import net.maidens.village.VillageRecord;
import net.maidens.village.VillageSocial;
import net.maidens.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Raids between maiden villages. An aggressive village raids each village it's hostile to every 3-5 days, but only
 * while a player is near the target (raiders don't really cross the world: they appear at the edge of the target
 * village, on the side facing home). Raiders are the attacking village's maidens with swords and its colour; they
 * fight the village's maidens and citizens, and steal from chests. Beaten ones surrender and run. A raid ends when
 * every raider has given up or after three minutes; players who fought them earn the right to speak for the village.
 */
public final class WarParties {
    private static final int PLAYER_RANGE = 128, RAID_TICKS = 3600;
    private static final Map<Long, Raid> ACTIVE = new HashMap<>();
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private static final class Raid {
        final KnownVillage from, to;
        /** The dimension it's in: every level ticks this class, and only that one may look at the raid. */
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final long started;
        final List<UUID> raiders = new ArrayList<>();
        final Set<UUID> fighters = new HashSet<>();
        int stolen;

        Raid(KnownVillage from, KnownVillage to, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, long started) {
            this.from = from;
            this.to = to;
            this.dimension = dimension;
            this.started = started;
        }
    }

    private WarParties() {}

    /** The server is stopping: raids don't carry over (into the next world, in single player). */
    public static void clear() {
        ACTIVE.clear();
    }

    /** Whether a village is being raided right now. */
    public static boolean raidOn(long village) {
        return ACTIVE.containsKey(village);
    }

    /** Every 5 s: raids in progress; once a minute: whether a new one starts somewhere. */
    public static void tick(ServerLevel level, Villages villages) {
        for (Raid r : new ArrayList<>(ACTIVE.values())) if (r.dimension == level.dimension()) update(level, r);
        if (level.getGameTime() % 1200 != 0) return;
        Network network = Network.get(level);
        long today = level.getDayTime() / 24000L;
        for (VillageRecord v : villages.all()) {
            if (ACTIVE.containsKey(v.id) || !level.isLoaded(v.centre()) || !playerNear(level, v)) continue;
            KnownVillage target = network.know(level, v);
            for (KnownVillage attacker : network.neighbours(target)) {
                if (network.stance(attacker, target) != Stance.AGGRESSIVE) continue;
                if (today < network.nextRaidDay(attacker, target, today, level.getRandom())) continue;
                if (start(level, attacker, v) != null) {
                    network.raided(attacker, target, today, level.getRandom());
                    break;
                }
            }
        }
    }

    private static boolean playerNear(ServerLevel level, VillageRecord v) {
        BlockPos c = v.centre();
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.distanceToSqr(c.getX(), p.getY(), c.getZ()) < PLAYER_RANGE * PLAYER_RANGE) return true;
        return false;
    }

    /** Starts a raid on a village now (also for the self test). Returns how many raiders came, or null if it couldn't start. */
    @Nullable
    public static Integer start(ServerLevel level, KnownVillage attacker, VillageRecord target) {
        Network network = Network.get(level);
        KnownVillage to = network.know(level, target);
        BlockPos at = edgeFacing(level, target, attacker);
        if (at == null) return null;
        Raid raid = new Raid(attacker, to, level.dimension(), level.getGameTime());
        RandomSource r = level.getRandom();
        int count = 3 + r.nextInt(3);
        int colour = colourOf(attacker.id);
        for (int i = 0; i < count; i++) {
            Maiden m = ModEntities.MAIDEN.get().create(level);
            if (m == null) continue;
            m.moveTo(at.getX() + 0.5 + r.nextInt(5) - 2, at.getY(), at.getZ() + 0.5 + r.nextInt(5) - 2, r.nextFloat() * 360, 0);
            m.ensureInitialised();
            m.setPersistenceRequired();     // not despawned mid-raid (she's removed when the raid is over)
            m.raider().start(target.id, attacker.id, at);
            m.clearRestriction();
            // their village's colours, and swords
            ItemStack tunic = new ItemStack(Items.LEATHER_CHESTPLATE);
            tunic.set(DataComponents.DYED_COLOR, new DyedItemColor(colour, false));
            m.employment().bag().addItem(tunic);
            if (r.nextInt(3) == 0) m.employment().bag().addItem(new ItemStack(Items.IRON_HELMET));
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(r.nextInt(3) == 0 ? Items.IRON_SWORD : Items.STONE_SWORD));
            level.addFreshEntity(m);
            raid.raiders.add(m.getUUID());
        }
        if (raid.raiders.isEmpty()) return null;
        ACTIVE.put(target.id, raid);
        String line = attacker.name + " is raiding " + target.name + "!";
        network.news(level, to.id, line);
        network.news(level, attacker.id, attacker.name + " sent raiders to " + target.name + ".");
        tell(level, target, Component.literal(line + " (" + raid.raiders.size() + " raiders, from the " + direction(target, attacker) + ")")
                .withStyle(ChatFormatting.RED));
        return raid.raiders.size();
    }

    /** A spot just outside the village, on the side facing the attackers, in loaded land. */
    @Nullable
    private static BlockPos edgeFacing(ServerLevel level, VillageRecord v, KnownVillage from) {
        BlockPos c = v.centre();
        double angle = Math.atan2(from.z - c.getZ(), from.x - c.getX());
        int dist = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 16;
        for (int i = 0; i < 7; i++) {
            double a = angle + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 0.5;
            int x = c.getX() + (int) (Math.cos(a) * dist), z = c.getZ() + (int) (Math.sin(a) * dist);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (level.getFluidState(p.below()).isEmpty()) return p;
        }
        return null;
    }

    private static void update(ServerLevel level, Raid r) {
        int fighting = 0;
        for (UUID id : r.raiders) {
            if (level.getEntity(id) instanceof Maiden m && m.isAlive() && !m.raider().surrendered()) fighting++;
        }
        boolean over = fighting == 0 || level.getGameTime() - r.started > RAID_TICKS;
        if (!over) return;
        LOG.info("Raid on {} by {} over after {} ticks ({} still fighting)", r.to.name, r.from.name, level.getGameTime() - r.started, fighting);
        ACTIVE.remove(r.to.id);
        Network network = Network.get(level);
        VillageRecord target = Villages.get(level).byId(r.to.id);
        String outcome;
        if (fighting == 0) {
            outcome = r.from.name + "'s raiders were driven off" + (r.stolen > 0 ? ", but they got away with " + r.stolen + " stacks of goods." : ".");
            if (target != null) {
                for (UUID p : r.fighters) {
                    target.addDefender(p);
                    if (level.getPlayerByUUID(p) instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(Component.literal("You helped save " + target.name + "! You may now speak for it in its dealings with other villages.")
                                .withStyle(ChatFormatting.GOLD));
                    }
                }
                Villages.get(level).setDirty();
            }
        } else {
            outcome = r.from.name + "'s raiders pulled back" + (r.stolen > 0 ? " with " + r.stolen + " stacks of stolen goods." : ".");
            for (UUID id : r.raiders) if (level.getEntity(id) instanceof Maiden m && m.isAlive()) m.raider().surrender();
        }
        network.news(level, r.to.id, outcome);
        if (target != null) tell(level, target, Component.literal(outcome).withStyle(ChatFormatting.GOLD));
        // the raided village's royal wants revenge (unless a player decided otherwise)
        if (network.setStanceByRoyal(r.to, r.from, Stance.AGGRESSIVE)) {
            network.news(level, r.to.id, r.to.name + " swore revenge on " + r.from.name + ".");
        }
    }

    /** A raider was hit by a player: they count as one of the village's defenders. */
    public static void onRaiderHurt(Maiden raider, Player by) {
        Raid r = ACTIVE.get(raider.raider().target());
        if (r != null) r.fighters.add(by.getUUID());
    }

    public static void onStolen(ServerLevel level, Maiden raider, int stacks) {
        Raid r = ACTIVE.get(raider.raider().target());
        if (r != null) r.stolen += stacks;
    }

    /** A raider loaded from disk with no raid going on any more: she's long gone home. */
    public static boolean stale(Maiden raider) {
        return !ACTIVE.containsKey(raider.raider().target());
    }

    private static void tell(ServerLevel level, VillageRecord v, Component message) {
        BlockPos c = v.centre();
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(c.getX(), p.getY(), c.getZ()) < PLAYER_RANGE * PLAYER_RANGE) p.sendSystemMessage(message);
    }

    private static String direction(VillageRecord v, KnownVillage from) {
        double a = Math.toDegrees(Math.atan2(from.z - v.centre().getZ(), from.x - v.centre().getX()));
        String[] dirs = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        return dirs[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }

    /** Each village's colour (raiders wear it), fixed by its id. */
    public static int colourOf(long village) {
        int[] colours = {0xB02E26, 0x3C44AA, 0xF9801D, 0x5E7C16, 0x8932B8, 0x169C9C, 0xFED83D, 0x1D1D21, 0xF38BAA, 0x835432};
        return colours[(int) Math.floorMod(village * 0x9E3779B97F4A7C15L >>> 33, colours.length)];
    }
}

package net.maidens.village;

import net.maidens.MaidensMod;
import net.maidens.core.MaidensConfig;
import net.maidens.maiden.Maiden;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The village's shared feelings: gossip (what one maiden feels spreads to the others), anger at players who break
 * the village, citizenship, and how maidens greet you. Called from the maiden's hooks and from {@link VillageEvents}.
 */
public final class VillageSocial {
    private static final ResourceLocation CITIZEN_ADVANCEMENT = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "citizen");
    /** When each player was last scolded for breaking something, so maidens don't shout every block. */
    private static final Map<UUID, Long> lastScolded = new HashMap<>();

    private VillageSocial() {}

    @Nullable
    public static VillageRecord villageOf(Maiden m) {
        if (!m.residence().inVillage() || !(m.level() instanceof ServerLevel level)) return null;
        return Villages.get(level).byId(m.residence().village());
    }

    /** The village's maidens that are loaded right now. */
    public static List<Maiden> residents(ServerLevel level, VillageRecord v) {
        return level.getEntitiesOfClass(Maiden.class, AABB.of(v.box).inflate(48), m -> m.residence().village() == v.id && m.isAlive());
    }

    // ------------------------------------------------------------------ from the maiden

    /** A player made one maiden like them more or less: each other resident has a chance to hear about it. */
    public static void onAffectionChanged(Maiden m, Player p, int amount) {
        VillageRecord v = villageOf(m);
        if (v == null) return;
        ServerLevel level = (ServerLevel) m.level();
        int percent = MaidensConfig.GOSSIP_SHARE_PERCENT.get();
        for (Maiden other : residents(level, v)) {
            if (other != m && other.getRandom().nextInt(100) < percent) other.relationships().hear(p.getUUID(), amount);
        }
        checkCitizen(level, v, p);
    }

    public static void onTraded(Maiden m, Player p) {
        VillageRecord v = villageOf(m);
        if (v == null) return;
        v.trades.merge(p.getUUID(), 1, Integer::sum);
        Villages.get((ServerLevel) m.level()).setDirty();
        checkCitizen((ServerLevel) m.level(), v, p);
    }

    /** Marrying one of the village's maidens makes you a citizen straight away. */
    public static void onMarried(Maiden m, Player p) {
        VillageRecord v = villageOf(m);
        if (v != null) makeCitizen((ServerLevel) m.level(), v, p);
    }

    /** Her greeting, with the village's welcome for strangers and neighbours. */
    public static String greeting(Maiden m, Player p) {
        String base = m.relationships().greeting(p);
        VillageRecord v = villageOf(m);
        if (v == null || m.relationships().isMarriedTo(p)) return base;
        if (v.isCitizen(p.getUUID())) return base + " How's life in " + v.name + ", neighbour?";
        if (m.relationships().affection(p) < 20) return "Welcome to " + v.name + ", traveller. Can I help you?";
        return base;
    }

    // ------------------------------------------------------------------ anger

    /** A player broke a block of the village's blueprint: every resident likes them less (capped per day). */
    public static void onBlueprintBroken(ServerLevel level, VillageRecord v, ServerPlayer p, BlockPos pos) {
        int amount = MaidensConfig.ANGER_PER_BLOCK.get();
        Maiden nearest = null;
        for (Maiden m : residents(level, v)) {
            if (m.relationships().anger(p.getUUID(), amount) > 0 && m.distanceToSqr(p) < 24 * 24) {
                m.burst(ParticleTypes.ANGRY_VILLAGER, 2);
                if (nearest == null || m.distanceToSqr(p) < nearest.distanceToSqr(p)) nearest = m;
            }
        }
        long now = level.getGameTime();
        if (nearest != null && now - lastScolded.getOrDefault(p.getUUID(), -10000L) > 600) {
            lastScolded.put(p.getUUID(), now);
            nearest.say(p, "Hey! That's part of " + v.name + "! Leave our houses alone.");
        }
    }

    // ------------------------------------------------------------------ citizenship

    /** Average affection of the residents, or -1 if none are loaded. */
    public static int averageAffection(ServerLevel level, VillageRecord v, UUID player) {
        List<Maiden> rs = residents(level, v);
        if (rs.isEmpty()) return -1;
        int sum = 0;
        for (Maiden m : rs) sum += m.relationships().affection(player);
        return sum / rs.size();
    }

    static void checkCitizen(ServerLevel level, VillageRecord v, Player p) {
        if (v.isCitizen(p.getUUID())) return;
        boolean trades = v.tradesBy(p.getUUID()) >= MaidensConfig.CITIZEN_TRADES.get();
        boolean liked = averageAffection(level, v, p.getUUID()) >= MaidensConfig.CITIZEN_AVERAGE_AFFECTION.get();
        if (trades || liked) makeCitizen(level, v, p);
    }

    public static void makeCitizen(ServerLevel level, VillageRecord v, Player p) {
        if (!v.citizens.add(p.getUUID())) return;
        Villages.get(level).setDirty();
        if (!(p instanceof ServerPlayer sp)) return;
        sp.sendSystemMessage(Component.literal("You are now a citizen of " + v.name + "! You may build and change the village as you like.")
                .withStyle(ChatFormatting.GOLD));
        if (sp instanceof FakePlayer) return;
        AdvancementHolder adv = level.getServer().getAdvancements().get(CITIZEN_ADVANCEMENT);
        if (adv != null) sp.getAdvancements().award(adv, "citizen");
    }
}

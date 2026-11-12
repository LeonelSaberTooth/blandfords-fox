package net.maidens.village;

import net.maidens.MaidensMod;
import net.maidens.politics.Network;
import net.maidens.politics.WarParties;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * World events that matter to maiden villages: blocks broken or placed by players, and a slow tick that
 * records blueprint pieces as their chunks load.
 */
@EventBusSubscriber(modid = MaidensMod.MOD_ID)
public final class VillageEvents {
    private VillageEvents() {}

    /** Maidens don't trample farmland (stepping down onto a field, working in it). */
    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent e) {
        if (e.getEntity() instanceof net.maidens.maiden.Maiden) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Network network = Network.get(level);
        // one possible village checked every 4 ticks (a real one means laying out the whole village, as /locate does)
        if (network.busy()) {
            if (network.boosted(level)) {
                // the world map is open: as many as fit in 6 ms this tick
                long until = System.nanoTime() + 6_000_000L;
                do network.tick(level); while (network.busy() && System.nanoTime() < until);
            } else if (level.getGameTime() % 4 == 0) network.tick(level);
        }
        Fortify.placeTick(level, Villages.get(level));
        if (level.getGameTime() % 100 == 0) {
            Villages villages = Villages.get(level);
            villages.recordLoadedPieces(level);
            Upkeep.tick(level, villages);
            Fortify.tick(level, villages);
            HomeVillages.tick(level, villages);
            Trees.tick(level, villages);
            Farms.tick(level, villages);
            VillageConversion.sweep(level, villages);
            WarParties.tick(level, villages);
            network.slowTick(level);
            if (level.getGameTime() % 1200 == 0) Housing.tick(level, villages);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        WarParties.clear();
        HomeVillages.clear();
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getPlayer() instanceof ServerPlayer p) {
            playerBroke(level, p, event.getPos(), event.getState());
        }
    }

    /** A citizen ringing their village's bell gets to choose what's built next. */
    @SubscribeEvent
    public static void onBell(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer p)) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !level.getBlockState(event.getPos()).is(Blocks.BELL)) return;
        VillageRecord v = Villages.get(level).at(event.getPos());
        if (v != null) VillageView.open(level, v, p);
    }

    /** Beating a vanilla raid on a maiden village also earns the right to speak for it. */
    @SubscribeEvent
    public static void onHero(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getEffectInstance().is(MobEffects.HERO_OF_THE_VILLAGE)) return;
        VillageRecord v = Villages.get(p.serverLevel()).at(p.blockPosition());
        if (v == null || v.isDefender(p.getUUID())) return;
        v.addDefender(p.getUUID());
        Villages.get(p.serverLevel()).setDirty();
        p.sendSystemMessage(net.minecraft.network.chat.Component.literal("You saved " + v.name + " from the raid! You may now speak for it in its dealings with other villages.")
                .withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer p)) return;
        Villages villages = Villages.get(level);
        VillageRecord v = villages.at(event.getPos());
        if (v != null && v.isCitizen(p.getUUID())) {
            v.blueprint.set(event.getPos(), event.getPlacedBlock());
            villages.setDirty();
        }
    }

    /** A player broke a block (also called by the self test). Citizens reshape the village; anyone else angers it. */
    public static void playerBroke(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state) {
        Villages villages = Villages.get(level);
        VillageRecord v = villages.at(pos);
        if (v == null) return;
        BlockState expected = v.blueprint.expected(pos);
        if (expected == null || !expected.is(state.getBlock())) return;
        if (v.isCitizen(p.getUUID())) {
            v.blueprint.set(pos, Blocks.AIR.defaultBlockState());
            villages.setDirty();
        } else {
            VillageSocial.onBlueprintBroken(level, v, p, pos);
        }
    }
}

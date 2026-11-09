package net.maidens.village;

import net.maidens.MaidensMod;
import net.maidens.politics.Network;
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

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Network network = Network.get(level);
        if (network.busy()) network.tick(level);           // one possible village checked per tick
        if (level.getGameTime() % 100 == 0) {
            Villages villages = Villages.get(level);
            villages.recordLoadedPieces(level);
            Upkeep.tick(level, villages);
            VillageConversion.sweep(level, villages);
            if (level.getGameTime() % 1200 == 0) Housing.tick(level, villages);
        }
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
        if (v != null) VillageMenu.show(level, v, p);
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

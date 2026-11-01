package net.blandfordsfox.block;

import net.blandfordsfox.BlandfordsFoxMod;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Stripping a birch log with an axe gives a stripped birch log and a sheet of paper. */
@EventBusSubscriber(modid = BlandfordsFoxMod.MOD_ID)
public final class BirchStripping {
    private BirchStripping() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onStrip(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.AXE_STRIP || event.isCanceled()) return;
        BlockState state = event.getState();
        if (!state.is(Blocks.BIRCH_LOG)) return;
        // NeoForge asks before working out vanilla's stripped state, so set it here for both
        event.setFinalState(Blocks.STRIPPED_BIRCH_LOG.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)));
        if (event.isSimulated()) return;
        if (event.getLevel() instanceof Level level && !level.isClientSide) {
            Direction face = event.getContext() != null ? event.getContext().getClickedFace() : Direction.UP;
            Block.popResourceFromFace(level, event.getPos(), face, new ItemStack(Items.PAPER));
        }
    }
}

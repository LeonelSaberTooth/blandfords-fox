package net.maidens;

import net.maidens.command.MaidenCommand;
import net.maidens.command.VillageCommand;
import net.maidens.core.MaidensConfig;
import net.maidens.core.ModBlocks;
import net.maidens.core.ModEntities;
import net.maidens.core.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Start-up only: registers each system. What each system does is described in docs/DESIGN.md.
 */
@Mod(MaidensMod.MOD_ID)
public final class MaidensMod {
    public static final String MOD_ID = "maidens";

    public MaidensMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, MaidensConfig.SPEC);
        ModEntities.register(modBus);
        ModItems.register(modBus);
        ModBlocks.register(modBus);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> {
            MaidenCommand.register(e.getDispatcher());
            VillageCommand.register(e.getDispatcher());
        });
    }
}

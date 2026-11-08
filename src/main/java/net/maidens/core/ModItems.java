package net.maidens.core;

import net.maidens.MaidensMod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {}

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaidensMod.MOD_ID);

    public static final DeferredItem<Item> MAIDEN_SPAWN_EGG = ITEMS.register("maiden_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.MAIDEN, 0xC79284, 0x74181A, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(MAIDEN_SPAWN_EGG);
    }
}

package net.maidens;

import net.maidens.command.MaidenCommand;
import net.maidens.entity.Maiden;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(MaidensMod.MOD_ID)
public final class MaidensMod {
    public static final String MOD_ID = "maidens";

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    /** Player-sized: a little under two blocks, so she fits through doors. */
    public static final DeferredHolder<EntityType<?>, EntityType<Maiden>> MAIDEN = ENTITIES.register("maiden",
            () -> EntityType.Builder.of(Maiden::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.7F)
                    .clientTrackingRange(10)
                    .build(MOD_ID + ":maiden"));

    public static final DeferredItem<Item> MAIDEN_SPAWN_EGG = ITEMS.register("maiden_spawn_egg",
            () -> new DeferredSpawnEggItem(MAIDEN, 0xC79284, 0x74181A, new Item.Properties()));

    public MaidensMod(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTabs);
        NeoForge.EVENT_BUS.addListener(this::commands);
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(MAIDEN.get(), Maiden.createAttributes().build());
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(MAIDEN_SPAWN_EGG);
    }

    private void commands(RegisterCommandsEvent event) {
        MaidenCommand.register(event.getDispatcher());
    }
}

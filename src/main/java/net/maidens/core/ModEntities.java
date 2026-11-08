package net.maidens.core;

import net.maidens.MaidensMod;
import net.maidens.maiden.Maiden;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MaidensMod.MOD_ID);

    /** Player-sized: a little under two blocks, so she fits through doors. */
    public static final DeferredHolder<EntityType<?>, EntityType<Maiden>> MAIDEN = ENTITIES.register("maiden",
            () -> EntityType.Builder.of(Maiden::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.7F)
                    .clientTrackingRange(10)
                    .build(MaidensMod.MOD_ID + ":maiden"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(ModEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(MAIDEN.get(), Maiden.createAttributes().build());
    }
}

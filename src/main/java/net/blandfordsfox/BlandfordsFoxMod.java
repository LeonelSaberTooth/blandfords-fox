package net.blandfordsfox;

import com.mojang.serialization.MapCodec;
import net.blandfordsfox.entity.BlandfordsFox;
import net.blandfordsfox.entity.Caracal;
import net.blandfordsfox.world.NegevDensity;
import net.blandfordsfox.world.NegevFeatures;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BlandfordsFoxMod.MOD_ID)
public final class BlandfordsFoxMod {
    public static final String MOD_ID = "blandfordsfox";

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD_ID);

    // Negev-style deserts: terrain shape, then rock layers and valley decoration
    static {
        DENSITY_FUNCTIONS.register("negev_terrain", () -> NegevDensity.Terrain.MAP_CODEC);
        DENSITY_FUNCTIONS.register("negev_detail", () -> NegevDensity.Detail.MAP_CODEC);
        FEATURES.register("negev_paint", () -> new NegevFeatures.Paint(NoneFeatureConfiguration.CODEC));
        FEATURES.register("negev_decorate", () -> new NegevFeatures.Decorate(NoneFeatureConfiguration.CODEC));
    }

    // Same settings as the vanilla fox, sized down to this smaller model.
    public static final DeferredHolder<EntityType<?>, EntityType<BlandfordsFox>> BLANDFORDS_FOX = ENTITIES.register("blandfords_fox",
            () -> EntityType.Builder.of(BlandfordsFox::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.6F)
                    .eyeHeight(0.35F)
                    .passengerAttachments(0.6375F)
                    .clientTrackingRange(8)
                    .immuneTo(Blocks.SWEET_BERRY_BUSH)
                    .build(MOD_ID + ":blandfords_fox"));

    public static final DeferredItem<Item> SPAWN_EGG = ITEMS.register("blandfords_fox_spawn_egg",
            () -> new DeferredSpawnEggItem(BLANDFORDS_FOX, 0xD9B27C, 0x5A4632, new Item.Properties()));

    // Ocelot settings, sized to the caracal model (about a block tall to the top of its head).
    public static final DeferredHolder<EntityType<?>, EntityType<Caracal>> CARACAL = ENTITIES.register("caracal",
            () -> EntityType.Builder.of(Caracal::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.0F)
                    .eyeHeight(0.9F)
                    .passengerAttachments(1.0F)
                    .clientTrackingRange(10)
                    .build(MOD_ID + ":caracal"));

    public static final DeferredItem<Item> CARACAL_SPAWN_EGG = ITEMS.register("caracal_spawn_egg",
            () -> new DeferredSpawnEggItem(CARACAL, 0xB8794A, 0x2B1E17, new Item.Properties()));

    public BlandfordsFoxMod(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        DENSITY_FUNCTIONS.register(modBus);
        FEATURES.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::spawnPlacements);
        modBus.addListener(this::creativeTabs);
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(BLANDFORDS_FOX.get(), BlandfordsFox.createAttributes().build());
        event.put(CARACAL.get(), Caracal.createAttributes().build());
    }

    private void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(BLANDFORDS_FOX.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlandfordsFox::checkBlandfordsFoxSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(CARACAL.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Caracal::checkCaracalSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(SPAWN_EGG);
            event.accept(CARACAL_SPAWN_EGG);
        }
    }
}

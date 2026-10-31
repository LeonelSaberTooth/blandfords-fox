package net.blandfordsfox;

import com.mojang.serialization.MapCodec;
import net.blandfordsfox.entity.BlandfordsFox;
import net.blandfordsfox.entity.Caracal;
import net.blandfordsfox.entity.Hare;
import net.blandfordsfox.entity.Hyena;
import net.blandfordsfox.entity.Onager;
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
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
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
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);

    /** Like cobblestone: needs a pickaxe, drops itself. */
    public static final DeferredBlock<Block> LIMESTONE = BLOCKS.registerSimpleBlock("limestone",
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    /** Like gravel: falls, best dug with a shovel, sometimes drops flint. */
    public static final DeferredBlock<ColoredFallingBlock> LIMESTONE_GRAVEL = BLOCKS.register("limestone_gravel",
            () -> new ColoredFallingBlock(new ColorRGBA(0xFFC09F73), BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .instrument(NoteBlockInstrument.SNARE).strength(0.6F).sound(SoundType.GRAVEL)));
    public static final DeferredItem<BlockItem> LIMESTONE_ITEM = ITEMS.registerSimpleBlockItem(LIMESTONE);
    public static final DeferredItem<BlockItem> LIMESTONE_GRAVEL_ITEM = ITEMS.registerSimpleBlockItem(LIMESTONE_GRAVEL);
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

    // Wolf settings, sized to the hyena model (about 1.4 blocks to the ear tips).
    public static final DeferredHolder<EntityType<?>, EntityType<Hyena>> HYENA = ENTITIES.register("hyena",
            () -> EntityType.Builder.of(Hyena::new, MobCategory.CREATURE)
                    .sized(0.8F, 1.3F)
                    .eyeHeight(1.1F)
                    .passengerAttachments(1.3F)
                    .clientTrackingRange(10)
                    .build(MOD_ID + ":hyena"));

    public static final DeferredItem<Item> HYENA_SPAWN_EGG = ITEMS.register("hyena_spawn_egg",
            () -> new DeferredSpawnEggItem(HYENA, 0xC9B79C, 0x4A3F35, new Item.Properties()));

    // Rabbit settings, sized to the hare model (longer legs and ears than a rabbit).
    public static final DeferredHolder<EntityType<?>, EntityType<Hare>> HARE = ENTITIES.register("hare",
            () -> EntityType.Builder.of(Hare::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.8F)
                    .eyeHeight(0.7F)
                    .clientTrackingRange(8)
                    .build(MOD_ID + ":hare"));

    public static final DeferredItem<Item> HARE_SPAWN_EGG = ITEMS.register("hare_spawn_egg",
            () -> new DeferredSpawnEggItem(HARE, 0xC8A878, 0x6E5238, new Item.Properties()));

    // Donkey settings: the onager is a donkey in all but looks.
    public static final DeferredHolder<EntityType<?>, EntityType<Onager>> ONAGER = ENTITIES.register("onager",
            () -> EntityType.Builder.of(Onager::new, MobCategory.CREATURE)
                    .sized(1.3964844F, 1.5F)
                    .eyeHeight(1.425F)
                    .passengerAttachments(1.1125F)
                    .clientTrackingRange(10)
                    .build(MOD_ID + ":onager"));

    public static final DeferredItem<Item> ONAGER_SPAWN_EGG = ITEMS.register("onager_spawn_egg",
            () -> new DeferredSpawnEggItem(ONAGER, 0xD2B48C, 0x5C4632, new Item.Properties()));

    public BlandfordsFoxMod(IEventBus modBus) {
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
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
        event.put(HYENA.get(), Hyena.createAttributes().build());
        event.put(HARE.get(), Hare.createAttributes().build());
        event.put(ONAGER.get(), Onager.createBaseChestedHorseAttributes().build());
    }

    private void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(BLANDFORDS_FOX.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlandfordsFox::checkBlandfordsFoxSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(CARACAL.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Caracal::checkCaracalSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(HYENA.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Hyena::checkHyenaSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(HARE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Hare::checkHareSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ONAGER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Onager::checkOnagerSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(LIMESTONE_ITEM);
            event.accept(LIMESTONE_GRAVEL_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(SPAWN_EGG);
            event.accept(CARACAL_SPAWN_EGG);
            event.accept(HYENA_SPAWN_EGG);
            event.accept(HARE_SPAWN_EGG);
            event.accept(ONAGER_SPAWN_EGG);
        }
    }
}

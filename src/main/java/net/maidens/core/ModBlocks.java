package net.maidens.core;

import net.maidens.MaidensMod;
import net.maidens.block.MarkedChestBlock;
import net.maidens.block.MarkedChestBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's blocks: the marked chest. */
public final class ModBlocks {
    private ModBlocks() {}

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MaidensMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaidensMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MaidensMod.MOD_ID);

    public static final DeferredBlock<MarkedChestBlock> MARKED_CHEST = BLOCKS.register("marked_chest",
            () -> new MarkedChestBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST)));
    public static final DeferredItem<BlockItem> MARKED_CHEST_ITEM = ITEMS.registerSimpleBlockItem(MARKED_CHEST, new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarkedChestBlockEntity>> MARKED_CHEST_ENTITY = BLOCK_ENTITIES.register("marked_chest",
            () -> BlockEntityType.Builder.of(MarkedChestBlockEntity::new, MARKED_CHEST.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ModBlocks::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(MARKED_CHEST_ITEM);
    }
}

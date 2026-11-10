package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * A village's colours and the look of its stone. Every maiden village has a banner, fixed by its id: a base colour
 * with one pattern in another colour, hung on its castle, its wall towers and by its bell. Desert villages build
 * their castle and walls in sandstone.
 */
public final class Heraldry {
    private static final List<ResourceKey<BannerPattern>> PATTERNS = List.of(
            BannerPatterns.STRIPE_CENTER, BannerPatterns.STRIPE_MIDDLE, BannerPatterns.CROSS, BannerPatterns.STRAIGHT_CROSS,
            BannerPatterns.TRIANGLE_BOTTOM, BannerPatterns.TRIANGLE_TOP, BannerPatterns.HALF_HORIZONTAL, BannerPatterns.HALF_VERTICAL,
            BannerPatterns.DIAGONAL_LEFT, BannerPatterns.CIRCLE_MIDDLE, BannerPatterns.RHOMBUS_MIDDLE, BannerPatterns.BORDER,
            BannerPatterns.STRIPE_TOP, BannerPatterns.STRIPE_BOTTOM, BannerPatterns.SQUARE_TOP_LEFT, BannerPatterns.FLOWER);

    private Heraldry() {}

    private static RandomSource random(long id) {
        return RandomSource.create(id * 0x9E3779B97F4A7C15L ^ 0xBA22E5L);
    }

    public static DyeColor base(long id) {
        return DyeColor.byId(random(id).nextInt(16));
    }

    public static DyeColor accent(long id) {
        RandomSource r = random(id);
        int base = r.nextInt(16), accent = r.nextInt(15);
        return DyeColor.byId(accent >= base ? accent + 1 : accent);
    }

    public static ResourceKey<BannerPattern> pattern(long id) {
        RandomSource r = random(id);
        r.nextInt(16);
        r.nextInt(15);
        return PATTERNS.get(r.nextInt(PATTERNS.size()));
    }

    /** A banner block in the village's base colour (same facing or rotation); anything else as it is. */
    public static BlockState recolour(BlockState s, long id) {
        if (!(s.getBlock() instanceof AbstractBannerBlock)) return s;
        String colour = base(id).getName();
        String kind = s.getBlock() instanceof WallBannerBlock ? "_wall_banner" : "_banner";
        Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(colour + kind));
        return b == Blocks.AIR ? s : b.withPropertiesOf(s);
    }

    /** Paints the village's pattern on a banner just put up. */
    public static void decorate(ServerLevel level, BlockPos pos, long id) {
        if (!(level.getBlockEntity(pos) instanceof BannerBlockEntity be)) return;
        Holder<BannerPattern> pattern = level.registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(pattern(id));
        BannerPatternLayers layers = new BannerPatternLayers.Builder().add(pattern, accent(id)).build();
        be.applyComponents(DataComponentMap.builder().set(DataComponents.BANNER_PATTERNS, layers).build(), DataComponentPatch.EMPTY);
        be.setChanged();
        level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Stone for sandstone in the desert (same shape, facing and half): everything else as it is. */
    private static final Map<Block, Block> DESERT = Map.ofEntries(
            Map.entry(Blocks.STONE_BRICKS, Blocks.CUT_SANDSTONE),
            Map.entry(Blocks.MOSSY_STONE_BRICKS, Blocks.SANDSTONE),
            Map.entry(Blocks.CRACKED_STONE_BRICKS, Blocks.SMOOTH_SANDSTONE),
            Map.entry(Blocks.COBBLESTONE, Blocks.SANDSTONE),
            Map.entry(Blocks.POLISHED_ANDESITE, Blocks.CHISELED_SANDSTONE),
            Map.entry(Blocks.STONE_BRICK_STAIRS, Blocks.SANDSTONE_STAIRS),
            Map.entry(Blocks.STONE_BRICK_SLAB, Blocks.SANDSTONE_SLAB),
            Map.entry(Blocks.STONE_BRICK_WALL, Blocks.SANDSTONE_WALL));

    public static BlockState forStyle(BlockState s, String style) {
        if (!"desert".equals(style)) return s;
        Block to = DESERT.get(s.getBlock());
        return to == null ? s : to.withPropertiesOf(s);
    }

    /** Both: the village's stone and its colours. */
    public static BlockState dress(BlockState s, VillageRecord v) {
        return recolour(forStyle(s, v.style), v.id);
    }
}

package net.maidens.politics;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;

/** Seed-map colours for biomes (vanilla ones by name; others by what kind of biome they are). */
final class BiomeColours {
    private static final Map<String, Integer> VANILLA = Map.ofEntries(
            Map.entry("ocean", 0x000070), Map.entry("deep_ocean", 0x000030), Map.entry("warm_ocean", 0x0000AC),
            Map.entry("lukewarm_ocean", 0x000090), Map.entry("deep_lukewarm_ocean", 0x000040), Map.entry("cold_ocean", 0x202070),
            Map.entry("deep_cold_ocean", 0x202038), Map.entry("frozen_ocean", 0x7070D6), Map.entry("deep_frozen_ocean", 0x404090),
            Map.entry("river", 0x0000FF), Map.entry("frozen_river", 0xA0A0FF), Map.entry("beach", 0xFADE55),
            Map.entry("snowy_beach", 0xFAF0C0), Map.entry("stony_shore", 0xA2A284), Map.entry("plains", 0x8DB360),
            Map.entry("sunflower_plains", 0xB5DB88), Map.entry("snowy_plains", 0xF0F0F0), Map.entry("ice_spikes", 0xB4DCDC),
            Map.entry("desert", 0xFA9418), Map.entry("swamp", 0x07F9B2), Map.entry("mangrove_swamp", 0x2CCC8E),
            Map.entry("forest", 0x056621), Map.entry("flower_forest", 0x2D8E49), Map.entry("birch_forest", 0x307444),
            Map.entry("dark_forest", 0x40511A), Map.entry("old_growth_birch_forest", 0x589C6C), Map.entry("old_growth_pine_taiga", 0x596651),
            Map.entry("old_growth_spruce_taiga", 0x818E79), Map.entry("taiga", 0x0B6659), Map.entry("snowy_taiga", 0x31554A),
            Map.entry("savanna", 0xBDB25F), Map.entry("savanna_plateau", 0xA79D64), Map.entry("windswept_hills", 0x606060),
            Map.entry("windswept_gravelly_hills", 0x888888), Map.entry("windswept_forest", 0x507050), Map.entry("windswept_savanna", 0xE5DA87),
            Map.entry("jungle", 0x537B09), Map.entry("sparse_jungle", 0x628B17), Map.entry("bamboo_jungle", 0x768E14),
            Map.entry("badlands", 0xD94515), Map.entry("eroded_badlands", 0xFF6D3D), Map.entry("wooded_badlands", 0xB09765),
            Map.entry("meadow", 0x60A445), Map.entry("cherry_grove", 0xE8A0C8), Map.entry("grove", 0x47726C),
            Map.entry("snowy_slopes", 0xC4C4C4), Map.entry("frozen_peaks", 0xA0A0D0), Map.entry("jagged_peaks", 0xDCDCC8),
            Map.entry("stony_peaks", 0x7B8F74), Map.entry("mushroom_fields", 0xFF00FF), Map.entry("deep_dark", 0x0B2C33),
            Map.entry("dripstone_caves", 0x8C6E50), Map.entry("lush_caves", 0x3F8F2F));

    private BiomeColours() {}

    static int of(String key, Holder<Biome> b) {
        if (key.startsWith("minecraft:")) {
            Integer c = VANILLA.get(key.substring(10));
            if (c != null) return c;
        }
        if (b.is(BiomeTags.IS_OCEAN)) return 0x000070;
        if (b.is(BiomeTags.IS_RIVER)) return 0x0000FF;
        if (b.is(BiomeTags.IS_BEACH)) return 0xFADE55;
        if (b.is(BiomeTags.IS_MOUNTAIN)) return 0x8A8A8A;
        if (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA) || b.is(BiomeTags.IS_JUNGLE)) return 0x2D6E2D;
        if (b.is(BiomeTags.IS_SAVANNA) || b.is(BiomeTags.IS_BADLANDS)) return 0xC0A050;
        float t = b.value().getBaseTemperature();
        return t < 0.15F ? 0xE8E8F0 : t > 1.5F ? 0xE0C060 : 0x7DA85A;
    }
}

package net.blandfordsfox.client;

import net.blandfordsfox.BlandfordsFoxMod;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * /middleeast skycolor: a small panel for trying sky colours on the biome you stand in.
 * Changes are on this client only and last until you leave the world.
 *
 * <p>Vanilla works a biome's sky colour out from its temperature:
 * hsv(0.6222 - 0.05 t/3, 0.5 + 0.1 t/3, 1) with t/3 clamped to -1..1. The panel stays inside
 * what vanilla biomes get from that (temperatures -0.7 to 2.0): hue and saturation within
 * those bounds, full brightness.
 */
@EventBusSubscriber(modid = BlandfordsFoxMod.MOD_ID, value = Dist.CLIENT)
public final class SkyColor {
    private SkyColor() {}

    public static final float TEMP_MIN = -0.7F, TEMP_MAX = 2.0F;

    /** Vanilla's sky colour for a temperature (OverworldBiomes.calculateSkyColor). */
    public static int vanilla(float temperature) {
        return Mth.hsvToRgb(hueFor(temperature), satFor(temperature), 1.0F);
    }

    public static float hueFor(float temperature) {
        return 0.62222224F - Mth.clamp(temperature / 3.0F, -1.0F, 1.0F) * 0.05F;
    }

    public static float satFor(float temperature) {
        return 0.5F + Mth.clamp(temperature / 3.0F, -1.0F, 1.0F) * 0.1F;
    }

    public static final float HUE_MIN = hueFor(TEMP_MAX), HUE_MAX = hueFor(TEMP_MIN);
    public static final float SAT_MIN = satFor(TEMP_MIN), SAT_MAX = satFor(TEMP_MAX);

    /** The biome's own sky colour before any change, so it can be put back. */
    private static final Map<Biome, Integer> ORIGINAL = new IdentityHashMap<>();

    public static int get(Biome biome) {
        return biome.getSpecialEffects().skyColor;
    }

    public static int original(Biome biome) {
        return ORIGINAL.getOrDefault(biome, get(biome));
    }

    public static void set(Biome biome, int rgb) {
        ORIGINAL.putIfAbsent(biome, get(biome));
        biome.getSpecialEffects().skyColor = rgb & 0xFFFFFF;
    }

    // ------------------------------------------------------------- command

    private static int openIn = -1;

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("middleeast")
                .then(Commands.literal("skycolor").executes(ctx -> {
                    openIn = 2;   // after the chat screen has closed
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (openIn < 0 || --openIn > 0) return;
        openIn = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Holder<Biome> biome = mc.level.getBiome(mc.player.blockPosition());
        String name = biome.unwrapKey().map(k -> k.location().toString()).orElse("?");
        mc.setScreen(new SkyColorScreen(biome.value(), name));
    }
}

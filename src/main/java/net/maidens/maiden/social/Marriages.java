package net.maidens.maiden.social;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Who is married to which maiden, for the whole world: a player can only have one spouse at a time. */
public final class Marriages extends SavedData {
    private static final String NAME = "maidens_marriages";
    private final Map<UUID, UUID> spouseOf = new HashMap<>();   // player -> maiden

    public static Marriages get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Marriages::new, Marriages::load, null), NAME);
    }

    public UUID spouse(UUID player) {
        return spouseOf.get(player);
    }

    public void marry(UUID player, UUID maiden) {
        spouseOf.put(player, maiden);
        setDirty();
    }

    /** When a maiden dies, whoever was married to her is free again. */
    public void widow(UUID maiden) {
        if (spouseOf.values().removeIf(maiden::equals)) setDirty();
    }

    private static Marriages load(CompoundTag tag, HolderLookup.Provider registries) {
        Marriages m = new Marriages();
        for (String k : tag.getAllKeys()) {
            try {
                m.spouseOf.put(UUID.fromString(k), tag.getUUID(k));
            } catch (IllegalArgumentException ignored) { }
        }
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        spouseOf.forEach((p, m) -> tag.putUUID(p.toString(), m));
        return tag;
    }
}

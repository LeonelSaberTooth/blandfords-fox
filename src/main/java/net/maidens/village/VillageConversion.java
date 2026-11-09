package net.maidens.village;

import net.maidens.MaidensMod;
import net.maidens.core.ModEntities;
import net.maidens.maiden.Maiden;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Turns half of the villages into maiden villages, without touching world generation:
 * a villager placed by a village's own buildings, appearing in the world for the first time
 * (vanilla marks those to pick a profession on their first tick), is swapped for a maiden in the same spot
 * if its village's coin flip says so. Babies are left out. Every other villager is left alone.
 */
@EventBusSubscriber(modid = MaidensMod.MOD_ID)
public final class VillageConversion {
    private VillageConversion() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Villager villager)) return;
        if (!fromVillageStructure(villager)) return;
        // decided on the next tick, once the chunk has finished loading: then looking up the village is safe
        level.getServer().execute(() -> convert(level, villager));
    }

    /**
     * Every 5 s, catches structure villagers in maiden villages that slipped past the join check
     * (some are loaded without ever ticking or being converted).
     */
    static void sweep(ServerLevel level, Villages villages) {
        for (VillageRecord v : villages.all()) {
            if (!level.isLoaded(v.centre())) continue;
            for (Villager villager : level.getEntitiesOfClass(Villager.class, AABB.of(v.box).inflate(16), VillageConversion::fromVillageStructure)) {
                convert(level, villager);
            }
        }
    }

    private static void convert(ServerLevel level, Villager villager) {
        if (villager.isRemoved()) return;
        StructureStart village = Villages.villageAt(level, villager.blockPosition());
        if (village == null || !Villages.rollsMaidens(level, village)) return;
        VillageRecord record = Villages.get(level).record(level, village);
        villager.discard();
        if (villager.isBaby()) return;
        Maiden m = ModEntities.MAIDEN.get().create(level);
        if (m == null) return;
        m.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), 0);
        m.ensureInitialised();
        m.residence().joinVillage(record.id);
        level.addFreshEntity(m);
    }

    /** Vanilla's "pick a profession on the first tick" flag, read directly (much cheaper than saving the villager). */
    private static final Field ASSIGN_PROFESSION = findFlag();

    private static Field findFlag() {
        try {
            return ObfuscationReflectionHelper.findField(Villager.class, "assignProfessionWhenSpawned");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A villager the village structure placed, which has never ticked yet. */
    private static boolean fromVillageStructure(Villager v) {
        if (ASSIGN_PROFESSION != null) {
            try {
                return ASSIGN_PROFESSION.getBoolean(v);
            } catch (IllegalAccessException ignored) {
                // fall back to reading it from the villager's save data
            }
        }
        CompoundTag tag = new CompoundTag();
        v.addAdditionalSaveData(tag);
        return tag.getBoolean("AssignProfessionWhenSpawned");
    }
}

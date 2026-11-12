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
 * if its village's coin flip says so (a baby for a child). Every other villager is left alone.
 */
@EventBusSubscriber(modid = MaidensMod.MOD_ID)
public final class VillageConversion {
    private VillageConversion() {}

    /** Structure villagers let back in after all (their village isn't a maiden village). */
    private static final java.util.Set<java.util.UUID> LET_IN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * A structure villager doesn't come into the world straight away: whether her village is a maiden village is
     * decided on the next tick (once the chunk has finished loading, when looking up the village is safe). If it is,
     * a maiden comes in her place; if not, she comes in after all. (Swapping her out after she'd come in could leave
     * a villager players still saw but that wasn't there.)
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Villager villager)) return;
        if (LET_IN.remove(villager.getUUID()) || !fromVillageStructure(villager)) return;
        event.setCanceled(true);
        level.getServer().execute(() -> convert(level, villager));
    }

    /** Iron golems aren't made in maiden villages (one a player builds is). */
    @SubscribeEvent
    public static void onGolem(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof net.minecraft.world.entity.animal.IronGolem golem)) return;
        if (golem.isPlayerCreated()) return;
        for (VillageRecord v : Villages.get(level).all()) {
            var b = (v.wallBox != null ? v.wallBox : v.box).inflatedBy(24);
            if (b.isInside(new net.minecraft.core.BlockPos(golem.getBlockX(), b.minY(), golem.getBlockZ()))) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Every 5 s, catches structure villagers in maiden villages that slipped past the join check
     * (some are loaded without ever ticking or being converted).
     */
    static void sweep(ServerLevel level, Villages villages) {
        for (VillageRecord v : villages.all()) {
            if (!level.isLoaded(v.centre())) continue;
            // structure villagers that slipped through, and any jobless villager who wandered or spawned in: a maiden
            // village has maidens (a villager someone has traded with is left alone)
            for (Villager villager : level.getEntitiesOfClass(Villager.class, AABB.of(v.box).inflate(16),
                    vl -> fromVillageStructure(vl) || vl.getVillagerXp() == 0 && vl.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE)) {
                replace(level, villager, v);
            }
        }
    }

    private static void convert(ServerLevel level, Villager villager) {
        StructureStart village = Villages.villageAt(level, villager.blockPosition());
        if (village == null || !Villages.rollsMaidens(level, village)) {
            // not a maiden village: she comes in after all
            if (!villager.isRemoved() && level.isLoaded(villager.blockPosition())) {
                LET_IN.add(villager.getUUID());
                level.addFreshEntity(villager);
            }
            return;
        }
        replace(level, villager, Villages.get(level).record(level, village));
    }

    /** The villager goes (if she ever came in); a maiden of that village takes her place (a baby becomes a child). */
    private static void replace(ServerLevel level, Villager villager, VillageRecord record) {
        if (!villager.isRemoved()) villager.discard();
        Maiden m = ModEntities.MAIDEN.get().create(level);
        if (m == null) return;
        m.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), 0);
        m.ensureInitialised();
        // who she is: a baby villager becomes a child; anyone else, as the village's people come
        if (villager.isBaby()) {
            m.setKind(level.getRandom().nextBoolean() ? Maiden.Kind.MAN : Maiden.Kind.WOMAN);
            m.makeChild();
        } else People.roll(m, level.getRandom());
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

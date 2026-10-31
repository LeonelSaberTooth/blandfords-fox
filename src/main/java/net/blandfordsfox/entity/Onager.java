package net.blandfordsfox.entity;

import net.blandfordsfox.BlandfordsFoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * A vanilla {@link Donkey} in every way: tamed by riding it until it accepts you, takes a
 * saddle and a chest, is led on a lead, eats wheat, hay, apples, sugar and golden food, and
 * breeds. Two onagers have an onager foal; with a horse it is a mule, like a donkey.
 * Spawns in herds in deserts.
 */
public class Onager extends Donkey {
    /** Blocks it can spawn on: desert sand and rock. */
    public static final TagKey<Block> SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "onager_spawnable_on"));

    public Onager(EntityType<? extends Donkey> type, Level level) {
        super(type, level);
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        if (otherParent instanceof Onager) {
            Onager foal = BlandfordsFoxMod.ONAGER.get().create(level);
            if (foal != null) this.setOffspringAttributes(otherParent, foal);
            return foal;
        }
        return super.getBreedOffspring(level, otherParent);
    }

    public static boolean checkOnagerSpawnRules(EntityType<Onager> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                                BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }
}

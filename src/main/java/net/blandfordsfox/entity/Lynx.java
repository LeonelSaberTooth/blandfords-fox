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
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The caracal of the northern forests: behaves exactly like the {@link Caracal} (so like a
 * vanilla ocelot), with its own look, gait speeds and spawn places (every taiga, and groves).
 */
public class Lynx extends Caracal {
    /** Blocks it can spawn on: taiga and grove ground. */
    public static final TagKey<Block> SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "lynx_spawnable_on"));

    // ground speeds of the lynx's own animations, measured from the keyframes like the caracal's
    static final double LYNX_WALK = 0.50, LYNX_RUN = 4.8, LYNX_SNEAK = 0.37;

    public Lynx(EntityType<? extends Ocelot> type, Level level) {
        super(type, level);
    }

    @Override
    protected String animationName() {
        return "lynx";
    }

    @Override
    protected double walkSpeed() { return LYNX_WALK; }

    @Override
    protected double runSpeed() { return LYNX_RUN; }

    @Override
    protected double sneakSpeed() { return LYNX_SNEAK; }

    /** Kittens from lynxes are lynxes. */
    @Override
    @Nullable
    public Ocelot getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return BlandfordsFoxMod.LYNX.get().create(level);
    }

    /** On taiga or grove ground, at or above sea level. */
    public static boolean checkLynxSpawnRules(EntityType<Lynx> type, ServerLevelAccessor level,
                                             MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return pos.getY() >= level.getSeaLevel() && level.getBlockState(pos.below()).is(SPAWNABLE_ON);
    }
}

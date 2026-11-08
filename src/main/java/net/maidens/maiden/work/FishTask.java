package net.maidens.maiden.work;

import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Stands on the shore of the nearest still water within 12 blocks and catches something from the fishing loot every 10-30 seconds. */
final class FishTask extends WorkTask {
    private BlockPos water, shore;
    private int bite;

    FishTask(Maiden m) {
        super(m);
    }

    @Override
    public void reset() {
        super.reset();
        water = shore = null;
    }

    @Override
    public void tick(ServerLevel level) {
        if (water == null || !level.getFluidState(water).is(Fluids.WATER)) {
            if (--scan > 0) return;
            scan = 40;
            water = findWater(level);
            shore = water == null ? null : shoreFor(level, water);
            if (water == null || shore == null) { water = null; idleNear(job().workPos); return; }
            bite = 200 + m.getRandom().nextInt(400);
            stuck = 0;
        }
        if (stuck > 300) { water = null; return; }
        if (!reach(shore, 2.0)) return;
        m.getLookControl().setLookAt(Vec3.atCenterOf(water));
        if (m.getRandom().nextInt(40) == 0) {
            level.sendParticles(ParticleTypes.FISHING, water.getX() + 0.5, water.getY() + 0.95, water.getZ() + 0.5, 3, 0.2, 0.0, 0.2, 0.0);
        }
        if (--bite > 0) return;
        bite = 200 + m.getRandom().nextInt(400);
        m.swing(InteractionHand.MAIN_HAND);
        level.sendParticles(ParticleTypes.SPLASH, water.getX() + 0.5, water.getY() + 1.0, water.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.1);
        level.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .withParameter(LootContextParams.THIS_ENTITY, m)
                .create(LootContextParamSets.FISHING);
        for (ItemStack s : table.getRandomItems(params)) job().store(s);
        if (job().bagFull()) job().giveUp("My bag's full of fish! Come and empty it.");
    }

    private BlockPos findWater(ServerLevel level) {
        BlockPos c = centre();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-12, -4, -12), c.offset(12, 3, 12))) {
            if (!level.getFluidState(p).is(Fluids.WATER) || !level.getFluidState(p).isSource() || !level.getBlockState(p.above()).isAir()) continue;
            if (shoreFor(level, p) == null) continue;
            double d = p.distSqr(c);
            if (d < bestD) { bestD = d; best = p.immutable(); }
        }
        return best;
    }

    /** Dry ground next to the water where she can stand. */
    private static BlockPos shoreFor(ServerLevel level, BlockPos w) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos p = w.relative(d).above(dy);
                if (level.getBlockState(p.below()).isSolid() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) return p;
            }
        }
        return null;
    }

    @Override
    public String debug() {
        return "water " + (water == null ? "-" : water.toShortString()) + " bite " + bite + " " + super.debug();
    }
}

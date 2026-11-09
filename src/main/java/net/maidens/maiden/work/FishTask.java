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

import java.util.HashSet;
import java.util.Set;

/**
 * Fishes the nearest open water within 24 blocks of where she was hired: walks to the shore, casts (the bobber
 * flies out and floats), waits 10-30 s for a bite (the bobber dips and splashes), reels in a catch from the
 * fishing loot, and casts again. Water she can't get to is skipped.
 */
final class FishTask extends WorkTask {
    private static final int RANGE = 24, CAST_TICKS = 12, BITE_TICKS = 16;
    private enum Phase { FIND, CAST, WAIT, BITE, PAUSE }

    private final Set<BlockPos> unreachable = new HashSet<>();
    private BlockPos water, shore;
    private Phase phase = Phase.FIND;
    private int timer;
    private Bobber bobber;
    private Vec3 castFrom;

    FishTask(Maiden m) {
        super(m);
    }

    @Override
    public void reset() {
        super.reset();
        stop();
        unreachable.clear();
        water = shore = null;
        phase = Phase.FIND;
    }

    @Override
    public void stop() {
        if (bobber != null) bobber.discard();
        bobber = null;
        phase = Phase.FIND;      // back to the shore first
    }

    @Override
    public void tick(ServerLevel level) {
        if (checkBag()) { stop(); return; }
        if (water == null || !level.getFluidState(water).is(Fluids.WATER)) {
            stop();
            phase = Phase.FIND;
            if (--scan > 0) return;
            scan = 40;
            water = findWater(level);
            shore = water == null ? null : shoreFor(level, water);
            if (water == null || shore == null) { water = null; idleNear(job().workPos); return; }
            stuck = 0;
        }
        if (stuck > 400) { unreachable.add(water); water = null; return; }
        if (phase == Phase.FIND) {
            if (!reach(shore, 2.0)) return;
            phase = Phase.PAUSE;
            timer = 10;
        }
        Vec3 float_ = new Vec3(water.getX() + 0.5, water.getY() + 0.9, water.getZ() + 0.5);
        m.getNavigation().stop();
        m.getLookControl().setLookAt(float_.x, float_.y, float_.z);
        switch (phase) {
            case PAUSE -> {
                if (--timer > 0) return;
                // cast: the bobber flies from her hand out over the water
                m.swing(InteractionHand.MAIN_HAND);
                level.playSound(null, m.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (m.getRandom().nextFloat() * 0.4F + 0.8F));
                castFrom = m.getEyePosition().add(m.getLookAngle().scale(0.6));
                bobber = Bobber.cast(m, castFrom);
                phase = Phase.CAST;
                timer = 0;
            }
            case CAST -> {
                if (bobber == null) { phase = Phase.PAUSE; return; }
                double t = (double) ++timer / CAST_TICKS;
                Vec3 p = castFrom.lerp(float_, t).add(0, Math.sin(t * Math.PI) * 1.2, 0);
                bobber.moveTo(p.x, p.y, p.z);
                if (timer >= CAST_TICKS) {
                    level.sendParticles(ParticleTypes.SPLASH, float_.x, float_.y + 0.1, float_.z, 6, 0.1, 0.0, 0.1, 0.0);
                    phase = Phase.WAIT;
                    timer = 200 + m.getRandom().nextInt(400);
                }
            }
            case WAIT -> {
                if (bobber == null || bobber.isRemoved()) { phase = Phase.PAUSE; timer = 20; return; }
                bobber.moveTo(float_.x, float_.y + Math.sin(m.tickCount * 0.15) * 0.04, float_.z);
                if (m.getRandom().nextInt(50) == 0) level.sendParticles(ParticleTypes.FISHING, float_.x, float_.y + 0.05, float_.z, 2, 0.15, 0.0, 0.15, 0.0);
                if (--timer <= 0) {
                    phase = Phase.BITE;
                    timer = BITE_TICKS;
                    level.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
                }
            }
            case BITE -> {
                if (bobber == null || bobber.isRemoved()) { phase = Phase.PAUSE; timer = 20; return; }
                bobber.moveTo(float_.x, float_.y - 0.3, float_.z);
                level.sendParticles(ParticleTypes.SPLASH, float_.x, float_.y + 0.1, float_.z, 3, 0.2, 0.0, 0.2, 0.0);
                if (--timer > 0) return;
                reelIn(level, float_);
            }
            default -> phase = Phase.PAUSE;
        }
    }

    /** Pulls the line in with whatever was on it. */
    private void reelIn(ServerLevel level, Vec3 at) {
        m.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, m.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 0.4F / (m.getRandom().nextFloat() * 0.4F + 0.8F));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, at)
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .withParameter(LootContextParams.THIS_ENTITY, m)
                .create(LootContextParamSets.FISHING);
        for (ItemStack s : table.getRandomItems(params)) job().store(s);
        if (bobber != null) bobber.discard();
        bobber = null;
        phase = Phase.PAUSE;
        timer = 30;
    }

    private BlockPos findWater(ServerLevel level) {
        BlockPos c = centre();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-RANGE, -6, -RANGE), c.offset(RANGE, 4, RANGE))) {
            if (unreachable.contains(p) || !level.getFluidState(p).is(Fluids.WATER) || !level.getFluidState(p).isSource()
                    || !level.getBlockState(p.above()).isAir()) continue;
            double d = p.distSqr(m.blockPosition());
            if (d >= bestD || shoreFor(level, p) == null) continue;
            bestD = d;
            best = p.immutable();
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
        return "water " + (water == null ? "-" : water.toShortString()) + " " + phase + " " + timer + " bobber " + (bobber != null) + " " + super.debug();
    }
}

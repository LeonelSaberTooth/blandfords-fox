package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.maidens.maiden.home.Schedule;
import net.maidens.village.Farms;
import net.maidens.village.VillageRecord;
import net.maidens.village.VillageSocial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A village farmer (and anyone without a workstation) works the village's fields in work hours: walks to a ripe crop,
 * harvests it and plants it again, then the next one.
 */
final class FieldWorkGoal extends Goal {
    private final Maiden m;
    private int cooldown, ticks, repath;
    private BlockPos crop;

    FieldWorkGoal(Maiden m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean farmer() {
        VillagerProfession p = m.profession();
        return p == null || p == VillagerProfession.FARMER;
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 100;
        if (!m.hasVillageDuties() || m.employment().isWorking() || m.isGuard() || m.isRoyal() || m.isBaby() || !m.isFree() || !farmer()
                || Schedule.now(m) != Schedule.Activity.WORK || !(m.level() instanceof ServerLevel level)) return false;
        VillageRecord v = VillageSocial.villageOf(m);
        if (v == null || !v.farmsPlanned) return false;
        crop = Farms.ripe(level, v, m.blockPosition());
        return crop != null;
    }

    @Override
    public boolean canContinueToUse() {
        return crop != null && ticks < 1200 && m.hasVillageDuties() && !m.employment().isWorking() && m.isFree() && Schedule.now(m) == Schedule.Activity.WORK;
    }

    @Override
    public void start() {
        ticks = 0;
        repath = 0;
    }

    @Override
    public void stop() {
        crop = null;
        m.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (!(m.level() instanceof ServerLevel level) || crop == null) return;
        Vec3 c = Vec3.atCenterOf(crop);
        if (m.distanceToSqr(c) > 2.5 * 2.5) {
            if (--repath <= 0) {
                repath = 30;
                m.getNavigation().moveTo(c.x, crop.getY(), c.z, 0.55);
            }
            return;
        }
        m.getNavigation().stop();
        m.getLookControl().setLookAt(c);
        BlockState s = level.getBlockState(crop);
        if (s.getBlock() instanceof CropBlock cb && cb.isMaxAge(s)) {
            // harvested and planted again in one go
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), c.x, c.y, c.z, 8, 0.25, 0.25, 0.25, 0.05);
            level.playSound(null, crop, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
            level.setBlock(crop, cb.getStateForAge(0), Block.UPDATE_CLIENTS);
            m.swing(InteractionHand.MAIN_HAND);
            // the harvest goes to the village's store (all but the seed planted again)
            VillageRecord home = VillageSocial.villageOf(m);
            if (home != null) {
                boolean seedBack = false;
                for (net.minecraft.world.item.ItemStack d : Block.getDrops(s, level, crop, null)) {
                    if (!seedBack && d.is(s.getBlock().asItem())) {
                        d.shrink(1);
                        seedBack = true;
                    }
                    home.storeAdd(d);
                }
                net.maidens.village.Villages.get(level).setDirty();
            }
        }
        VillageRecord v = VillageSocial.villageOf(m);
        crop = v == null ? null : Farms.ripe(level, v, m.blockPosition());
        ticks = 0;
    }
}

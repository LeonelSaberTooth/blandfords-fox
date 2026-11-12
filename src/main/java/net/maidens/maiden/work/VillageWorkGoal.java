package net.maidens.maiden.work;

import net.maidens.maiden.Maiden;
import net.maidens.village.VillageRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * A village worker at her spot in work hours, going through the motions (nothing is really cut, dug or picked: the
 * village gets its day's share all the same). She picks something to work on near her spot (a ripe crop at the edge
 * of a field, a tree trunk, a rock face, open water), stands next to it and works at it for a while (swinging her
 * tool, bits flying off, the sound of it; a fisher casts her line and waits), then moves on to the next. A guard keeps
 * her gate, looking out of it, or walks round the walls from corner to corner.
 */
public final class VillageWorkGoal extends Goal {
    private static final int RANGE = 8;

    private final Maiden m;
    private int cooldown;
    @Nullable private BlockPos stand, target, water;
    private int ticks, timer, arrive, swing, failures;
    private boolean working;
    @Nullable private Bobber bobber;
    private List<BlockPos> route = List.of();
    private int leg;

    public VillageWorkGoal(Maiden m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 10;
        return ready() && near();
    }

    @Override
    public boolean canContinueToUse() {
        return ready() && (patrols() || farFrom() < 16 * 16);
    }

    private boolean ready() {
        return m.villageWork().onDuty() && m.isFree() && !m.isSleeping() && m.getTarget() == null && m.villageWork().village() != null;
    }

    private boolean patrols() {
        VillageRecord v = m.villageWork().village();
        return v != null && m.villageWork().patrols(v);
    }

    private double farFrom() {
        VillageRecord v = m.villageWork().village();
        BlockPos p = v == null ? null : m.villageWork().place(v);
        return p == null ? 0 : m.distanceToSqr(Vec3.atBottomCenterOf(p));
    }

    /** At her spot (a guard who walks round can start from anywhere). */
    private boolean near() {
        return patrols() || farFrom() < 9 * 9;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        stand = target = water = null;
        working = false;
        failures = 0;
        timer = 0;
        arrive = ticks;
        VillageRecord v = m.villageWork().village();
        if (v != null && patrols()) {
            route = m.villageWork().round(v);
            // from the corner nearest her, round one way or the other
            leg = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < route.size(); i++) {
                double d = m.distanceToSqr(Vec3.atBottomCenterOf(route.get(i)));
                if (d < best) {
                    best = d;
                    leg = i;
                }
            }
        }
    }

    @Override
    public void stop() {
        m.getNavigation().stop();
        reelIn(false);
        stand = target = null;
    }

    @Override
    public void tick() {
        if (!(m.level() instanceof ServerLevel level)) return;
        VillageRecord v = m.villageWork().village();
        if (v == null) return;
        ticks++;
        Job job = m.villageWork().job();
        if (job == Job.GUARD) {
            if (patrols()) patrol();
            else keepGate(v);
            return;
        }
        if (stand == null) {
            if (--timer > 0) {
                lookAround();
                return;
            }
            if (!pick(level, job)) {
                timer = 100;          // nothing to work on just now: she looks about and tries again
                return;
            }
            working = false;
            arrive = 0;
            m.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.5);
        }
        Vec3 look = job == Job.FISH ? Vec3.atBottomCenterOf(water).add(0, 0.9, 0) : Vec3.atCenterOf(target);
        if (!working) {
            arrive++;
            double d = m.distanceToSqr(Vec3.atBottomCenterOf(stand));
            if (d < 0.8 * 0.8 || arrive > 100 && m.distanceToSqr(look) < 3.5 * 3.5) {
                working = true;
                m.getNavigation().stop();
                timer = job == Job.FISH ? 0 : 300 + m.getRandom().nextInt(400);
                swing = 10;
            } else if (arrive > 160) {
                // can't get there: something else
                stand = null;
                timer = ++failures > 3 ? 100 : 0;
                return;
            } else {
                if (arrive % 40 == 0) m.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.5);
                return;
            }
        }
        failures = 0;
        m.getLookControl().setLookAt(look.x, look.y, look.z);
        if (job == Job.FISH) fish(level, look);
        else hit(level, job);
    }

    // ------------------------------------------------------------------ the work

    /** Swings at it every second or so, with bits of it flying and the sound of it, then moves on. */
    private void hit(ServerLevel level, Job job) {
        BlockState s = level.getBlockState(target);
        if (s.isAir() || --timer <= 0) {
            stand = null;
            timer = 10 + m.getRandom().nextInt(30);
            return;
        }
        if (--swing > 0) return;
        swing = (job == Job.FARM ? 18 : 14) + m.getRandom().nextInt(10);
        m.swing(InteractionHand.MAIN_HAND);
        Vec3 c = Vec3.atCenterOf(target);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), c.x, c.y + (job == Job.FARM ? 0 : 0.3), c.z, job == Job.FARM ? 3 : 5,
                0.25, 0.25, 0.25, 0.05);
        var sound = s.getSoundType();
        level.playSound(null, target, job == Job.FARM ? sound.getBreakSound() : sound.getHitSound(), SoundSource.NEUTRAL,
                job == Job.FARM ? 0.3F : 0.5F, sound.getPitch() * 0.8F);
    }

    /** Casts out, waits for a bite (the float dips), reels in, and again. */
    private void fish(ServerLevel level, Vec3 at) {
        timer++;
        if (bobber == null) {
            if (timer < 40) return;
            m.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, m.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (m.getRandom().nextFloat() * 0.4F + 0.8F));
            bobber = Bobber.cast(m, m.getEyePosition().add(m.getLookAngle().scale(0.6)));
            timer = 0;
            arrive = 200 + m.getRandom().nextInt(400);      // how long until a bite
            return;
        }
        if (bobber.isRemoved()) {
            bobber = null;
            timer = 0;
            return;
        }
        Vec3 from = m.getEyePosition();
        if (timer <= 12) {
            double t = timer / 12.0;
            Vec3 p = from.lerp(at, t).add(0, Math.sin(t * Math.PI) * 1.2, 0);
            bobber.moveTo(p.x, p.y, p.z);
            if (timer == 12) level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.1, at.z, 6, 0.1, 0.0, 0.1, 0.0);
            return;
        }
        if (timer < arrive) {
            if (timer % 5 == 0) bobber.moveTo(at.x, at.y + Math.sin(timer * 0.15) * 0.04, at.z);
            if (m.getRandom().nextInt(50) == 0) level.sendParticles(ParticleTypes.FISHING, at.x, at.y + 0.05, at.z, 2, 0.15, 0.0, 0.15, 0.0);
            return;
        }
        if (timer == arrive) level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
        if (timer < arrive + 20) {
            bobber.moveTo(at.x, at.y - 0.3, at.z);
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.1, at.z, 3, 0.2, 0.0, 0.2, 0.0);
            return;
        }
        reelIn(true);
        // now and then she moves along the shore
        if (m.getRandom().nextInt(3) == 0) stand = null;
    }

    private void reelIn(boolean sound) {
        if (bobber == null) return;
        if (sound) {
            m.swing(InteractionHand.MAIN_HAND);
            m.level().playSound(null, m.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 0.4F / (m.getRandom().nextFloat() * 0.4F + 0.8F));
        }
        bobber.discard();
        bobber = null;
        timer = 0;
    }

    // ------------------------------------------------------------------ guards

    private void keepGate(VillageRecord v) {
        BlockPos post = m.villageWork().gatePost(v);
        Vec3 p = Vec3.atBottomCenterOf(post);
        if (m.distanceToSqr(p) > 2.0 * 2.0) {
            if (ticks % 40 == 1 || m.getNavigation().isDone()) m.getNavigation().moveTo(p.x, p.y, p.z, 0.5);
            return;
        }
        m.getNavigation().stop();
        BlockPos out = m.villageWork().outside(v);
        // mostly looking out of the gate, now and then up and down the road
        if (out != null && ticks % 200 < 150) m.getLookControl().setLookAt(out.getX() + 0.5, out.getY() + 1.5, out.getZ() + 0.5);
        else lookAround();
    }

    private void patrol() {
        if (route.isEmpty()) {
            lookAround();
            return;
        }
        BlockPos to = route.get(Math.floorMod(leg, route.size()));
        Vec3 c = Vec3.atBottomCenterOf(to);
        double d = m.distanceToSqr(c);
        if (timer > 0) {           // a pause at each corner, looking out
            timer--;
            lookAround();
            if (timer == 0) leg += m.getUUID().hashCode() % 2 == 0 ? 1 : -1;
            return;
        }
        if (d < 3 * 3 || ticks - arrive > 1200) {
            m.getNavigation().stop();
            timer = 40 + m.getRandom().nextInt(60);
            arrive = ticks;
            return;
        }
        if (ticks % 40 == 1 || m.getNavigation().isDone()) {
            Vec3 step = d > 30 * 30 ? LandRandomPos.getPosTowards(m, 20, 7, c) : c;
            if (step == null) step = c;
            if (!m.getNavigation().moveTo(step.x, step.y, step.z, 0.5) && ticks - arrive > 200) {
                leg += m.getUUID().hashCode() % 2 == 0 ? 1 : -1;        // no way there: the next one
                arrive = ticks;
            }
        }
    }

    private void lookAround() {
        if (m.getRandom().nextInt(60) == 0) {
            double a = m.getRandom().nextDouble() * Math.PI * 2;
            m.getLookControl().setLookAt(m.getX() + Math.cos(a) * 6, m.getEyeY() + m.getRandom().nextGaussian() * 0.5, m.getZ() + Math.sin(a) * 6);
        }
    }

    // ------------------------------------------------------------------ finding something to work on

    /** Something to work on near her spot, and where to stand for it. */
    private boolean pick(ServerLevel level, Job job) {
        BlockPos c = m.villageWork().spot();
        if (c == null || !level.hasChunksAt(c.offset(-RANGE, 0, -RANGE), c.offset(RANGE, 0, RANGE))) return false;
        VillageRecord v = m.villageWork().village();
        List<BlockPos[]> found = new ArrayList<>();
        int dy = job == Job.MINE ? 5 : 4;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-RANGE, -dy, -RANGE), c.offset(RANGE, dy, RANGE))) {
            BlockState s = level.getBlockState(p);
            BlockPos[] f = switch (job) {
                case FARM -> s.getBlock() instanceof CropBlock ? standBy(level, p, 0, 0, q -> !level.getBlockState(q.below()).is(Blocks.FARMLAND)) : null;
                case WOOD -> s.is(BlockTags.LOGS) && (v == null || v.blueprint.expected(p) == null) && tree(level, p) ? standBy(level, p, -1, 0, q -> true) : null;
                case MINE -> rock(s) && (v == null || v.blueprint.expected(p) == null) ? standBy(level, p, -1, 0, q -> true) : null;
                case FISH -> shore(level, p);
                default -> null;
            };
            if (f != null) found.add(f);
            if (found.size() > 60) break;
        }
        if (found.isEmpty()) return false;
        BlockPos[] f = found.get(m.getRandom().nextInt(found.size()));
        stand = f[0];
        target = f[1];
        water = job == Job.FISH ? f[1] : null;
        return true;
    }

    /** A spot next to a block where she can stand (her feet from its level + from to + to), that passes the test. */
    @Nullable
    private BlockPos[] standBy(ServerLevel level, BlockPos b, int from, int to, Predicate<BlockPos> ok) {
        for (Direction d : Direction.Plane.HORIZONTAL)
            for (int y = from; y <= to; y++) {
                BlockPos q = b.relative(d).above(y);
                if (standable(level, q) && ok.test(q)) return new BlockPos[]{q.immutable(), b.immutable()};
            }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos q) {
        return level.getBlockState(q.below()).isSolid() && level.getBlockState(q).getCollisionShape(level, q).isEmpty()
                && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty() && level.getFluidState(q).isEmpty()
                && level.getFluidState(q.below()).isEmpty();
    }

    /** A trunk with natural leaves above it (not a house's beam). */
    private static boolean tree(ServerLevel level, BlockPos p) {
        for (int y = 1; y <= 8; y++) {
            BlockState s = level.getBlockState(p.above(y));
            if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) return true;
            if (!s.is(BlockTags.LOGS)) return false;
        }
        return false;
    }

    private static boolean rock(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES) || s.is(Blocks.SANDSTONE)
                || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE);
    }

    /** Dry ground on the shore, and open water two to four blocks out from it to cast into. */
    @Nullable
    private static BlockPos[] shore(ServerLevel level, BlockPos p) {
        if (!standable(level, p)) return null;
        for (Direction d : Direction.Plane.HORIZONTAL)
            for (int k = 4; k >= 2; k--) {
                BlockPos w = p.relative(d, k).below();
                if (!level.getFluidState(w).is(Fluids.WATER) || !level.getFluidState(w).isSource() || !level.getBlockState(w.above()).isAir()) continue;
                // the water starts right by her (she's on the shore, not across a field from it)
                if (!level.getFluidState(p.relative(d).below()).is(Fluids.WATER) && !level.getFluidState(p.relative(d)).is(Fluids.WATER)) continue;
                return new BlockPos[]{p.immutable(), w.immutable()};
            }
        return null;
    }
}

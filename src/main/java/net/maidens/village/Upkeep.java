package net.maidens.village;

import net.maidens.MaidensMod;
import net.maidens.core.MaidensConfig;
import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * Keeping a maiden village as it was: a slow scan of the blueprint finds missing blocks and blocked roads and
 * lists them as {@link Chore}s on the village; free residents take them one at a time
 * ({@code VillageChoreGoal}), walk there and fix them. Repairs cost nothing for now.
 */
public final class Upkeep {
    /** Blocks that make up roads; whatever stands on them that the blueprint doesn't expect is cleared away. */
    public static final TagKey<Block> ROAD = BlockTags.create(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "village_road"));
    private static final int SCAN_PER_TICK = 8192, MAX_CHORES = 128, REACH = 48, CLAIM_TIMEOUT = 1200, RETRY_LATER = 6000;

    private Upkeep() {}

    /** Every 5 s: look over part of each village for new chores; forget chores that fixed themselves. */
    static void tick(ServerLevel level, Villages villages) {
        if (!MaidensConfig.REPAIRS.get()) return;
        long now = level.getGameTime();
        for (VillageRecord v : villages.all()) {
            for (Iterator<Chore> it = v.chores.values().iterator(); it.hasNext(); ) {
                Chore c = it.next();
                if (level.isLoaded(c.pos) && !stillNeeded(level, v, c)) it.remove();
                else if (c.takenBy != null && now - c.takenAt > CLAIM_TIMEOUT) c.takenBy = null;
            }
            if (v.chores.size() >= MAX_CHORES) continue;
            boolean done = v.blueprint.scan(level, SCAN_PER_TICK, (pos, expected, building) -> {
                boolean needed = false;
                BlockState now = level.getBlockState(pos);
                if (expected != null && missing(now, expected)) {
                    needed = true;
                    add(v, new Chore(Chore.Kind.REPAIR, pos.immutable(), expected, false, v.id, now(level, building)));
                } else if (building && inTheWay(now, expected)) {
                    needed = true;
                    add(v, new Chore(Chore.Kind.CLEAR, pos.immutable(), null, false, v.id, now(level, building)));
                }
                if (expected != null && expected.is(ROAD)) {
                    for (int dy = 1; dy <= 2; dy++) {
                        BlockPos up = pos.above(dy);
                        if (v.blueprint.expected(up) == null && !v.blueprint.isBuildingSite(up) && obstacle(level, up)) {
                            add(v, new Chore(Chore.Kind.CLEAR, up, null, true, v.id, now));
                        }
                    }
                }
                return needed;
            });
            if (done) villages.setDirty();
        }
    }

    /** Building work can start straight away; repairs wait a while (the player might still be busy there). */
    private static long now(ServerLevel level, boolean building) {
        return building ? level.getGameTime() - MaidensConfig.REPAIR_DELAY.get() : level.getGameTime();
    }

    /** On a building site, something that has to go: anything but the planned block (air for "keep clear"). */
    private static boolean inTheWay(BlockState now, @Nullable BlockState expected) {
        if (now.isAir()) return false;
        if (expected == null) return true;
        return !now.is(expected.getBlock()) && !missing(now, expected);
    }

    private static void add(VillageRecord v, Chore c) {
        v.chores.putIfAbsent(c.pos.asLong(), c);
    }

    /** The blueprint block is gone (air, a plant, fluid or fire in its place). A different solid block isn't "missing". */
    public static boolean missing(BlockState now, BlockState expected) {
        if (now.is(expected.getBlock())) return false;
        return now.isAir() || now.canBeReplaced() || !now.getFluidState().isEmpty();
    }

    /** Something solid enough to walk into, standing on a road. */
    static boolean obstacle(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        var shape = s.getCollisionShape(level, p);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) > 0.5;
    }

    static boolean stillNeeded(ServerLevel level, VillageRecord v, Chore c) {
        BlockState now = level.getBlockState(c.pos);
        BlockState e = v.blueprint.expected(c.pos);
        if (c.kind == Chore.Kind.REPAIR) return e != null && missing(now, e);
        if (v.blueprint.isBuildingSite(c.pos)) return inTheWay(now, e);
        return e == null && obstacle(level, c.pos);
    }

    // ------------------------------------------------------------------ for the maidens

    /** Gives this maiden the nearest chore of her village that's been waiting long enough, or null. */
    @Nullable
    public static Chore take(Maiden m) {
        if (!MaidensConfig.REPAIRS.get() || !(m.level() instanceof ServerLevel level)) return null;
        VillageRecord v = VillageSocial.villageOf(m);
        if (v == null || v.chores.isEmpty()) return null;
        long now = level.getGameTime();
        long ready = now - MaidensConfig.REPAIR_DELAY.get();
        // lowest first (buildings go up from the ground), then nearest
        Chore best = null;
        double bestD = 0;
        for (Chore c : v.chores.values()) {
            if (c.takenBy != null || c.since > ready) continue;
            double d = m.distanceToSqr(c.pos.getX() + 0.5, c.pos.getY(), c.pos.getZ() + 0.5);
            if (d > REACH * REACH) continue;
            if (best == null || c.pos.getY() < best.pos.getY() || (c.pos.getY() == best.pos.getY() && d < bestD)) {
                bestD = d;
                best = c;
            }
        }
        if (best != null) {
            best.takenBy = m.getUUID();
            best.takenAt = now;
        }
        return best;
    }

    public static boolean stillNeeded(Maiden m, Chore c) {
        if (!(m.level() instanceof ServerLevel level)) return false;
        VillageRecord v = Villages.get(level).byId(c.villageId);
        return v != null && v.chores.get(c.pos.asLong()) == c && stillNeeded(level, v, c);
    }

    /** She gave up on it (couldn't get there): someone tries again later. */
    public static void giveBack(Chore c, boolean failed, long now) {
        c.takenBy = null;
        if (failed) c.since = now + RETRY_LATER;
    }

    /** Does the chore (she's next to it). Returns false if she has to wait (someone is standing in the way). */
    public static boolean doIt(Maiden m, Chore c) {
        ServerLevel level = (ServerLevel) m.level();
        VillageRecord v = Villages.get(level).byId(c.villageId);
        if (v == null) return true;
        m.swing(InteractionHand.MAIN_HAND);
        if (c.kind == Chore.Kind.CLEAR) {
            if (level.getBlockState(c.pos).getFluidState().isEmpty()) level.destroyBlock(c.pos, c.drop, m);
            else level.setBlock(c.pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            v.chores.remove(c.pos.asLong());
            return true;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(c.pos)).isEmpty()) return false;
        place(level, c.pos, c.block);
        v.chores.remove(c.pos.asLong());
        // the other half of a door or bed, or the rest of the same block next to it, goes back at the same time
        for (Direction d : Direction.values()) {
            BlockPos n = c.pos.relative(d);
            BlockState e = v.blueprint.expected(n);
            if (e != null && e.is(c.block.getBlock()) && missing(level.getBlockState(n), e)) {
                place(level, n, e);
                v.chores.remove(n.asLong());
            }
        }
        return true;
    }

    private static void place(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, Block.UPDATE_CLIENTS);
        SoundType sound = s.getSoundType();
        level.playSound(null, p, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    /** For the self test: how many blueprint blocks are missing within r of a spot. */
    public static int missingNear(ServerLevel level, VillageRecord v, BlockPos c, int r) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            BlockState e = v.blueprint.expected(p);
            if (e != null && missing(level.getBlockState(p), e)) n++;
        }
        return n;
    }

    public static int chores(VillageRecord v) {
        return v.chores.size();
    }
}

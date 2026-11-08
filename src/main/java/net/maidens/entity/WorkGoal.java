package net.maidens.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Her work at a work spot: farming the crops around her house, cutting the trees around where she was hired,
 * digging a staircase mine from there, or fishing at the nearest water. Everything she gets goes in her bag.
 */
final class WorkGoal extends Goal {
    private static final double REACH = 2.6;
    private static final ItemStack PICK = new ItemStack(Items.IRON_PICKAXE), AXE = new ItemStack(Items.IRON_AXE);

    private final Maiden m;
    private BlockPos target;
    private int scan, busy, repath, stuck;
    // woodcutting: the logs of the tree being cut, in order, and where its trunk stands
    private final List<BlockPos> tree = new ArrayList<>();
    private BlockPos treeBase;
    // fishing
    private BlockPos water;
    private int bite;

    WorkGoal(Maiden m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return m.getJob().atWorkSpot() && m.canWorkNow();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        target = null;
        scan = 0;
        busy = 0;
        tree.clear();
        water = null;
    }

    @Override
    public void stop() {
        m.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(m.level() instanceof ServerLevel level)) return;
        if (m.tickCount % 20 == 0) {
            m.workNote = m.getJob() + " at " + m.blockPosition().toShortString() + " target " + (target == null ? "-" : target.toShortString())
                    + " tree " + tree.size() + " water " + (water == null ? "-" : water.toShortString()) + " bite " + bite
                    + " stuck " + stuck + " busy " + busy + " step " + m.mineStep + " nav " + (m.getNavigation().isDone() ? "idle" : "moving");
        }
        switch (m.getJob()) {
            case FARM -> farm(level);
            case WOOD -> wood(level);
            case MINE -> mine(level);
            case FISH -> fish(level);
            default -> { }
        }
    }

    /** Walks towards a block until within reach; true once there (then she looks at it). */
    private boolean reach(BlockPos p, double reach) {
        Vec3 c = Vec3.atCenterOf(p);
        if (m.distanceToSqr(c) <= reach * reach) {
            m.getNavigation().stop();
            m.getLookControl().setLookAt(c.x, c.y, c.z);
            stuck = 0;
            return true;
        }
        if (--repath <= 0) {
            repath = 20;
            boolean moving = m.getNavigation().moveTo(c.x, p.getY(), c.z, 0.6);
            if (!moving || m.getNavigation().isStuck()) stuck += 20;
        }
        return false;
    }

    private void harvest(ServerLevel level, BlockPos p, ItemStack tool, boolean replantCrop) {
        BlockState state = level.getBlockState(p);
        List<ItemStack> drops = Block.getDrops(state, level, p, level.getBlockEntity(p), m, tool);
        level.destroyBlock(p, false, m);
        m.swing(InteractionHand.MAIN_HAND);
        if (replantCrop && state.getBlock() instanceof CropBlock crop) {
            for (ItemStack d : drops) {
                if (d.getItem() instanceof BlockItem bi && bi.getBlock() == crop) {
                    d.shrink(1);
                    level.setBlock(p, crop.getStateForAge(0), Block.UPDATE_ALL);
                    break;
                }
            }
        }
        for (ItemStack d : drops) m.store(d);
    }

    // ------------------------------------------------------------------ farming

    private void farm(ServerLevel level) {
        if (busy > 0) { busy--; return; }
        if (target == null || stuck > 200) {
            stuck = 0;
            if (--scan > 0) return;
            scan = 40;
            target = findFarmWork(level);
            if (target == null) {
                idleNear(m.workPos);
                return;
            }
        }
        BlockState s = level.getBlockState(target);
        boolean ripe = s.getBlock() instanceof CropBlock c && c.isMaxAge(s);
        boolean bare = s.isAir() && level.getBlockState(target.below()).getBlock() instanceof FarmBlock;
        if (!ripe && !bare) { target = null; return; }
        if (!reach(target, REACH)) return;
        if (ripe) harvest(level, target, ItemStack.EMPTY, true);
        else {
            ItemStack seed = m.takeFromBag(st -> st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock);
            if (!seed.isEmpty() && seed.getItem() instanceof BlockItem bi) {
                level.setBlock(target, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
                m.swing(InteractionHand.MAIN_HAND);
                level.playSound(null, target, SoundEvents.CROP_PLANTED, SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }
        target = null;
        busy = 10;
    }

    private BlockPos findFarmWork(ServerLevel level) {
        BlockPos c = m.workPos != null ? m.workPos : m.blockPosition();
        boolean seeds = m.bagHas(st -> st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-12, -3, -12), c.offset(12, 3, 12))) {
            BlockState s = level.getBlockState(p);
            boolean ripe = s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s);
            boolean bare = seeds && s.isAir() && level.getBlockState(p.below()).getBlock() instanceof FarmBlock;
            if (!ripe && !bare) continue;
            double d = p.distSqr(m.blockPosition());
            if (d < bestD) { bestD = d; best = p.immutable(); }
        }
        return best;
    }

    // ------------------------------------------------------------------ woodcutting

    private void wood(ServerLevel level) {
        if (busy > 0) { busy--; return; }
        if (tree.isEmpty()) {
            if (treeBase != null) replant(level, treeBase);
            treeBase = null;
            if (--scan > 0) return;
            scan = 40;
            BlockPos base = findTree(level);
            if (base == null) { idleNear(m.workPos); return; }
            treeBase = base;
            collectTree(level, base);
            stuck = 0;
        }
        if (stuck > 300) { tree.clear(); treeBase = null; return; }
        if (!reach(treeBase, 3.0)) return;
        BlockPos log = tree.get(0);
        if (!level.getBlockState(log).is(BlockTags.LOGS)) { tree.remove(0); return; }
        m.getLookControl().setLookAt(Vec3.atCenterOf(log));
        harvest(level, log, AXE, false);
        tree.remove(0);
        busy = 12;
    }

    private BlockPos findTree(ServerLevel level) {
        BlockPos c = m.workPos != null ? m.workPos : m.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-14, -3, -14), c.offset(14, 6, 14))) {
            if (!level.getBlockState(p).is(BlockTags.LOGS)) continue;
            BlockState below = level.getBlockState(p.below());
            if (!below.is(BlockTags.DIRT)) continue;                      // a trunk standing on the ground, not a house beam
            if (!level.getBlockState(p.above()).is(BlockTags.LOGS)) continue;
            double d = p.distSqr(m.blockPosition());
            if (d < bestD) { bestD = d; best = p.immutable(); }
        }
        return best;
    }

    /** Every log joined to the trunk (diagonals too), lowest first, up to a big tree's worth. */
    private void collectTree(ServerLevel level, BlockPos base) {
        tree.clear();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(base);
        seen.add(base);
        while (!open.isEmpty() && tree.size() < 96) {
            BlockPos p = open.poll();
            tree.add(p);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 0; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos q = p.offset(dx, dy, dz);
                        if (Math.abs(q.getX() - base.getX()) > 6 || Math.abs(q.getZ() - base.getZ()) > 6) continue;
                        if (seen.add(q) && level.getBlockState(q).is(BlockTags.LOGS)) open.add(q);
                    }
        }
        tree.sort(Comparator.comparingInt(BlockPos::getY));
    }

    private void replant(ServerLevel level, BlockPos base) {
        if (!level.getBlockState(base).isAir() || !level.getBlockState(base.below()).is(BlockTags.DIRT)) return;
        ItemStack sapling = m.takeFromBag(st -> st.is(ItemTags.SAPLINGS));
        if (sapling.getItem() instanceof BlockItem bi) {
            level.setBlock(base, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
            m.swing(InteractionHand.MAIN_HAND);
        }
    }

    // ------------------------------------------------------------------ mining

    /** How far the staircase goes down before it levels out into a tunnel, and how long the tunnel is. */
    private static final int STAIRS = 40, TUNNEL = 32, FLOOR_Y = 12;

    private void mine(ServerLevel level) {
        if (busy > 0) { busy--; return; }
        if (m.workPos == null) { m.giveUp("I don't know where to dig."); return; }
        if (m.bagFull()) { m.giveUp("My bag's full! Come and empty it, then hire me again."); return; }
        int step = m.mineStep + 1;
        int down = Math.min(step, stairsDown());
        if (step > stairsDown() + TUNNEL) { m.giveUp("I've finished the tunnel! Come and see what I found."); return; }
        Direction d = m.workDir;
        BlockPos feet = m.workPos.relative(d, step).below(down);
        boolean stairs = step <= stairsDown();
        BlockPos[] clear = stairs ? new BlockPos[]{feet, feet.above(), feet.above(2)} : new BlockPos[]{feet, feet.above()};
        // stand on the last cleared step
        BlockPos stand = m.workPos.relative(d, step - 1).below(Math.min(step - 1, stairsDown()));
        if (!reach(stand, 1.6)) {
            if (stuck > 400) m.giveUp("I can't get back into the mine.");
            return;
        }
        for (BlockPos p : clear) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) continue;
            if (nearFluid(level, p)) { m.giveUp("There's water or lava ahead, so I stopped digging."); return; }
            float hardness = s.getDestroySpeed(level, p);
            if (hardness < 0 || hardness > 30) { m.giveUp("I hit something too hard to dig through."); return; }
            m.getLookControl().setLookAt(Vec3.atCenterOf(p));
            harvest(level, p, PICK, false);
            veins(level, p, 0);
            busy = 8 + (int) (hardness * 6);
            return;
        }
        // the step is open: make sure there's a floor, light it now and then, move on
        BlockState floor = level.getBlockState(feet.below());
        if (!floor.isSolid() || !floor.getFluidState().isEmpty()) {
            if (nearFluid(level, feet.below())) { m.giveUp("There's water or lava ahead, so I stopped digging."); return; }
            BlockPos hole = feet.below();
            ItemStack block = m.takeFromBag(st -> st.getItem() instanceof BlockItem bi
                    && bi.getBlock().defaultBlockState().isCollisionShapeFullBlock(level, hole) && !(bi.getBlock() instanceof net.minecraft.world.level.block.EntityBlock));
            if (block.isEmpty()) { m.giveUp("There's a hole I can't fill. Put some cobblestone or dirt in my bag."); return; }
            level.setBlock(feet.below(), ((BlockItem) block.getItem()).getBlock().defaultBlockState(), Block.UPDATE_ALL);
        }
        if (step % 8 == 0 && level.getBlockState(feet).isAir() && level.getBlockState(feet.below()).isSolid()) {
            ItemStack torch = m.takeFromBag(st -> st.is(Items.TORCH));
            if (!torch.isEmpty()) level.setBlock(feet, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
        }
        m.mineStep = step;
    }

    private int stairsDown() {
        return Math.max(0, Math.min(STAIRS, m.workPos.getY() - FLOOR_Y));
    }

    private static boolean nearFluid(ServerLevel level, BlockPos p) {
        if (!level.getFluidState(p).isEmpty()) return true;
        for (Direction d : Direction.values()) if (!level.getFluidState(p.relative(d)).isEmpty()) return true;
        return false;
    }

    /** Ores touching the tunnel come out too. */
    private void veins(ServerLevel level, BlockPos p, int depth) {
        if (depth > 6) return;
        for (Direction d : Direction.values()) {
            BlockPos q = p.relative(d);
            BlockState s = level.getBlockState(q);
            if (s.is(Tags.Blocks.ORES) && !nearFluid(level, q)) {
                harvest(level, q, PICK, false);
                veins(level, q, depth + 1);
            }
        }
    }

    // ------------------------------------------------------------------ fishing

    private void fish(ServerLevel level) {
        if (water == null || !level.getFluidState(water).is(Fluids.WATER)) {
            m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
            if (--scan > 0) return;
            scan = 40;
            water = findWater(level);
            target = water == null ? null : shoreFor(level, water);
            if (water == null || target == null) { water = null; idleNear(m.workPos); return; }
            bite = 200 + m.getRandom().nextInt(400);
            stuck = 0;
        }
        if (stuck > 300) { water = null; return; }
        if (!reach(target, 1.2)) return;
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
        for (ItemStack s : table.getRandomItems(params)) m.store(s);
        if (m.bagFull()) m.giveUp("My bag's full of fish! Come and empty it.");
    }

    private BlockPos findWater(ServerLevel level) {
        BlockPos c = m.workPos != null ? m.workPos : m.blockPosition();
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
    private BlockPos shoreFor(ServerLevel level, BlockPos w) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos p = w.relative(d).above(dy);
                if (level.getBlockState(p.below()).isSolid() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) return p;
            }
        }
        return null;
    }

    private void idleNear(BlockPos p) {
        if (p != null && !MaidenGoals.near(m, p, 6) && m.getNavigation().isDone()) {
            m.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.5);
        }
    }
}

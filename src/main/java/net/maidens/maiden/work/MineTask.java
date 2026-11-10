package net.maidens.maiden.work;

import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Digs a staircase down from where she was hired, in the direction her employer faced (3 high), to about y 16,
 * then a 2-high tunnel up to 64 long. She lights it with a torch every 6 steps (from her bag, made from coal in her
 * bag, or her own), and digs out any ore she spots within 4 blocks, whole veins at a time. Holes in the floor are
 * filled from her bag. When her bag is full, or the job ends (shift over, water or lava ahead, the tunnel done),
 * she climbs back up her staircase; at the top she empties her bag into a marked chest and goes back down if the
 * shift isn't over.
 */
final class MineTask extends WorkTask {
    private static final ItemStack PICK = new ItemStack(Items.IRON_PICKAXE);
    private static final int STAIRS = 140, TUNNEL = 64, FLOOR_Y = 16, TORCH_EVERY = 6, ORE_RANGE = 6, WAYPOINT = 12;

    /** Blocks to break to get at an ore she spotted, the ore last. */
    private final Deque<BlockPos> dig = new ArrayDeque<>();
    private final Set<BlockPos> skipOre = new HashSet<>();

    MineTask(Maiden m) {
        super(m);
    }

    @Override
    public void reset() {
        super.reset();
        dig.clear();
        skipOre.clear();
    }

    @Override
    public void tick(ServerLevel level) {
        if (busy > 0) { busy--; return; }
        Employment e = job();
        if (e.workPos == null) { e.giveUp("I don't know where to dig."); return; }
        if (e.wrappingUp || e.bagFull() || e.forVillage && !e.busyNow()) { climbOut(e); return; }
        int step = e.mineStep + 1;
        int stairs = stairsDown();
        if (step > stairs + TUNNEL) { e.giveUp("I've finished the tunnel! Come and see what I found."); return; }
        // back to the work face, a stretch of staircase at a time
        BlockPos stand = feet(step - 1);
        if (m.distanceToSqr(Vec3.atBottomCenterOf(stand)) > 6 * 6) {
            int here = nearestStep();
            reach(feet(Math.min(e.mineStep, here + WAYPOINT)), 1.5);
            if (stuck > 400) e.giveUp("I can't get back into the mine.");
            return;
        }
        if (!reach(stand, 1.6)) {
            if (stuck > 400) e.giveUp("I can't get back into the mine.");
            return;
        }
        // an ore she spotted: dig towards it, then the whole vein
        if (!dig.isEmpty()) {
            BlockPos p = dig.peek();
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) { dig.poll(); return; }
            if (nearFluid(level, p) || s.getDestroySpeed(level, p) < 0) { skipOre.add(dig.peekLast()); dig.clear(); return; }
            breakBlock(level, p, s);
            dig.poll();
            return;
        }
        Direction d = e.workDir;
        BlockPos feet = feet(step);
        BlockPos[] clear = step <= stairs ? new BlockPos[]{feet, feet.above(), feet.above(2)} : new BlockPos[]{feet, feet.above()};
        for (BlockPos p : clear) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) continue;
            if (nearFluid(level, p)) { e.giveUp("There's water or lava ahead, so I stopped digging."); return; }
            float hardness = s.getDestroySpeed(level, p);
            if (hardness < 0 || hardness > 30) { e.giveUp("I hit something too hard to dig through."); return; }
            breakBlock(level, p, s);
            return;
        }
        // the step is open: make sure there's a floor, light it now and then, move on
        BlockPos hole = feet.below();
        BlockState floor = level.getBlockState(hole);
        if (!floor.isSolid() || !floor.getFluidState().isEmpty()) {
            if (nearFluid(level, hole)) { e.giveUp("There's water or lava ahead, so I stopped digging."); return; }
            ItemStack block = e.takeFromBag(st -> st.getItem() instanceof BlockItem bi
                    && bi.getBlock().defaultBlockState().isCollisionShapeFullBlock(level, hole) && !(bi.getBlock() instanceof EntityBlock));
            BlockState fill = block.isEmpty() ? Blocks.COBBLESTONE.defaultBlockState() : ((BlockItem) block.getItem()).getBlock().defaultBlockState();
            level.setBlock(hole, fill, Block.UPDATE_ALL);
        }
        if (step % TORCH_EVERY == 0) placeTorch(level, feet, d);
        e.mineStep = step;
        lookForOre(level, feet);
    }

    private void breakBlock(ServerLevel level, BlockPos p, BlockState s) {
        m.getLookControl().setLookAt(Vec3.atCenterOf(p));
        harvest(level, p, PICK, false);
        veins(level, p, 0);
        busy = 8 + (int) (s.getDestroySpeed(level, p) * 6);
    }

    /** Climbs back up her staircase, a stretch at a time; at the top, the job ends or her bag goes to the chest. */
    private void climbOut(Employment e) {
        int here = nearestStep();
        if (m.distanceToSqr(Vec3.atBottomCenterOf(e.workPos)) <= 2.5 * 2.5) {      // out at the top
            m.getNavigation().stop();
            if (e.wrappingUp) e.finish(e.wrapUpMessage);
            else if (e.forVillage && !e.busyNow()) { if (e.hasDeliverables()) Delivery.start(e, false); }    // done for the day
            else e.bagFullDuringWork();
            return;
        }
        if (reach(feet(Math.max(0, here - WAYPOINT)), 1.5) && here <= 1) arriveNear(e);
        if (stuck >= GIVE_UP && !e.inMine()) {
            // not down the mine after all (or can't find the way up): out is wherever she is
            stuck = 0;
            if (e.wrappingUp) e.finish(e.wrapUpMessage);
            else if (!e.forVillage || e.busyNow()) e.bagFullDuringWork();
        }
    }

    /** At the top of the stairs but the pathfinder won't take the last step: she steps up. */
    private void arriveNear(Employment e) {
        m.arriveNear(e.workPos);
    }

    /** Where she stands on step i of the mine (0 is where she was hired). */
    private BlockPos feet(int i) {
        Employment e = job();
        return e.workPos.relative(e.workDir, i).below(Math.min(i, stairsDown()));
    }

    /** The step of the mine she's closest to. */
    private int nearestStep() {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i <= job().mineStep; i++) {
            double d = m.distanceToSqr(Vec3.atBottomCenterOf(feet(i)));
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private int stairsDown() {
        return Math.max(0, Math.min(STAIRS, job().workPos.getY() - floorY(job().mineTarget)));
    }

    /** How deep to tunnel: where what she's looking for is most common. */
    static int floorY(@org.jetbrains.annotations.Nullable Block target) {
        if (target == null) return FLOOR_Y;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(target).getPath();
        if (id.contains("diamond") || id.contains("redstone")) return -54;
        if (id.contains("deepslate") || id.contains("tuff")) return -20;
        if (id.contains("gold") || id.contains("lapis")) return -16;
        if (id.contains("coal") || id.contains("copper")) return 44;
        if (id.contains("emerald")) return 16;
        return FLOOR_Y;
    }

    /** The block a held item stands for, if it's an ore or a kind of stone; else null (any ore). */
    @org.jetbrains.annotations.Nullable
    static Block targetFrom(ItemStack held) {
        if (!(held.getItem() instanceof BlockItem bi)) return null;
        BlockState s = bi.getBlock().defaultBlockState();
        boolean ok = s.is(Tags.Blocks.ORES) || s.is(Tags.Blocks.STONES) || s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || s.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER) || s.is(Tags.Blocks.COBBLESTONES) || s.is(Tags.Blocks.SANDSTONE_BLOCKS);
        return ok ? bi.getBlock() : null;
    }

    /** What she digs out when she spots it: her target (and any ore) or, with none, any ore. */
    private boolean wanted(BlockState s) {
        Block t = job().mineTarget;
        return t != null && s.is(t) || s.is(Tags.Blocks.ORES);
    }

    /** A torch on the wall at head height (or on the floor if there's no wall). */
    private void placeTorch(ServerLevel level, BlockPos feet, Direction d) {
        BlockPos head = feet.above();
        Direction side = d.getClockWise();
        boolean wall = level.getBlockState(head).isAir() && level.getBlockState(head.relative(side)).isFaceSturdy(level, head.relative(side), side.getOpposite());
        boolean ground = level.getBlockState(feet).isAir() && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
        if (!wall && !ground) return;
        takeTorch();
        if (wall) level.setBlock(head, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side.getOpposite()), Block.UPDATE_ALL);
        else level.setBlock(feet, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, head, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    /** A torch from her bag, or four made from a piece of coal in her bag; otherwise one of her own. */
    private void takeTorch() {
        Employment e = job();
        if (!e.takeFromBag(st -> st.is(Items.TORCH)).isEmpty()) return;
        if (!e.takeFromBag(st -> st.is(Items.COAL) || st.is(Items.CHARCOAL)).isEmpty()) e.store(new ItemStack(Items.TORCH, 3));
    }

    /** Spots the nearest ore within reach of the tunnel and plans a way to it. */
    private void lookForOre(ServerLevel level, BlockPos feet) {
        BlockPos eye = feet.above();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(eye.offset(-ORE_RANGE, -ORE_RANGE, -ORE_RANGE), eye.offset(ORE_RANGE, ORE_RANGE, ORE_RANGE))) {
            BlockState st = level.getBlockState(p);
            if (!wanted(st) || skipOre.contains(p)) continue;
            double dist = p.distSqr(eye);
            // her target comes first: it counts as half as far
            if (job().mineTarget != null && st.is(job().mineTarget)) dist *= 0.5;
            if (dist < bestD && dist <= ORE_RANGE * ORE_RANGE + 1) {
                bestD = dist;
                best = p.immutable();
            }
        }
        if (best == null) return;
        // a straight line from her head to the ore
        dig.clear();
        Vec3 from = Vec3.atCenterOf(eye), to = Vec3.atCenterOf(best);
        int n = (int) Math.ceil(from.distanceTo(to) * 2);
        for (int i = 1; i <= n; i++) {
            BlockPos p = BlockPos.containing(from.lerp(to, (double) i / n));
            if (!dig.contains(p) && !p.equals(eye) && !p.equals(feet)) dig.add(p);
        }
        for (BlockPos p : dig) {
            if (nearFluid(level, p)) {
                skipOre.add(best);
                dig.clear();
                return;
            }
        }
    }

    private static boolean nearFluid(ServerLevel level, BlockPos p) {
        if (!level.getFluidState(p).isEmpty()) return true;
        for (Direction d : Direction.values()) if (!level.getFluidState(p.relative(d)).isEmpty()) return true;
        return false;
    }

    /** Ores touching what she just broke come out too. */
    private void veins(ServerLevel level, BlockPos p, int depth) {
        if (depth > 8) return;
        for (Direction d : Direction.values()) {
            BlockPos q = p.relative(d);
            BlockState st = level.getBlockState(q);
            if (wanted(st) && !nearFluid(level, q)) {
                harvest(level, q, PICK, false);
                // a stone target spreads through everything: only a few blocks of it at a time
                veins(level, q, depth + (st.is(Tags.Blocks.ORES) ? 1 : 3));
            }
        }
    }

    @Override
    public String debug() {
        return "step " + job().mineStep + (job().wrappingUp ? " climbing out" : "") + " ore path " + dig.size() + " " + super.debug();
    }
}

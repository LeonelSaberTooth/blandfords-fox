package net.maidens.village;

import net.maidens.MaidensMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The royal castle, made in code (one stone design for every village style, until hand-built ones replace it):
 * a 25 x 25 courtyard behind a curtain wall, a tower at each corner (ladder up to the wall walk and the top), a
 * gatehouse in the middle of the front, and a keep in the middle: throne room, the royal bedroom above, a top
 * floor, and a walkable roof.
 * <p>
 * Laid out in its own frame (corner at 0,0,0, ground floor at y = 0, gate in the south wall), then turned and
 * moved into place like a vanilla building.
 */
public final class Castle {
    public static final String NAME = "castle";
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, NAME);
    static final int SIZE = 25, HEIGHT = 18;
    private static final int TRIES = 300, MAX_SLOPE = 6;

    private Castle() {}

    // ------------------------------------------------------------------ the design

    /** Collects the castle's blocks in its own frame; anything inside the footprint not set is kept clear. */
    private static final class Draft {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

        void set(int x, int y, int z, BlockState s) {
            blocks.put(new BlockPos(x, y, z), s);
        }

        void air(int x, int y, int z) {
            blocks.remove(new BlockPos(x, y, z));
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++) set(x, y, z, s);
        }

        void stone(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++) set(x, y, z, Castle.stone(x, y, z));
        }

        void hollow(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++) air(x, y, z);
        }
    }

    /** Mostly stone bricks, some mossy, cracked or cobbled, fixed by the spot (so it looks the same every time). */
    static BlockState stone(int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        h = Math.floorMod(h ^ (h >>> 13), 100);
        if (h < 66) return Blocks.STONE_BRICKS.defaultBlockState();
        if (h < 80) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        if (h < 92) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    private static Map<BlockPos, BlockState> design;

    /** The castle's blocks in its own frame (made once). */
    static synchronized Map<BlockPos, BlockState> design() {
        if (design != null) return design;
        Draft d = new Draft();
        int last = SIZE - 1;
        // the courtyard floor
        for (int x = 0; x <= last; x++)
            for (int z = 0; z <= last; z++) d.set(x, 0, z, (x * 7 + z * 3) % 5 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
        // the curtain wall: two thick, 7 high, battlements on the outer edge
        for (int x = 0; x <= last; x++)
            for (int z = 0; z <= last; z++) {
                boolean ring = x <= 1 || x >= last - 1 || z <= 1 || z >= last - 1;
                if (!ring) continue;
                d.stone(x, 1, z, x, 7, z);
                boolean outer = x == 0 || x == last || z == 0 || z == last;
                if (outer && (x + z) % 2 == 0) d.set(x, 8, z, Blocks.STONE_BRICKS.defaultBlockState());
            }
        // the corner towers
        for (int tx : new int[]{0, last - 6})
            for (int tz : new int[]{0, last - 6}) tower(d, tx, tz);
        // the gatehouse, in the middle of the south wall
        d.stone(9, 1, last - 3, 15, 10, last);
        for (int x = 9; x <= 15; x++) {
            if ((x % 2) == 1) d.set(x, 11, last, Blocks.STONE_BRICKS.defaultBlockState());
            if ((x % 2) == 1) d.set(x, 11, last - 3, Blocks.STONE_BRICKS.defaultBlockState());
        }
        d.hollow(11, 1, last - 3, 13, 4, last);
        d.set(12, 4, last - 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        for (int x = 11; x <= 13; x++) {
            d.set(x, 4, last, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.TOP));
        }
        keep(d);
        // lanterns on posts around the courtyard
        for (int[] p : new int[][]{{4, 9}, {20, 9}, {4, 15}, {20, 15}}) {
            d.set(p[0], 1, p[1], Blocks.SPRUCE_FENCE.defaultBlockState());
            d.set(p[0], 2, p[1], Blocks.LANTERN.defaultBlockState());
        }
        design = d.blocks;
        return design;
    }

    /** A 7 x 7 corner tower: 12 high, battlements, a floor halfway (level with the wall walk), a ladder to the top. */
    private static void tower(Draft d, int tx, int tz) {
        int x1 = tx + 6, z1 = tz + 6;
        d.stone(tx, 1, tz, x1, 12, z1);
        d.hollow(tx + 1, 1, tz + 1, x1 - 1, 11, z1 - 1);
        for (int x = tx + 1; x < x1; x++)
            for (int z = tz + 1; z < z1; z++) d.set(x, 7, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int x = tx; x <= x1; x++)
            for (int z = tz; z <= z1; z++) {
                boolean edge = x == tx || x == x1 || z == tz || z == z1;
                if (edge && (x + z) % 2 == 0) d.set(x, 13, z, Blocks.STONE_BRICKS.defaultBlockState());
            }
        boolean west = tx == 0, north = tz == 0;
        int last = SIZE - 1;
        // the ladder in the outer corner, up through the floor to a hatch in the roof
        int lx = west ? 1 : last - 1, lz = north ? 1 : last - 1;
        Direction facing = north ? Direction.SOUTH : Direction.NORTH;
        for (int y = 1; y <= 11; y++) d.set(lx, y, lz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
        d.set(lx, 12, lz, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.HALF, Half.TOP));
        // a way in from the courtyard, and out onto the wall walk on both sides
        int cx = west ? tx + 6 : tx, cz = north ? tz + 4 : tz + 2;
        d.air(cx, 1, cz);
        d.air(cx, 2, cz);
        int wx = west ? tx + 6 : tx, wz = north ? 1 : last - 1;
        d.air(wx, 8, wz);
        d.air(wx, 9, wz);
        int vx = west ? 1 : last - 1, vz = north ? tz + 6 : tz;
        d.air(vx, 8, vz);
        d.air(vx, 9, vz);
        // windows looking out
        int ox = west ? tx : x1, oz = north ? tz : z1;
        for (int y : new int[]{4, 10}) {
            d.set(ox, y, tz + 3, Blocks.GLASS_PANE.defaultBlockState());
            d.set(tx + 3, y, oz, Blocks.GLASS_PANE.defaultBlockState());
        }
    }

    /** The keep: 9 x 9, three floors and a roof, door facing the gate. */
    private static void keep(Draft d) {
        int x0 = 8, x1 = 16, z0 = 6, z1 = 14;
        d.stone(x0, 1, z0, x1, 16, z1);
        d.hollow(x0 + 1, 1, z0 + 1, x1 - 1, 15, z1 - 1);
        for (int x = x0 + 1; x < x1; x++)
            for (int z = z0 + 1; z < z1; z++) {
                d.set(x, 6, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                d.set(x, 11, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge && (x + z) % 2 == 0) d.set(x, 17, z, Blocks.STONE_BRICKS.defaultBlockState());
            }
        // the door
        d.set(12, 1, z1, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        d.set(12, 2, z1, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        d.set(12, 3, z1 + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        d.set(12, 4, z1 + 1, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        // the throne room: a red carpet from the door to the throne, banners and lanterns either side
        for (int z = z0 + 2; z < z1; z++) d.set(12, 1, z, Blocks.RED_CARPET.defaultBlockState());
        d.set(12, 1, z0 + 1, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        d.set(11, 1, z0 + 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
        d.set(13, 1, z0 + 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
        d.set(11, 2, z0 + 1, Blocks.LANTERN.defaultBlockState());
        d.set(13, 2, z0 + 1, Blocks.LANTERN.defaultBlockState());
        for (int x : new int[]{10, 14}) d.set(x, 3, z0 + 1, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
        for (int z : new int[]{9, 12}) {
            d.set(x0 + 1, 3, z, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.EAST));
            d.set(x1 - 1, 3, z, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.WEST));
        }
        // the royal bedroom
        for (int x : new int[]{10, 11}) {
            d.set(x, 7, z0 + 1, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
            d.set(x, 7, z0 + 2, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        }
        d.set(14, 7, z0 + 1, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        for (int z = z0 + 4; z <= z1 - 2; z++) d.set(x0 + 1, 7, z, Blocks.BOOKSHELF.defaultBlockState());
        for (int x = 11; x <= 13; x++)
            for (int z = z0 + 4; z <= z0 + 6; z++) d.set(x, 7, z, Blocks.RED_CARPET.defaultBlockState());
        d.set(13, 7, z1 - 1, Blocks.LANTERN.defaultBlockState());
        d.set(12, 12, 10, Blocks.LANTERN.defaultBlockState());
        // the ladder in the back corner, through both floors and up to a hatch in the roof
        for (int y = 1; y <= 15; y++) d.set(x1 - 1, y, z1 - 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        d.set(x1 - 1, 16, z1 - 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.WEST).setValue(TrapDoorBlock.HALF, Half.TOP));
        // windows
        for (int y : new int[]{3, 8, 13}) {
            for (int a : new int[]{10, 14}) {
                if (y != 3) d.set(a, y, z0, Blocks.GLASS_PANE.defaultBlockState());
                d.set(a, y, z1, Blocks.GLASS_PANE.defaultBlockState());
            }
            for (int a : new int[]{8, 11}) {
                d.set(x0, y, a, Blocks.GLASS_PANE.defaultBlockState());
                d.set(x1, y, a, Blocks.GLASS_PANE.defaultBlockState());
            }
        }
    }

    // ------------------------------------------------------------------ placing it

    /** How the footprint lies around the corner for a rotation (as a vanilla template turns). */
    static BoundingBox footprint(BlockPos corner, Rotation rot) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rot);
        BlockPos a = StructureTemplate.calculateRelativePosition(settings, BlockPos.ZERO).offset(corner);
        BlockPos b = StructureTemplate.calculateRelativePosition(settings, new BlockPos(SIZE - 1, HEIGHT - 1, SIZE - 1)).offset(corner);
        return BoundingBox.fromCorners(a, b);
    }

    /** The castle placed with its corner (ground floor) at a spot and turned, ready to add to a village. */
    static Buildings.Plan plan(BlockPos corner, Rotation rot) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rot);
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Set<BlockPos> clear = new HashSet<>();
        int beds = 0;
        for (Map.Entry<BlockPos, BlockState> e : design().entrySet()) {
            BlockState s = e.getValue().rotate(rot);
            blocks.put(StructureTemplate.calculateRelativePosition(settings, e.getKey()).offset(corner), s);
            if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) beds++;
        }
        for (int x = 0; x < SIZE; x++)
            for (int z = 0; z < SIZE; z++)
                for (int y = 1; y < HEIGHT; y++) {
                    BlockPos w = StructureTemplate.calculateRelativePosition(settings, new BlockPos(x, y, z)).offset(corner);
                    if (!blocks.containsKey(w)) clear.add(w);
                }
        BlockPos door = StructureTemplate.calculateRelativePosition(settings, new BlockPos(12, 0, SIZE)).offset(corner);
        List<BlockPos> all = new ArrayList<>(blocks.keySet());
        BoundingBox box = BoundingBox.encapsulatingPositions(all).orElse(footprint(corner, rot));
        return new Buildings.Plan(ID, box, blocks, clear, beds, door);
    }

    /** The turn that puts the gate (south in its own frame) towards a direction. */
    static Rotation facing(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
        return dz > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
    }

    /** The corner that centres the castle on a spot for a rotation. */
    static BlockPos cornerFor(int cx, int y, int cz, Rotation rot) {
        BoundingBox fp = footprint(BlockPos.ZERO, rot);
        return new BlockPos(cx - (fp.minX() + fp.maxX()) / 2, y, cz - (fp.minZ() + fp.maxZ()) / 2);
    }

    /**
     * The best spot for a castle just outside the village (or up a nearby hill), facing the village: flat enough,
     * dry, loaded, clear of its buildings. Null if there's none.
     */
    @Nullable
    static BlockPos[] findSite(ServerLevel level, VillageRecord v) {
        var r = net.minecraft.util.RandomSource.create(v.id ^ 0xCA57L);
        BlockPos c = v.centre();
        int centreY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        double half = Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2.0;
        BlockPos best = null;
        Rotation bestRot = Rotation.NONE;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < TRIES; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double dist = half * 0.6 + 14 + r.nextDouble() * (half * 0.5 + 24);
            int x = c.getX() + (int) (Math.cos(angle) * dist), z = c.getZ() + (int) (Math.sin(angle) * dist);
            Rotation rot = facing(c.getX() - x, c.getZ() - z);
            BlockPos corner0 = cornerFor(x, 0, z, rot);
            BoundingBox fp = footprint(corner0, rot);
            if (!level.hasChunksAt(fp.minX() - 2, fp.minZ() - 2, fp.maxX() + 2, fp.maxZ() + 2)) continue;
            BoundingBox flat = new BoundingBox(fp.minX(), -64, fp.minZ(), fp.maxX(), 320, fp.maxZ());
            if (v.blueprint.overlaps(flat.inflatedBy(4))) continue;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            long sum = 0;
            int n = 0;
            boolean wet = false;
            for (int px = fp.minX(); px <= fp.maxX() && !wet; px += 2)
                for (int pz = fp.minZ(); pz <= fp.maxZ(); pz += 2) {
                    int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz) - 1;
                    if (!level.getFluidState(new BlockPos(px, h, pz)).isEmpty()) {
                        wet = true;
                        break;
                    }
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                    sum += h;
                    n++;
                }
            if (wet || n == 0 || max - min > MAX_SLOPE) continue;
            int base = (int) Math.round((double) sum / n);
            // close to the village, on flat ground, and a little higher up is nicer
            double score = dist + (max - min) * 4 - Math.min(12, base - centreY) * 2.5;
            if (score < bestScore) {
                bestScore = score;
                best = new BlockPos(x, base, z);
                bestRot = rot;
            }
        }
        if (best == null) return null;
        return new BlockPos[]{cornerFor(best.getX(), best.getY(), best.getZ(), bestRot), new BlockPos(bestRot.ordinal(), 0, 0)};
    }
}

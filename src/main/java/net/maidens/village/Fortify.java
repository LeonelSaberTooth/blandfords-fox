package net.maidens.village;

import net.maidens.core.MaidensConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Castles and walls. Some maiden villages are fortified by nature (fixed by the seed, {@code fortifiedVillageChance}):
 * the first time the whole village is loaded, a castle goes up just outside it with a few houses beside it, then
 * walls around everything, all put in place at once. Any other village can have them built by its maidens,
 * chosen at the bell (the Castle and Walls cards).
 */
public final class Fortify {
    private static final long SALT = 0x466F727473L;
    private static final int PLACE_PER_TICK = 8192;

    private Fortify() {}

    /** Whether a village is fortified by nature (from its start chunk alone, so it's known before it's visited). */
    public static boolean rolls(ServerLevel level, long villageId) {
        long seed = level.getSeed() ^ villageId * 0xC2B2AE3D27D4EB4FL ^ SALT;
        return RandomSource.create(seed).nextDouble() < MaidensConfig.FORTIFIED_VILLAGE_CHANCE.get();
    }

    /**
     * Every 5 s: a fortified-by-nature village gets its castle site and walls planned as soon as it's known (from
     * the lie of the land, loaded or not: the walls go up stretch by stretch as their land loads); the castle and the
     * houses beside it go up once the land at the site is loaded; gates get their paths once theirs is.
     */
    static void tick(ServerLevel level, Villages villages) {
        for (VillageRecord v : villages.all()) {
            if (!v.fortified && rolls(level, v.id) && v.blueprint.pieceCount() > 0) fortify(level, v, true);
            if (v.castleCorner != null && v.castle == null) {
                Rotation rot = Rotation.values()[v.castleTurn];
                BoundingBox fp = Castle.footprint(v.castleCorner, rot);
                if (level.hasChunksAt(fp.minX() - 16, fp.minZ() - 16, fp.maxX() + 16, fp.maxZ() + 16)) {
                    castleAt(level, v, v.castleCorner, rot, true);
                    housesBy(level, v, true);
                }
            }
            if (!v.gates.isEmpty()) Walls.joinGates(level, v);
            if (!v.bannerUp) bannerByBell(level, v);
        }
    }

    /** Every tick: puts what's waiting to go up all at once in place, a little at a time. */
    static void placeTick(ServerLevel level, Villages villages) {
        for (VillageRecord v : villages.all()) {
            if (!v.blueprint.hasInstant()) continue;
            v.blueprint.placeInstant(level, PLACE_PER_TICK, p -> Heraldry.decorate(level, p, v.id));
            if (!v.blueprint.hasInstant()) villages.setDirty();
        }
    }

    /** The village's banner on a pole by its bell, once the bell is known and its land loaded. */
    private static void bannerByBell(ServerLevel level, VillageRecord v) {
        BlockPos bell = v.bell();
        if (bell == null || !level.hasChunksAt(bell.getX() - 6, bell.getZ() - 6, bell.getX() + 6, bell.getZ() + 6)) return;
        v.bannerUp = true;
        for (BlockPos p : BlockPos.withinManhattan(bell, 4, 2, 4)) {
            if (p.distManhattan(bell) < 3) continue;
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !level.getBlockState(p.above(2)).isAir()) continue;
            if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) continue;
            if (v.blueprint.expected(p) != null || v.blueprint.isBuildingSite(p) || v.blueprint.expected(p.below()) != null && v.blueprint.expected(p.below()).is(Upkeep.ROAD)) continue;
            int facing = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(-(bell.getX() - p.getX()), bell.getZ() - p.getZ())) / 22.5F), 16);
            BlockPos pos = p.immutable();
            java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks = java.util.Map.of(pos,
                    Heraldry.recolour(net.minecraft.world.level.block.Blocks.WHITE_BANNER.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.BannerBlock.ROTATION, (facing + 8) % 16), v.id));
            v.blueprint.addBuilding(new BoundingBox(pos), blocks, java.util.Set.of(), "banner", true);
            break;
        }
        Villages.get(level).setDirty();
    }

    /** What fortifying did, for the self test. */
    public record Report(@Nullable BoundingBox castle, int houses, @Nullable Walls.Result walls) {}

    /** A castle (with houses beside it) and walls around the village. Instant: put in place at once, else built. */
    public static Report fortify(ServerLevel level, VillageRecord v, boolean instant) {
        v.fortified = true;
        BoundingBox reserve = null;
        if (v.castle == null && v.castleCorner == null) {
            BlockPos[] site = Castle.findSite(level, v);
            if (site != null) {
                v.castleCorner = site[0];
                v.castleTurn = site[1].getX();
            }
        }
        if (v.castle == null && v.castleCorner != null) {
            // room for the castle and the houses beside it, inside the walls
            reserve = Castle.footprint(v.castleCorner, Rotation.values()[v.castleTurn]).inflatedBy(14);
        }
        Walls.Result walls = v.wallBox == null ? Walls.plan(level, v, instant, reserve, v.castle) : null;
        Villages.get(level).setDirty();
        return new Report(v.castleCorner == null ? null : Castle.footprint(v.castleCorner, Rotation.values()[v.castleTurn]), 0, walls);
    }

    /** Adds the castle at a corner and turn, with a road from its gate. Returns false if it can't go there. */
    static boolean castleAt(ServerLevel level, VillageRecord v, BlockPos corner, Rotation rot, boolean instant) {
        Buildings.Plan plan = Buildings.withFoundation(level, Castle.plan(corner, rot, v), 16);
        v.castleCorner = corner;
        v.castleTurn = rot.ordinal();
        Buildings.Plan road = Buildings.road(level, v, plan);
        v.blueprint.addBuilding(plan.box(), plan.blocks(), plan.clear(), Castle.NAME, instant);
        if (road != null) v.blueprint.addBuilding(road.box(), road.blocks(), road.clear(), "road", instant);
        v.castle = plan.box();
        if (!instant) v.project = Castle.NAME;
        Villages.get(level).setDirty();
        return true;
    }

    /** Two or three of the village's houses next to the castle, so it's part of the village. */
    static int housesBy(ServerLevel level, VillageRecord v, boolean instant) {
        List<ResourceLocation> houses = new ArrayList<>();
        for (ResourceLocation t : Buildings.templates(level, v.style)) if (Buildings.beds(level, t) > 0) houses.add(t);
        if (houses.isEmpty() || v.castle == null) return 0;
        RandomSource r = RandomSource.create(v.id ^ 0x40C5EL);
        int count = 2 + r.nextInt(2), built = 0;
        for (int i = 0; i < count; i++) {
            if (Buildings.build(level, v, houses.get(r.nextInt(houses.size())), v.castle.getCenter(), instant) != null) built++;
        }
        return built;
    }

    /** A citizen placing the castle from the bell screen. Returns why not, or null if it's going up. */
    @Nullable
    static String castleFromBell(ServerLevel level, VillageRecord v, BlockPos corner, Rotation rot) {
        if (v.castle != null) return v.name + " already has a castle.";
        if (!level.hasChunkAt(corner)) return "That land isn't loaded.";
        BoundingBox fp = Castle.footprint(corner, rot);
        if (!v.box.inflatedBy(60).intersects(fp)) return "That's too far from " + v.name + ".";
        if (!level.hasChunksAt(fp.minX() - 2, fp.minZ() - 2, fp.maxX() + 2, fp.maxZ() + 2)) return "That land isn't loaded.";
        BoundingBox column = new BoundingBox(fp.minX(), -64, fp.minZ(), fp.maxX(), 320, fp.maxZ());
        if (v.blueprint.overlaps(column.inflatedBy(2))) return "Something's already there.";
        int base = Buildings.groundLevel(level, new BoundingBox(fp.minX(), 0, fp.minZ(), fp.maxX(), 0, fp.maxZ()));
        if (base == Integer.MIN_VALUE) {
            // the castle stands on a foundation, so a gentle slope is fine: use the average if it isn't wet
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            long sum = 0;
            int n = 0, wet = 0;
            for (int x = fp.minX(); x <= fp.maxX(); x += 2)
                for (int z = fp.minZ(); z <= fp.maxZ(); z += 2) {
                    int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (!level.getFluidState(new BlockPos(x, h, z)).isEmpty()) {
                        if (++wet > 40) return "The ground there is too wet.";
                        continue;
                    }
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                    sum += h;
                    n++;
                }
            if (max - min > 10) return "The ground there is too steep.";
            base = (int) Math.round((double) sum / n);
        }
        castleAt(level, v, new BlockPos(corner.getX(), base, corner.getZ()), rot, false);
        housesBy(level, v, false);
        return null;
    }

    /** A citizen ordering walls from the bell screen. Returns why not, or null if they're going up. */
    @Nullable
    static String wallsFromBell(ServerLevel level, VillageRecord v) {
        if (v.wallBox != null) return v.name + " already has walls.";
        Walls.Result r = Walls.plan(level, v, false, v.castle);
        if (r == null) return "There's nothing to put walls around yet.";
        v.project = Walls.NAME;
        return null;
    }
}

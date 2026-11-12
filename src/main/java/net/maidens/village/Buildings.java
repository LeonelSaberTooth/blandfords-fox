package net.maidens.village;

import com.mojang.datafixers.util.Either;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * New buildings for a maiden village: which vanilla buildings its style has (from the vanilla village's own
 * "houses" pool, so modded additions to that pool come along), finding a free flat spot near the village,
 * and planning the building and a road from its door to the nearest road. A plan is added to the village's
 * {@link Blueprint} as pieces under construction; the maidens then build it block by block through {@link Upkeep}.
 */
public final class Buildings {
    private static final Pattern TEMPLATE_NAME = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_/.-]+)");
    private static final Map<String, List<ResourceLocation>> BY_STYLE = new HashMap<>();
    private static final int TRIES = 400, MAX_SLOPE = 3, ROAD_SEARCH = 6000;

    /** How the last site search went, for the self test and /maiden village. */
    public static String lastSearch = "";

    private Buildings() {}

    /** A building ready to add to a village: blocks, spots to clear, beds, and where its door connects to a road. */
    public record Plan(ResourceLocation template, BoundingBox box, Map<BlockPos, BlockState> blocks, Set<BlockPos> clear,
                       int beds, @Nullable BlockPos door) {}

    // ------------------------------------------------------------------ the buildings of a style

    /** Every building template of a village style ("plains", "desert"...), as listed in its vanilla houses pool. */
    public static List<ResourceLocation> templates(ServerLevel level, String style) {
        return BY_STYLE.computeIfAbsent(style, s -> {
            List<ResourceLocation> out = new ArrayList<>();
            StructureTemplatePool pool = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
                    .get(ResourceLocation.withDefaultNamespace("village/" + s + "/houses"));
            if (pool == null) return out;
            for (StructurePoolElement e : pool.getShuffledTemplates(RandomSource.create(0))) {
                ResourceLocation rl = templateOf(e);
                if (rl != null && !out.contains(rl) && level.getStructureManager().get(rl).isPresent()) out.add(rl);
            }
            out.sort((a, b) -> a.getPath().compareTo(b.getPath()));
            return out;
        });
    }

    @Nullable
    private static ResourceLocation templateOf(StructurePoolElement e) {
        if (!(e instanceof SinglePoolElement single)) return null;
        try {
            Either<ResourceLocation, StructureTemplate> t = ObfuscationReflectionHelper.getPrivateValue(SinglePoolElement.class, single, "template");
            if (t != null && t.left().isPresent()) return t.left().get();
        } catch (RuntimeException ignored) {
            // fall back to its description, "Single[Left[minecraft:village/...]]"
        }
        Matcher m = TEMPLATE_NAME.matcher(e.toString());
        return m.find() ? ResourceLocation.tryParse(m.group(1)) : null;
    }

    /** The template a village piece was made from ("plains_small_house_1"), "road" for streets, or "". */
    static String pieceName(net.minecraft.world.level.levelgen.structure.StructurePiece piece) {
        if (!(piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pe)) return "";
        ResourceLocation t = templateOf(pe.getElement());
        if (t == null) return "";
        String path = t.getPath();
        if (path.contains("/streets/")) return "road";
        // houses, workplaces and the centre are buildings; lamps, end pieces, animals and villagers are not
        return path.contains("/houses/") || path.contains("/town_centers/") ? shortName(t) : NOT_A_BUILDING;
    }

    /** The name of recorded pieces that aren't buildings (decorations, entities): left off the village map. */
    public static final String NOT_A_BUILDING = "-";

    /** "a small house 1", "an animal pen 1". */
    public static String withArticle(String name) {
        return (name.isEmpty() || "aeiou".indexOf(Character.toLowerCase(name.charAt(0))) < 0 ? "a " : "an ") + name;
    }

    /** "plains_small_house_1" for minecraft:village/plains/houses/plains_small_house_1. */
    public static String shortName(ResourceLocation template) {
        String p = template.getPath();
        return p.substring(p.lastIndexOf('/') + 1);
    }

    /** "small house 1". */
    public static String displayName(ResourceLocation template, String style) {
        String n = shortName(template);
        if (n.startsWith(style + "_")) n = n.substring(style.length() + 1);
        return n.replace('_', ' ');
    }

    private static final Map<ResourceLocation, Integer> BEDS = new HashMap<>();

    /** A building's blocks at the origin, unrotated (for its picture). */
    public static Plan preview(ServerLevel level, ResourceLocation template) {
        return read(level, template, BlockPos.ZERO, Rotation.NONE);
    }

    /**
     * Plans a building a citizen placed by hand: its corner at x, z (on the ground there), turned by rot, with a road
     * to the village's roads. Returns why not, or null if it's going up.
     */
    @Nullable
    public static String buildAt(ServerLevel level, VillageRecord v, ResourceLocation template, BlockPos corner, Rotation rot) {
        if (!level.hasChunkAt(corner)) return "That land isn't loaded.";
        StructureTemplate t = level.getStructureManager().getOrCreate(template);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, corner.getX(), corner.getZ()) - 1;
        BoundingBox fp = t.getBoundingBox(new StructurePlaceSettings().setRotation(rot), new BlockPos(corner.getX(), y, corner.getZ()));
        if (!v.box.inflatedBy(40).intersects(fp)) return "That's too far from " + v.name + ".";
        if (!level.hasChunksAt(fp.minX() - 2, fp.minZ() - 2, fp.maxX() + 2, fp.maxZ() + 2)) return "That land isn't loaded.";
        if (v.blueprint.overlaps(fp.inflatedBy(2))) return "Something's already there.";
        // any ground will do: the maidens fell the trees in the way, dig into a slope and build up a foundation
        int base = groundLevel(level, fp);
        Plan plan = shaped(level, v, read(level, template, new BlockPos(corner.getX(), base, corner.getZ()), rot));
        Plan road = plan.door() == null ? null : road(level, v, plan, 20000);
        v.blueprint.addBuilding(plan.box(), plan.blocks(), plan.clear(), shortName(template));
        if (road != null) v.blueprint.addBuilding(road.box(), road.blocks(), road.clear(), "road");
        v.project = shortName(template);
        Villages.get(level).setDirty();
        return null;
    }

    /** How many beds a building has (to house maidens). Worked out once per building. */
    public static int beds(ServerLevel level, ResourceLocation template) {
        return BEDS.computeIfAbsent(template, t -> read(level, t, BlockPos.ZERO, Rotation.NONE).beds());
    }

    // ------------------------------------------------------------------ planning

    /**
     * Plans a building near a spot (the village centre, or where a citizen stands), with a road to the village's
     * roads, and adds it to the village. Returns what was planned, or null if there's no free spot for it.
     */
    @Nullable
    public static Plan build(ServerLevel level, VillageRecord v, ResourceLocation template, BlockPos near) {
        return build(level, v, template, near, false);
    }

    /** The same; instant ones are put in place all at once (a fortified village's first houses) instead of built. */
    @Nullable
    public static Plan build(ServerLevel level, VillageRecord v, ResourceLocation template, BlockPos near, boolean instant) {
        // the nearest good spot whose door can be joined to the village's roads by a walkable path (or, if none can,
        // the best spot anyway)
        int tried = 0;
        List<Site> sites = findSites(level, v, template, near);
        for (Site site : sites) {
            if (++tried > 12) break;
            Plan plan = shaped(level, v, read(level, template, site.origin(), site.rot()));
            Plan road = plan.door() == null ? null : road(level, v, plan);
            if (road == null) continue;
            v.blueprint.addBuilding(plan.box(), plan.blocks(), plan.clear(), shortName(template), instant);
            v.blueprint.addBuilding(road.box(), road.blocks(), road.clear(), "road", instant);
            if (!instant) v.project = shortName(template);
            Villages.get(level).setDirty();
            return plan;
        }
        if (sites.isEmpty()) return null;
        lastSearch += "; " + tried + " checked for a road, none could be joined (built without one)";
        Site site = sites.get(0);
        Plan plan = shaped(level, v, read(level, template, site.origin(), site.rot()));
        v.blueprint.addBuilding(plan.box(), plan.blocks(), plan.clear(), shortName(template), instant);
        if (!instant) v.project = shortName(template);
        Villages.get(level).setDirty();
        return plan;
    }

    private record Site(BlockPos origin, Rotation rot, double distance) {}

    /** Flat, dry, free spots for the building near a place, nearest first. */
    private static List<Site> findSites(ServerLevel level, VillageRecord v, ResourceLocation template, BlockPos near) {
        RandomSource r = RandomSource.create(level.getGameTime() ^ v.id);
        StructureTemplate t = level.getStructureManager().getOrCreate(template);
        List<Site> sites = new ArrayList<>();
        // inside the walls, if it has any (the wall pieces themselves are kept clear of by the overlap check)
        BoundingBox allowed = v.wallBox != null ? v.wallBox.inflatedBy(-8) : v.box.inflatedBy(40);
        int unloaded = 0, outside = 0, overlap = 0, ground = 0;
        for (int i = 0; i < TRIES; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            // anywhere from right here out to past the village's edge
            double dist = 6 + r.nextDouble() * (Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2.0 + 40);
            int x = near.getX() + (int) (Math.cos(angle) * dist);
            int z = near.getZ() + (int) (Math.sin(angle) * dist);
            Rotation rot = Rotation.getRandom(r);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) { unloaded++; continue; }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BoundingBox fp = t.getBoundingBox(new StructurePlaceSettings().setRotation(rot), new BlockPos(x, y, z));
            if (!allowed.isInside(new BlockPos(fp.getCenter().getX(), allowed.minY(), fp.getCenter().getZ()))) { outside++; continue; }
            if (!level.hasChunksAt(fp.minX() - 2, fp.minZ() - 2, fp.maxX() + 2, fp.maxZ() + 2)) { unloaded++; continue; }
            if (v.blueprint.overlaps(fp.inflatedBy(2))) { overlap++; continue; }
            int base = groundLevel(level, fp);
            if (base == Integer.MIN_VALUE) { ground++; continue; }
            // nearer is better (up or down a hill counts three times), and flatter and drier (less to dig and fill)
            double d = Math.sqrt(near.distSqr(fp.getCenter())) + 3 * Math.abs(base - near.getY()) + 4 * roughness(level, fp);
            sites.add(new Site(new BlockPos(x, base, z), rot, d));
        }
        lastSearch = shortName(template) + ": " + sites.size() + " good spots of " + TRIES + " tried (" + overlap + " on other buildings, " + ground
                + " under water, " + unloaded + " not loaded, " + outside + " too far)";
        sites.sort((p, q) -> Double.compare(p.distance(), q.distance()));
        return sites;
    }

    /** The height to build at: the top of the dry ground, averaged (the water's top if it's all water). */
    static int groundLevel(ServerLevel level, BoundingBox fp) {
        long sum = 0, wetSum = 0;
        int n = 0, wet = 0;
        for (int x = fp.minX(); x <= fp.maxX(); x++)
            for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
                int h = ground(level, x, z);
                if (!level.getFluidState(new BlockPos(x, h, z)).isEmpty()) {
                    wet++;
                    wetSum += h;
                    continue;
                }
                sum += h;
                n++;
            }
        if (n == 0 && wet == 0) return Integer.MIN_VALUE;
        return (int) Math.round(n > 0 ? (double) sum / n : (double) wetSum / wet);
    }

    /** How uneven a spot is (the spread of the ground's heights, and a bit for every column of water). */
    private static double roughness(ServerLevel level, BoundingBox fp) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0, all = 0;
        for (int x = fp.minX(); x <= fp.maxX(); x += 2)
            for (int z = fp.minZ(); z <= fp.maxZ(); z += 2) {
                int h = ground(level, x, z);
                all++;
                if (!level.getFluidState(new BlockPos(x, h, z)).isEmpty()) wet++;
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        return (max - min) + 10.0 * wet / Math.max(1, all);
    }

    /** The top of the ground (under trees and plants). */
    private static int ground(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        for (int i = 0; i < 40; i++, p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) continue;
            return p.getY();
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /**
     * A building made to fit its spot: the trees on it and just round it are felled at once, a foundation goes under
     * it down to the ground (or the bottom of shallow water), and any ground higher than its floor is dug away (up
     * through its roof, so it isn't buried in a hillside).
     */
    static Plan shaped(ServerLevel level, VillageRecord v, Plan p) {
        BoundingBox b = p.box();
        for (int x = b.minX() - 2; x <= b.maxX() + 2; x++)
            for (int z = b.minZ() - 2; z <= b.maxZ() + 2; z++) {
                BlockPos top = Trees.treeTop(level, x, z);
                if (top != null) Trees.fellAt(level, v, top);
            }
        Plan f = withFoundation(level, p, 16);
        Set<BlockPos> clear = new HashSet<>(f.clear());
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                for (int y = b.maxY() + 1; y <= Math.min(top, b.maxY() + 16); y++) clear.add(new BlockPos(x, y, z));
            }
        if (clear.size() == f.clear().size()) return f;
        List<BlockPos> all = new ArrayList<>(f.blocks().keySet());
        all.addAll(clear);
        BoundingBox box = BoundingBox.encapsulatingPositions(all).orElse(f.box());
        return new Plan(f.template(), box, f.blocks(), clear, f.beds(), f.door());
    }

    /** Fills the gaps under the bottom layer down to the ground, so nothing floats on a slope. */
    static Plan withFoundation(ServerLevel level, Plan p) {
        return withFoundation(level, p, 8);
    }

    /** The same, filling down at most depth blocks. */
    static Plan withFoundation(ServerLevel level, Plan p, int depth) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BoundingBox b = p.box();
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                BlockState floor = p.blocks().get(new BlockPos(x, b.minY(), z));
                if (floor == null) continue;
                BlockState fill = floor.isCollisionShapeFullBlock(level, BlockPos.ZERO) && !floor.hasBlockEntity() ? floor : Blocks.COBBLESTONE.defaultBlockState();
                for (int y = b.minY() - 1; y >= b.minY() - depth; y--) {
                    BlockPos q = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(q);
                    if (!s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty() && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES)) break;
                    blocks.put(q, fill);
                }
            }
        if (blocks.isEmpty()) return p;
        blocks.putAll(p.blocks());
        List<BlockPos> all = new ArrayList<>(blocks.keySet());
        all.addAll(p.clear());
        BoundingBox box = BoundingBox.encapsulatingPositions(all).orElse(b);
        return new Plan(p.template(), box, blocks, p.clear(), p.beds(), p.door());
    }

    /** A building's blocks placed at a spot with a rotation (the box is just the building's own). */
    private static Plan read(ServerLevel level, ResourceLocation id, BlockPos origin, Rotation rot) {
        StructureTemplate t = level.getStructureManager().getOrCreate(id);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rot);
        BoundingBox box = t.getBoundingBox(settings, origin);
        CompoundTag nbt = t.save(new CompoundTag());
        HolderLookup<Block> blockLookup = level.holderLookup(Registries.BLOCK);
        ListTag palTag = nbt.contains("palettes") ? nbt.getList("palettes", Tag.TAG_LIST).getList(0) : nbt.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < palTag.size(); i++) palette.add(NbtUtils.readBlockState(blockLookup, palTag.getCompound(i)));
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Set<BlockPos> clear = new HashSet<>();
        int beds = 0;
        BlockPos door = null;
        ListTag list = nbt.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            BlockPos rel = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
            BlockPos world = StructureTemplate.calculateRelativePosition(settings, rel).offset(origin);
            int stateId = b.getInt("state");
            if (stateId < 0 || stateId >= palette.size()) continue;
            BlockState s = palette.get(stateId).rotate(rot);
            if (s.is(Blocks.STRUCTURE_VOID)) continue;
            if (s.is(Blocks.JIGSAW)) {
                Direction front = s.getValue(JigsawBlock.ORIENTATION).front();
                if (front.getAxis().isHorizontal()) door = world.relative(front);
                s = finalState(blockLookup, b.getCompound("nbt"));
            }
            if (s.isAir()) {
                clear.add(world);
                continue;
            }
            if (s.is(BlockTags.BEDS) && s.getValue(BedBlock.PART) == BedPart.HEAD) beds++;
            blocks.put(world, s);
        }
        return new Plan(id, box, blocks, clear, beds, door);
    }

    private static BlockState finalState(HolderLookup<Block> blocks, CompoundTag nbt) {
        String s = nbt.getString("final_state");
        if (s.isEmpty()) return Blocks.AIR.defaultBlockState();
        try {
            return BlockStateParser.parseForBlock(blocks, s, false).blockState();
        } catch (Exception e) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    // ------------------------------------------------------------------ the road to it

    /** A path from the building's door to the nearest village road, over the ground, avoiding other buildings. */
    @Nullable
    static Plan road(ServerLevel level, VillageRecord v, Plan house) {
        return road(level, v, house, ROAD_SEARCH);
    }

    /**
     * The same, searching further (up to limit columns); over water a plank bridge is laid at the water's top (the
     * search goes over water only after trying the land).
     */
    static Plan road(ServerLevel level, VillageRecord v, Plan house, int limit) {
        Map<Long, BlockState> roadAt = new HashMap<>();
        v.blueprint.forEachBlock((p, s) -> {
            if (s.is(Upkeep.ROAD)) roadAt.put(column(p.getX(), p.getZ()), s);
        });
        if (roadAt.isEmpty()) return null;
        BoundingBox avoid = house.box().inflatedBy(1);
        BlockPos start = house.door();
        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Integer> height = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long first = column(start.getX(), start.getZ());
        cameFrom.put(first, first);
        height.put(first, surface(level, start.getX(), start.getZ()));
        queue.add(first);
        Long goal = null;
        BlockState material = Blocks.DIRT_PATH.defaultBlockState();
        while (!queue.isEmpty() && cameFrom.size() < limit) {
            long c = queue.poll();
            int x = colX(c), z = colZ(c), h = height.get(c);
            BlockState road = roadAt.get(c);
            if (road != null) {
                goal = c;
                material = road;
                break;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ();
                long n = column(nx, nz);
                if (cameFrom.containsKey(n) || !level.hasChunkAt(new BlockPos(nx, 0, nz))) continue;
                int nh = surface(level, nx, nz);
                if (Math.abs(nh - h) > 1) continue;
                BlockPos top = new BlockPos(nx, nh, nz);
                boolean isRoad = roadAt.containsKey(n);
                if (!isRoad) {
                    // water: a bridge (only in a long search; not lava)
                    boolean water = level.getFluidState(top).is(net.minecraft.tags.FluidTags.WATER);
                    if (avoid.isInside(top) || !level.getFluidState(top).isEmpty() && !(water && limit > ROAD_SEARCH)) continue;
                    if (v.blueprint.expected(top) != null || v.blueprint.expected(top.above()) != null || v.blueprint.isBuildingSite(top.above())) continue;
                }
                cameFrom.put(n, c);
                height.put(n, nh);
                queue.add(n);
            }
        }
        if (goal == null) return null;
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Set<BlockPos> clear = new HashSet<>();
        List<Long> path = new ArrayList<>();
        for (long c = cameFrom.get(goal); ; c = cameFrom.get(c)) {
            path.add(c);
            if (c == first) break;
        }
        for (long c : path) {
            BlockPos top = new BlockPos(colX(c), height.get(c), colZ(c));
            blocks.put(top, level.getFluidState(top).isEmpty() ? material : Blocks.SPRUCE_PLANKS.defaultBlockState());
            clear.add(top.above());
            clear.add(top.above(2));
        }
        // three wide: the ground either side of the path too, where it's open and level enough (not by the house)
        for (long c : path) {
            int x = colX(c), z = colZ(c), h = height.get(c);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ();
                if (height.containsKey(column(nx, nz)) && path.contains(column(nx, nz))) continue;
                if (!level.hasChunkAt(new BlockPos(nx, 0, nz))) continue;
                int nh = surface(level, nx, nz);
                if (Math.abs(nh - h) > 1) continue;
                BlockPos side = new BlockPos(nx, nh, nz);
                if (blocks.containsKey(side) || avoid.isInside(side) || roadAt.containsKey(column(nx, nz)) || !level.getFluidState(side).isEmpty()) continue;
                if (v.blueprint.expected(side) != null || v.blueprint.expected(side.above()) != null || v.blueprint.isBuildingSite(side.above())) continue;
                BlockState ground = level.getBlockState(side);
                if (!ground.is(net.minecraft.tags.BlockTags.DIRT) && !ground.is(net.minecraft.tags.BlockTags.SAND) && !ground.is(Blocks.GRAVEL)) continue;
                blocks.put(side, material);
                if (level.getBlockState(side.above()).canBeReplaced()) clear.add(side.above());
            }
        }
        List<BlockPos> all = new ArrayList<>(blocks.keySet());
        all.addAll(clear);
        BoundingBox box = BoundingBox.encapsulatingPositions(all).orElse(null);
        return box == null ? null : new Plan(house.template(), box, blocks, clear, 0, null);
    }

    /** A path from a spot (where a road between villages ends) to the village's own streets. True if there is one. */
    public static boolean joinToStreets(ServerLevel level, VillageRecord v, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return false;
        BlockPos at = new BlockPos(x, surface(level, x, z), z);
        Plan road = road(level, v, new Plan(Castle.ID, new BoundingBox(at), Map.of(), Set.of(), 0, at));
        if (road == null) return false;
        v.blueprint.addBuilding(road.box(), road.blocks(), road.clear(), "road", true);
        Villages.get(level).setDirty();
        return true;
    }

    /** The top solid block of a column (what a road replaces). */
    private static int surface(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int colX(long c) {
        return (int) (c >> 32);
    }

    private static int colZ(long c) {
        return (int) c;
    }
}

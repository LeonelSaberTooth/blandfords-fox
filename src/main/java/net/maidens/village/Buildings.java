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
    private static final int TRIES = 96, MAX_SLOPE = 3, ROAD_SEARCH = 6000;

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

    /** How many beds a building has (to house maidens). */
    public static int beds(ServerLevel level, ResourceLocation template) {
        return read(level, template, BlockPos.ZERO, Rotation.NONE).beds();
    }

    // ------------------------------------------------------------------ planning

    /**
     * Plans a building near a spot (the village centre, or where a citizen stands), with a road to the village's
     * roads, and adds it to the village. Returns what was planned, or null if there's no free spot for it.
     */
    @Nullable
    public static Plan build(ServerLevel level, VillageRecord v, ResourceLocation template, BlockPos near) {
        Plan plan = findSite(level, v, template, near);
        if (plan == null) return null;
        v.blueprint.addBuilding(plan.box(), plan.blocks(), plan.clear());
        if (plan.door() != null) {
            Plan road = road(level, v, plan);
            if (road != null) v.blueprint.addBuilding(road.box(), road.blocks(), road.clear());
        }
        v.project = shortName(template);
        Villages.get(level).setDirty();
        return plan;
    }

    @Nullable
    private static Plan findSite(ServerLevel level, VillageRecord v, ResourceLocation template, BlockPos near) {
        RandomSource r = RandomSource.create(level.getGameTime() ^ v.id);
        StructureTemplate t = level.getStructureManager().getOrCreate(template);
        BlockPos bestOrigin = null;
        Rotation bestRot = Rotation.NONE;
        BoundingBox allowed = v.box.inflatedBy(40);
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < TRIES; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double dist = 6 + r.nextDouble() * 40;
            int x = near.getX() + (int) (Math.cos(angle) * dist);
            int z = near.getZ() + (int) (Math.sin(angle) * dist);
            Rotation rot = Rotation.getRandom(r);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BoundingBox fp = t.getBoundingBox(new StructurePlaceSettings().setRotation(rot), new BlockPos(x, y, z));
            if (!allowed.isInside(new BlockPos(fp.getCenter().getX(), allowed.minY(), fp.getCenter().getZ()))) continue;
            if (!level.hasChunksAt(fp.minX() - 2, fp.minZ() - 2, fp.maxX() + 2, fp.maxZ() + 2)) continue;
            if (v.blueprint.overlaps(fp.inflatedBy(2))) continue;
            int base = groundLevel(level, fp);
            if (base == Integer.MIN_VALUE) continue;
            double d = near.distSqr(fp.getCenter());
            if (d < bestD) {
                bestD = d;
                bestOrigin = new BlockPos(x, base, z);
                bestRot = rot;
            }
        }
        if (bestOrigin == null) return null;
        return withFoundation(level, read(level, template, bestOrigin, bestRot));
    }

    /** The height to build at (the top of the ground, averaged), or MIN_VALUE if it's too steep or wet here. */
    private static int groundLevel(ServerLevel level, BoundingBox fp) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        long sum = 0;
        int n = 0;
        for (int x = fp.minX(); x <= fp.maxX(); x++)
            for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (!level.getFluidState(new BlockPos(x, h, z)).isEmpty()) return Integer.MIN_VALUE;
                min = Math.min(min, h);
                max = Math.max(max, h);
                sum += h;
                n++;
            }
        if (n == 0 || max - min > MAX_SLOPE) return Integer.MIN_VALUE;
        return (int) Math.round((double) sum / n);
    }

    /** Fills the gaps under the bottom layer down to the ground, so nothing floats on a slope. */
    private static Plan withFoundation(ServerLevel level, Plan p) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BoundingBox b = p.box();
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                BlockState floor = p.blocks().get(new BlockPos(x, b.minY(), z));
                if (floor == null) continue;
                BlockState fill = floor.isCollisionShapeFullBlock(level, BlockPos.ZERO) && !floor.hasBlockEntity() ? floor : Blocks.COBBLESTONE.defaultBlockState();
                for (int y = b.minY() - 1; y >= b.minY() - 8; y--) {
                    BlockPos q = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(q);
                    if (!s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty()) break;
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
    private static Plan road(ServerLevel level, VillageRecord v, Plan house) {
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
        while (!queue.isEmpty() && cameFrom.size() < ROAD_SEARCH) {
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
                    if (avoid.isInside(top) || !level.getFluidState(top).isEmpty()) continue;
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
        for (long c = cameFrom.get(goal); c != first || blocks.isEmpty(); c = cameFrom.get(c)) {
            BlockPos top = new BlockPos(colX(c), height.get(c), colZ(c));
            blocks.put(top, material);
            clear.add(top.above());
            clear.add(top.above(2));
            if (c == first) break;
        }
        List<BlockPos> all = new ArrayList<>(blocks.keySet());
        all.addAll(clear);
        BoundingBox box = BoundingBox.encapsulatingPositions(all).orElse(null);
        return box == null ? null : new Plan(house.template(), box, blocks, clear, 0, null);
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

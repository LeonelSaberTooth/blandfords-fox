package net.maidens.village;

import net.maidens.MaidensMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How a village should look, piece by piece (a house, a road...): for every spot in a piece's box, the block that
 * belongs there.
 * <ul>
 *   <li>Pieces of the original village are recorded from the world once all the chunks they cover are loaded, so
 *       they fill in as the player explores. Natural blocks (terrain, plants, leaves, crops: the block tag
 *       {@code maidens:not_village_blueprint}), air and fluids aren't part of them.</li>
 *   <li>New buildings are added already planned, marked as being built: their spots can also say "keep clear"
 *       (the air inside a house). Once nothing is left to do, the building becomes an ordinary piece.</li>
 * </ul>
 * Used for anger (breaking a blueprint block) and by {@link Upkeep} for repairs and building.
 */
public final class Blueprint {
    public static final TagKey<Block> NOT_BLUEPRINT = BlockTags.create(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "not_village_blueprint"));
    /** Cell values: 0 not part of the blueprint, -1 must be kept clear (buildings only), else a palette index. */
    private static final int NOTHING = 0, KEEP_CLEAR = -1;

    private static final class Piece {
        final BoundingBox box;
        /** What it is: a building's template name ("plains_small_house_1"), "road", or "" if unknown. */
        String name = "";
        @Nullable int[] cells;
        boolean building;
        /** Put in place all at once (as soon as its land is loaded) instead of block by block by the maidens. */
        boolean instant;
        /** During a scan of a building: whether anything still needed doing. */
        boolean workLeft;

        Piece(BoundingBox box) {
            this.box = box;
        }

        int index(BlockPos p) {
            return (p.getX() - box.minX()) + box.getXSpan() * ((p.getZ() - box.minZ()) + box.getZSpan() * (p.getY() - box.minY()));
        }

        int size() {
            return box.getXSpan() * box.getYSpan() * box.getZSpan();
        }
    }

    /** What the scan reports for each spot. */
    public interface Visitor {
        /** expected is null for "keep clear". Returns true if the spot still needs work. */
        boolean visit(BlockPos pos, @Nullable BlockState expected, boolean building);
    }

    private final List<Piece> pieces = new ArrayList<>();
    /** Palette shared by all pieces; entry 0 is unused. */
    private final List<BlockState> palette = new ArrayList<>(List.of(Blocks.AIR.defaultBlockState()));
    private final Map<BlockState, Integer> paletteIndex = new HashMap<>();
    private int scanPiece, scanCell;
    /** Which pieces touch each chunk column, so a lookup only checks the few pieces there, not all of them. */
    private final Map<Long, List<Piece>> byChunk = new HashMap<>();

    private void index(Piece p) {
        BoundingBox b = p.box;
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++)
            for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++)
                byChunk.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>()).add(p);
    }

    private List<Piece> piecesAt(BlockPos p) {
        List<Piece> list = byChunk.get(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
        return list == null ? List.of() : list;
    }

    void addPiece(BoundingBox box, String name) {
        Piece p = new Piece(box);
        p.name = name;
        pieces.add(p);
        index(p);
    }

    /** A new building: blocks to place, and spots to keep clear (everything else in the box is left alone). */
    void addBuilding(BoundingBox box, Map<BlockPos, BlockState> blocks, Iterable<BlockPos> clear, String name) {
        addBuilding(box, blocks, clear, name, false);
    }

    /** The same, optionally put in place all at once ({@link #placeInstant}) rather than built by the maidens. */
    void addBuilding(BoundingBox box, Map<BlockPos, BlockState> blocks, Iterable<BlockPos> clear, String name, boolean instant) {
        Piece p = new Piece(box);
        p.name = name;
        p.cells = new int[p.size()];
        p.building = true;
        p.instant = instant;
        for (BlockPos c : clear) if (box.isInside(c)) p.cells[p.index(c)] = KEEP_CLEAR;
        blocks.forEach((pos, s) -> {
            if (box.isInside(pos)) p.cells[p.index(pos)] = paletteId(s);
        });
        pieces.add(p);
        index(p);
    }

    public static boolean counts(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && !s.is(NOT_BLUEPRINT);
    }

    /** Records every original piece whose chunks are now all loaded. Returns true if anything new was recorded. */
    boolean recordLoadedPieces(ServerLevel level) {
        boolean any = false;
        for (Piece piece : pieces) {
            if (piece.cells != null) continue;
            BoundingBox b = piece.box;
            if (!level.hasChunksAt(b.minX(), b.minZ(), b.maxX(), b.maxZ())) continue;
            int[] cells = new int[piece.size()];
            for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                BlockState s = level.getBlockState(p);
                if (counts(s)) cells[piece.index(p)] = paletteId(s);
            }
            piece.cells = cells;
            any = true;
        }
        return any;
    }

    private int paletteId(BlockState s) {
        return paletteIndex.computeIfAbsent(s, k -> {
            palette.add(k);
            return palette.size() - 1;
        });
    }

    /** The block the village expects here, or null if this spot isn't part of the blueprint (or only kept clear). */
    @Nullable
    public BlockState expected(BlockPos p) {
        for (Piece piece : piecesAt(p)) {
            if (piece.cells == null || !piece.box.isInside(p)) continue;
            int id = piece.cells[piece.index(p)];
            if (id > 0) return palette.get(id);
        }
        return null;
    }

    /** Inside a building under construction: whether this spot must be cleared (something else stands there). */
    public boolean isBuildingSite(BlockPos p) {
        for (Piece piece : piecesAt(p)) {
            if (piece.building && piece.box.isInside(p) && piece.cells[piece.index(p)] != NOTHING) return true;
        }
        return false;
    }

    public boolean overlaps(BoundingBox box) {
        for (Piece piece : pieces) if (piece.box.intersects(box)) return true;
        return false;
    }

    public boolean isBuilding() {
        for (Piece piece : pieces) if (piece.building) return true;
        return false;
    }

    /** A citizen changed this spot: the village now expects the new block (or nothing). */
    void set(BlockPos p, BlockState s) {
        int id = counts(s) ? paletteId(s) : NOTHING;
        for (Piece piece : piecesAt(p)) {
            if (piece.cells != null && piece.box.isInside(p)) piece.cells[piece.index(p)] = id;
        }
    }

    /**
     * Visits up to budget spots, carrying on where the last call stopped (only pieces whose chunks are loaded).
     * A building whose whole box was visited with nothing left to do is finished. Returns true if one finished.
     */
    boolean scan(ServerLevel level, int budget, Visitor visitor) {
        if (pieces.isEmpty()) return false;
        boolean finished = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int skipped = 0;
        while (budget > 0 && skipped <= pieces.size()) {
            if (scanPiece >= pieces.size()) scanPiece = 0;
            Piece piece = pieces.get(scanPiece);
            BoundingBox b = piece.box;
            if (piece.cells == null || piece.instant || scanCell >= piece.cells.length || !level.hasChunksAt(b.minX(), b.minZ(), b.maxX(), b.maxZ())) {
                if (piece.building && piece.cells != null && scanCell >= piece.cells.length && !piece.workLeft) {
                    finish(piece);
                    finished = true;
                }
                scanPiece++;
                scanCell = 0;
                skipped++;
                continue;
            }
            if (scanCell == 0) piece.workLeft = false;
            int xs = b.getXSpan(), zs = b.getZSpan();
            for (; scanCell < piece.cells.length && budget > 0; scanCell++, budget--) {
                int id = piece.cells[scanCell];
                if (id == NOTHING) continue;
                int i = scanCell;
                p.set(b.minX() + i % xs, b.minY() + i / (xs * zs), b.minZ() + (i / xs) % zs);
                if (visitor.visit(p, id > 0 ? palette.get(id) : null, piece.building)) piece.workLeft = true;
            }
        }
        return finished;
    }

    private static void finish(Piece piece) {
        piece.building = false;
        piece.instant = false;
        for (int i = 0; i < piece.cells.length; i++) if (piece.cells[i] == KEEP_CLEAR) piece.cells[i] = NOTHING;
    }

    private int instantPiece, instantCell;

    /** Whether some pieces are still waiting to be put in place all at once. */
    public boolean hasInstant() {
        for (Piece p : pieces) if (p.instant) return true;
        return false;
    }

    /**
     * Puts instant pieces in place, up to budget spots per call, in land that's loaded (the rest waits until it is).
     * Blocks go in without sounds or drops; spots to keep clear are emptied. Returns how many blocks were set.
     */
    int placeInstant(ServerLevel level, int budget, java.util.function.Consumer<BlockPos> banner) {
        int set = 0, skipped = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (budget > 0 && skipped <= pieces.size()) {
            if (instantPiece >= pieces.size()) instantPiece = 0;
            Piece piece = pieces.get(instantPiece);
            BoundingBox b = piece.box;
            if (!piece.instant || !level.hasChunksAt(b.minX(), b.minZ(), b.maxX(), b.maxZ())) {
                instantPiece++;
                instantCell = 0;
                skipped++;
                continue;
            }
            skipped = 0;
            int xs = b.getXSpan(), zs = b.getZSpan();
            for (; instantCell < piece.cells.length && budget > 0; instantCell++, budget--) {
                int id = piece.cells[instantCell];
                if (id == NOTHING) continue;
                int i = instantCell;
                p.set(b.minX() + i % xs, b.minY() + i / (xs * zs), b.minZ() + (i / xs) % zs);
                BlockState now = level.getBlockState(p);
                if (id > 0) {
                    BlockState want = palette.get(id);
                    if (now != want) {
                        // anyone standing there steps aside first (others: the block waits for a repair)
                        if (!want.getCollisionShape(level, p).isEmpty()) {
                            boolean blocked = false;
                            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(p))) {
                                if (!(e instanceof net.maidens.maiden.Maiden mm) || !mm.moveNear(p)) blocked = true;
                            }
                            if (blocked) continue;
                        }
                        Place.set(level, p.immutable(), want, q -> expected(q) != null);
                        if (want.getBlock() instanceof net.minecraft.world.level.block.AbstractBannerBlock) banner.accept(p.immutable());
                        set++;
                    }
                } else if (!now.isAir() && expected(p) == null) {
                    Place.remove(level, p.immutable(), q -> expected(q) != null);
                    set++;
                }
            }
            if (instantCell >= piece.cells.length) {
                finish(piece);
                instantPiece++;
                instantCell = 0;
            }
        }
        return set;
    }

    /** Every recorded block of the blueprint. */
    public void forEachBlock(java.util.function.BiConsumer<BlockPos, BlockState> action) {
        for (Piece piece : pieces) {
            if (piece.cells == null) continue;
            BoundingBox b = piece.box;
            int xs = b.getXSpan(), zs = b.getZSpan();
            for (int i = 0; i < piece.cells.length; i++) {
                int id = piece.cells[i];
                if (id > 0) action.accept(new BlockPos(b.minX() + i % xs, b.minY() + i / (xs * zs), b.minZ() + (i / xs) % zs), palette.get(id));
            }
        }
    }

    /** A piece as the village map shows it. */
    public record PieceInfo(BoundingBox box, String name, boolean building) {}

    public List<PieceInfo> pieces() {
        List<PieceInfo> out = new ArrayList<>();
        for (Piece p : pieces) out.add(new PieceInfo(p.box, p.name, p.building));
        return out;
    }

    /** Every piece's box (for planning walls around them all). */
    public List<BoundingBox> boxes() {
        List<BoundingBox> out = new ArrayList<>();
        for (Piece p : pieces) out.add(p.box);
        return out;
    }

    public int recordedPieces() {
        int n = 0;
        for (Piece p : pieces) if (p.cells != null) n++;
        return n;
    }

    public int pieceCount() {
        return pieces.size();
    }

    public int blockCount() {
        int n = 0;
        for (Piece p : pieces) if (p.cells != null) for (int c : p.cells) if (c > 0) n++;
        return n;
    }

    // ------------------------------------------------------------------ saving

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag pal = new ListTag();
        for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
        t.put("Palette", pal);
        ListTag list = new ListTag();
        for (Piece p : pieces) {
            CompoundTag pt = new CompoundTag();
            pt.putIntArray("Box", new int[]{p.box.minX(), p.box.minY(), p.box.minZ(), p.box.maxX(), p.box.maxY(), p.box.maxZ()});
            if (p.cells != null) pt.putIntArray("Cells", p.cells);
            if (p.building) pt.putBoolean("Building", true);
            if (p.instant) pt.putBoolean("Instant", true);
            if (!p.name.isEmpty()) pt.putString("Name", p.name);
            list.add(pt);
        }
        t.put("Pieces", list);
        return t;
    }

    static Blueprint load(CompoundTag t, HolderLookup.Provider registries) {
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        Blueprint bp = new Blueprint();
        ListTag pal = t.getList("Palette", Tag.TAG_COMPOUND);
        if (!pal.isEmpty()) bp.palette.clear();
        for (int i = 0; i < pal.size(); i++) {
            BlockState s = NbtUtils.readBlockState(blocks, pal.getCompound(i));
            bp.palette.add(s);
            if (i > 0) bp.paletteIndex.putIfAbsent(s, i);
        }
        ListTag list = t.getList("Pieces", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag pt = list.getCompound(i);
            int[] b = pt.getIntArray("Box");
            if (b.length != 6) continue;
            Piece p = new Piece(new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]));
            if (pt.contains("Cells")) {
                int[] cells = pt.getIntArray("Cells");
                if (cells.length == p.size()) p.cells = cells;
            }
            p.building = p.cells != null && pt.getBoolean("Building");
            p.instant = p.building && pt.getBoolean("Instant");
            p.name = pt.getString("Name");
            bp.pieces.add(p);
            bp.index(p);
        }
        return bp;
    }
}

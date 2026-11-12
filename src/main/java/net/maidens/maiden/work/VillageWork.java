package net.maidens.maiden.work;

import net.maidens.maiden.Maiden;
import net.maidens.maiden.home.Schedule;
import net.maidens.village.VillageRecord;
import net.maidens.village.VillageSocial;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A grown resident's job for her village: farmer, woodcutter, miner, fisher or guard. She doesn't really farm or
 * dig: she goes to her spot in work hours and works away there ({@link VillageWorkGoal}), and each day the village
 * gets a fixed amount for every worker ({@code Economy}). Her trade (the vanilla profession whose things she wears at
 * work) is picked once for her job. A guard either keeps a gate or walks round the walls, in an iron helmet and
 * chestplate with a sword (only on duty).
 */
public final class VillageWork {
    private static final String[] FARM = {"farmer", "shepherd"}, WOOD = {"fletcher", "leatherworker"},
            MINE = {"mason", "weaponsmith", "toolsmith", "armorer"}, FISH = {"fisherman"};

    private final Maiden m;
    private Job job = Job.NONE;
    @Nullable private BlockPos spot;
    /** A guard's post: which gate she keeps (or, past the last gate, she walks round the walls). */
    private int post;
    /** The wood her woodcutting brings in (the trees by her spot). */
    private String log = "minecraft:oak_log";

    public VillageWork(Maiden m) {
        this.m = m;
    }

    public Job job() {
        return job;
    }

    @Nullable
    public BlockPos spot() {
        return spot;
    }

    public int post() {
        return post;
    }

    public String log() {
        return log;
    }

    /** Gives her a job at a spot (a guard's post instead of a spot). */
    public void set(Job j, @Nullable BlockPos at, int guardPost) {
        job = j;
        spot = at == null ? null : at.immutable();
        post = guardPost;
    }

    public void setLog(String id) {
        log = id;
    }

    public void clear() {
        job = Job.NONE;
        spot = null;
    }

    /** The trade whose things she wears for a job: one picked for her of the job's (always the same). */
    public String trade(Job j) {
        String[] from = switch (j) {
            case FARM -> FARM;
            case WOOD -> WOOD;
            case MINE -> MINE;
            case FISH -> FISH;
            default -> null;
        };
        return from == null ? "" : from[Math.floorMod(m.getUUID().hashCode() >>> 3, from.length)];
    }

    /** At her village work right now: keeping to her day, in work hours, with a job and somewhere to do it. */
    public boolean onDuty() {
        return job != Job.NONE && (job == Job.GUARD || spot != null) && Schedule.adult(m) && !m.isRoyal()
                && Schedule.activity(m) == Schedule.Activity.WORK;
    }

    /** A guard who walks round the walls (or the edge of the village) rather than keeping a gate. */
    public boolean patrols(VillageRecord v) {
        return job == Job.GUARD && post >= Math.max(v.gates.size(), 2);
    }

    /** Where she works: her spot, or a guard's gate (none for one who walks round). */
    @Nullable
    public BlockPos place(VillageRecord v) {
        if (job != Job.GUARD) return spot;
        if (patrols(v)) return null;
        return gatePost(v);
    }

    /** Just inside one of the gates (each guard her own), or by the bell if there are none. */
    public BlockPos gatePost(VillageRecord v) {
        if (v.gates.isEmpty()) return v.bell() != null ? v.bell() : v.centre();
        VillageRecord.Gate g = v.gates.get(Math.floorMod(post, v.gates.size()));
        return g.pos.offset(-Integer.signum(g.dx) * 3, 0, -Integer.signum(g.dz) * 3);
    }

    /** The way out of her gate (to look out of it), or null. */
    @Nullable
    public BlockPos outside(VillageRecord v) {
        if (v.gates.isEmpty()) return null;
        VillageRecord.Gate g = v.gates.get(Math.floorMod(post, v.gates.size()));
        return g.pos.offset(Integer.signum(g.dx) * 10, 0, Integer.signum(g.dz) * 10);
    }

    /**
     * The way round: the corners and the middle of each side of the walls, three blocks inside them (or the edge of
     * the village), on the ground.
     */
    public List<BlockPos> round(VillageRecord v) {
        BoundingBox b = v.wallBox != null ? v.wallBox : v.box;
        int in = v.wallBox != null ? 3 : 0;
        int x0 = b.minX() + in, x1 = b.maxX() - in, z0 = b.minZ() + in, z1 = b.maxZ() - in, xm = (x0 + x1) / 2, zm = (z0 + z1) / 2;
        int[][] xz = {{x0, z0}, {xm, z0}, {x1, z0}, {x1, zm}, {x1, z1}, {xm, z1}, {x0, z1}, {x0, zm}};
        List<BlockPos> out = new ArrayList<>();
        for (int[] p : xz) {
            if (!m.level().hasChunkAt(new BlockPos(p[0], 0, p[1]))) continue;
            out.add(new BlockPos(p[0], m.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p[0], p[1]), p[1]));
        }
        return out;
    }

    /** The tool for a job. */
    public static Item tool(Job j) {
        return switch (j) {
            case FARM -> Items.IRON_HOE;
            case WOOD -> Items.IRON_AXE;
            case MINE -> Items.IRON_PICKAXE;
            case FISH -> Items.FISHING_ROD;
            case GUARD -> Items.IRON_SWORD;
            case NONE -> Items.AIR;
        };
    }

    /**
     * Once a second: what she wears and holds for her village work. A guard on duty wears an iron helmet and
     * chestplate and carries a sword (the sword also when she fights); off duty they're put away. Other workers hold
     * their tool at work. Only her village's things are put on and taken off (a hired maiden wears her own).
     */
    public void dress() {
        if (m.employment().isHired() || m.isBaby()) return;
        boolean on = onDuty();
        if (job == Job.GUARD) {
            boolean fight = on || m.getTarget() != null || m.isDefending();
            swap(EquipmentSlot.HEAD, on, Items.IRON_HELMET, Items.CHAINMAIL_HELMET);
            swap(EquipmentSlot.CHEST, on, Items.IRON_CHESTPLATE);
            swap(EquipmentSlot.MAINHAND, fight, Items.IRON_SWORD);
        } else {
            // a former guard's helmet (from before guards took theirs off)
            swap(EquipmentSlot.HEAD, false, Items.CHAINMAIL_HELMET);
            Item tool = tool(job);
            if (tool != Items.AIR) swap(EquipmentSlot.MAINHAND, on, tool);
        }
    }

    /** Puts the first item on (if the slot's empty or has one of these) or takes these off. */
    private void swap(EquipmentSlot slot, boolean wear, Item item, Item... alsoOurs) {
        ItemStack now = m.getItemBySlot(slot);
        boolean ours = now.is(item);
        for (Item i : alsoOurs) ours |= now.is(i);
        if (wear && !now.is(item) && (now.isEmpty() || ours)) m.setItemSlot(slot, new ItemStack(item));
        else if (!wear && ours) m.setItemSlot(slot, ItemStack.EMPTY);
    }

    /** Her village, if she has one. */
    @Nullable
    VillageRecord village() {
        return VillageSocial.villageOf(m);
    }

    public void save(CompoundTag tag) {
        tag.putInt("VillageJob", job.ordinal());
        if (spot != null) tag.putLong("VillageSpot", spot.asLong());
        tag.putInt("GuardPost", post);
        tag.putString("VillageLog", log);
    }

    public void load(CompoundTag tag) {
        job = Job.byId(tag.getInt("VillageJob"));
        // from before village jobs: a guard was just a flag
        if (job == Job.NONE && tag.getBoolean("Guard")) job = Job.GUARD;
        spot = tag.contains("VillageSpot") ? BlockPos.of(tag.getLong("VillageSpot")) : null;
        post = tag.getInt("GuardPost");
        if (tag.contains("VillageLog")) log = tag.getString("VillageLog");
    }
}

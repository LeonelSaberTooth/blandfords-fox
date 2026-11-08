package net.maidens.maiden.work;

import net.maidens.core.MaidensConfig;
import net.maidens.maiden.Maiden;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Who hired a maiden, for what job, for how long, where she works, and her bag (what she gathers).
 * Hiring costs emeralds for one shift; her husband hires her for free with no time limit.
 * The work itself is done by {@link WorkGoal}.
 */
public final class Employment {
    private final Maiden m;
    private Job job = Job.NONE;
    @Nullable private UUID employer;
    private String employerName = "";
    @Nullable private UUID lastEmployer;
    /** Ticks left in the shift; -1 for no end. */
    private int shiftLeft;
    @Nullable BlockPos workPos;
    Direction workDir = Direction.NORTH;
    int mineStep;
    private final SimpleContainer bag = new SimpleContainer(27);
    /** What her work is doing and why she last gave up, for the self test. */
    String note = "";
    private String lastGiveUp = "";

    public Employment(Maiden m) {
        this.m = m;
    }

    public Job job() {
        return job;
    }

    public boolean isGuarding() {
        return job == Job.GUARD;
    }

    public boolean isWorking() {
        return job != Job.NONE;
    }

    @Nullable
    public BlockPos workPos() {
        return workPos;
    }

    public int shiftLeft() {
        return job == Job.NONE ? 0 : shiftLeft;
    }

    public int mineStep() {
        return mineStep;
    }

    public SimpleContainer bag() {
        return bag;
    }

    public String status() {
        return note + (lastGiveUp.isEmpty() ? "" : " | gave up: " + lastGiveUp);
    }

    @Nullable
    public Player employerPlayer() {
        return employer == null ? null : m.level().getPlayerByUUID(employer);
    }

    /** 0 nobody, 1 this player, 2 someone else. */
    public int employerFor(Player p) {
        return employer == null ? 0 : employer.equals(p.getUUID()) ? 1 : 2;
    }

    public String employerName() {
        return employerName;
    }

    public boolean canOpenBag(Player p) {
        return m.relationships().isMarriedTo(p) || p.getUUID().equals(employer) || p.getUUID().equals(lastEmployer);
    }

    // ------------------------------------------------------------------ every tick (server)

    public void tick() {
        if (job != Job.NONE && shiftLeft > 0 && --shiftLeft == 0) {
            Player p = employerPlayer();
            if (p instanceof ServerPlayer sp) m.say(sp, "My shift's over! Come by my house to collect what I gathered.");
            stop();
        }
        if (m.tickCount % 20 == 0 && job == Job.GUARD && m.getTarget() == null) equip(Job.GUARD);
    }

    // ------------------------------------------------------------------ the menu's Work page

    public String hire(ServerPlayer p, Job newJob) {
        String n = p.getName().getString();
        if (newJob == Job.NONE) return stopBy(p);
        boolean mine = p.getUUID().equals(employer);
        boolean wife = m.relationships().isMarriedTo(p);
        int rent = MaidensConfig.RENT_EMERALDS.get();
        if (employer != null && !mine) return "Sorry, " + n + ", I'm working for " + employerName + " today.";
        if (!mine && !wife && !p.getAbilities().instabuild) {
            if (p.getInventory().countItem(Items.EMERALD) < rent) return "A day's work is " + rent + " emeralds, " + n + ".";
            ContainerHelper.clearOrCountMatchingItems(p.getInventory(), s -> s.is(Items.EMERALD), rent, false);
        }
        if (!mine) shiftLeft = wife ? -1 : MaidensConfig.SHIFT_TICKS.get();
        employer = p.getUUID();
        employerName = n;
        lastEmployer = p.getUUID();
        start(newJob, p.getDirection());
        return switch (newJob) {
            case FARM -> "I'll tend the crops around my house and keep the harvest in my bag.";
            case GUARD -> "I've got your back, " + n + ". Put a sword in my bag and I'll use it.";
            case WOOD -> "I'll chop the trees around here and replant saplings if I have some.";
            case MINE -> "I'll dig a staircase down, " + workDir.getName() + " from here, then tunnel. Torches in my bag and I'll light it.";
            case FISH -> "I'll fish at the nearest water. Wish me luck!";
            case NONE -> "";
        } + (wife ? " For you, it's free, my love." : "");
    }

    public String stopBy(ServerPlayer p) {
        if (job == Job.NONE) return "I'm not working right now.";
        if (!p.getUUID().equals(employer) && !m.relationships().isMarriedTo(p)) return "You're not the one who hired me.";
        stop();
        return "Alright, I'll stop. Come see my bag for what I gathered.";
    }

    /** Opens her bag for the player, or returns why not. */
    @Nullable
    public String openBag(ServerPlayer p) {
        if (!canOpenBag(p)) return "That's my bag, " + p.getName().getString() + "! Hire me first.";
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, bag), Component.literal(m.name() + "'s bag")));
        return null;
    }

    /** Her husband hired her: the shift no longer ends. */
    public void onMarried(Player p) {
        if (p.getUUID().equals(employer)) shiftLeft = -1;
    }

    // ------------------------------------------------------------------ starting and stopping

    void start(Job newJob, Direction facing) {
        job = newJob;
        BlockPos home = m.getHome();
        workPos = newJob == Job.FARM && home != null ? home : m.blockPosition();
        workDir = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        mineStep = 0;
        lastGiveUp = "";
        m.setTarget(null);
        m.getNavigation().stop();
        equip(newJob);
    }

    public void stop() {
        job = Job.NONE;
        employer = null;
        employerName = "";
        shiftLeft = 0;
        m.setTarget(null);
        equip(Job.NONE);
    }

    /** Her work has to give up (lava ahead, a full bag...): tells her employer why. */
    void giveUp(String why) {
        lastGiveUp = why;
        if (employerPlayer() instanceof ServerPlayer sp) m.say(sp, why);
        stop();
    }

    /** For the self test: puts her to work without the menu or payment. */
    public void hireForTest(Job j, @Nullable Player who) {
        if (who != null) {
            employer = who.getUUID();
            employerName = who.getName().getString();
            lastEmployer = employer;
        }
        shiftLeft = MaidensConfig.SHIFT_TICKS.get();
        start(j, Direction.NORTH);
    }

    private void equip(Job j) {
        ItemStack tool = switch (j) {
            case FARM -> new ItemStack(Items.IRON_HOE);
            case WOOD -> new ItemStack(Items.IRON_AXE);
            case MINE -> new ItemStack(Items.IRON_PICKAXE);
            case FISH -> new ItemStack(Items.FISHING_ROD);
            case GUARD -> bestWeapon();
            case NONE -> ItemStack.EMPTY;
        };
        if (!ItemStack.isSameItemSameComponents(tool, m.getMainHandItem())) m.setItemSlot(EquipmentSlot.MAINHAND, tool);
    }

    /** A copy of the best weapon in her bag (a sword before an axe), or bare hands. */
    private ItemStack bestWeapon() {
        ItemStack axe = ItemStack.EMPTY;
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack s = bag.getItem(i);
            if (s.getItem() instanceof SwordItem) return s.copyWithCount(1);
            if (axe.isEmpty() && s.getItem() instanceof AxeItem) axe = s.copyWithCount(1);
        }
        return axe;
    }

    // ------------------------------------------------------------------ her bag

    /** Puts something in her bag; whatever doesn't fit is dropped at her feet. */
    public void store(ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack rest = bag.addItem(stack);
        if (!rest.isEmpty()) m.spawnAtLocation(rest);
    }

    /** Takes one item matching the test from her bag, or EMPTY. */
    ItemStack takeFromBag(Predicate<ItemStack> test) {
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack s = bag.getItem(i);
            if (!s.isEmpty() && test.test(s)) {
                ItemStack one = s.split(1);
                bag.setChanged();
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    boolean bagHas(Predicate<ItemStack> test) {
        for (int i = 0; i < bag.getContainerSize(); i++) if (!bag.getItem(i).isEmpty() && test.test(bag.getItem(i))) return true;
        return false;
    }

    boolean bagFull() {
        for (int i = 0; i < bag.getContainerSize(); i++) if (bag.getItem(i).isEmpty()) return false;
        return true;
    }

    // ------------------------------------------------------------------ saving

    public void save(CompoundTag tag) {
        tag.putInt("Job", job.ordinal());
        if (employer != null) tag.putUUID("Employer", employer);
        tag.putString("EmployerName", employerName);
        if (lastEmployer != null) tag.putUUID("LastEmployer", lastEmployer);
        tag.putInt("ShiftLeft", shiftLeft);
        if (workPos != null) tag.putLong("WorkPos", workPos.asLong());
        tag.putInt("WorkDir", workDir.get2DDataValue());
        tag.putInt("MineStep", mineStep);
        tag.put("Bag", bag.createTag(m.registryAccess()));
    }

    public void load(CompoundTag tag) {
        job = Job.byId(tag.getInt("Job"));
        employer = tag.hasUUID("Employer") ? tag.getUUID("Employer") : null;
        employerName = tag.getString("EmployerName");
        lastEmployer = tag.hasUUID("LastEmployer") ? tag.getUUID("LastEmployer") : null;
        shiftLeft = tag.getInt("ShiftLeft");
        workPos = tag.contains("WorkPos") ? BlockPos.of(tag.getLong("WorkPos")) : null;
        workDir = Direction.from2DDataValue(tag.getInt("WorkDir"));
        mineStep = tag.getInt("MineStep");
        bag.fromTag(tag.getList("Bag", Tag.TAG_COMPOUND), m.registryAccess());
    }
}

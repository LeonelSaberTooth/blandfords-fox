package net.maidens.entity;

import net.maidens.network.MaidenAction;
import net.maidens.network.MaidenMenu;
import net.maidens.world.Marriages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A maiden: lives in her house, trades like a farmer villager, and gets to know each player separately.
 * Chatting, joking, complimenting, flirting, gifts and trades change how much she likes you (0 to 100);
 * at 100, giving her the gift her personality dreams of is a proposal. She can be hired for a day
 * (5 emeralds) to farm, guard you, cut wood, mine or fish; married, she works for free for as long as you like
 * and trades at half price. What she gathers goes in her bag.
 */
public class Maiden extends AbstractVillager {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.INT);

    public static final int SKINS = 6;
    /** Emeralds for one day's work. */
    public static final int RENT = 5;
    /** A day's work, in ticks. */
    public static final int SHIFT = 24000;
    /** Trading experience needed to leave each level (novice to expert), as for villagers. */
    private static final int[] NEXT_LEVEL = {0, 10, 70, 150, 250};

    private boolean initialised;
    private Personality personality = Personality.SWEET;
    @Nullable private BlockPos home;
    private final Map<UUID, Bond> bonds = new HashMap<>();
    @Nullable private UUID spouse;
    private String spouseName = "";

    private Job job = Job.NONE;
    @Nullable private UUID employer;
    private String employerName = "";
    @Nullable private UUID lastEmployer;
    private int shiftLeft;
    @Nullable BlockPos workPos;
    Direction workDir = Direction.NORTH;
    int mineStep;
    final SimpleContainer bag = new SimpleContainer(27);

    private int tradeLevel = 1, tradeXp;
    private long lastRestockDay = -1;

    @Nullable private UUID talker;
    private int talkTicks;

    public Maiden(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation nav) nav.setCanOpenDoors(true);
        setCanPickUpLoot(true);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
    }

    public int getSkin() {
        return Math.floorMod(entityData.get(SKIN), SKINS);
    }

    public void setSkin(int skin) {
        entityData.set(SKIN, Math.floorMod(skin, SKINS));
    }

    public Personality getPersonality() {
        return personality;
    }

    public void setPersonality(Personality p) {
        personality = p;
    }

    public Job getJob() {
        return job;
    }

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    public SimpleContainer getBag() {
        return bag;
    }

    public boolean isGuarding() {
        return job == Job.GUARD;
    }

    @Nullable
    public Player getEmployerPlayer() {
        return employer == null ? null : level().getPlayerByUUID(employer);
    }

    @Nullable
    Player getTalker() {
        return talkTicks > 0 && talker != null ? level().getPlayerByUUID(talker) : null;
    }

    public String name() {
        return getName().getString();
    }

    // ------------------------------------------------------------------ AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(1, new MaidenGoals.Talk(this));
        goalSelector.addGoal(2, new MaidenGoals.Flee(this));
        goalSelector.addGoal(2, new MaidenGoals.Fight(this));
        goalSelector.addGoal(3, new MaidenGoals.FollowEmployer(this));
        goalSelector.addGoal(4, new WorkGoal(this));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(6, new OpenDoorGoal(this, true));
        goalSelector.addGoal(7, new MaidenGoals.Stroll(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new MaidenGoals.Retaliate(this));
        targetSelector.addGoal(2, new MaidenGoals.DefendEmployer(this));
        targetSelector.addGoal(3, new MaidenGoals.HuntMonsters(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        ensureInitialised();
        if (talkTicks > 0) talkTicks--;
        if (job != Job.NONE && shiftLeft > 0 && --shiftLeft == 0) endShift();
        if (tickCount % 20 == 0) {
            if (job == Job.GUARD) clearRestriction();
            else if (job.atWorkSpot() && workPos != null) restrictTo(workPos, job == Job.MINE ? 96 : 24);
            else if (home != null) restrictTo(home, 16);
            else clearRestriction();
            if (job == Job.GUARD && getTarget() == null) equipFor(Job.GUARD);
        }
        if (tickCount % 100 == 0) {
            if (getHealth() < getMaxHealth() && getTarget() == null) heal(1.0F);
            long day = level().getDayTime() / 24000L;
            if (offers != null && day != lastRestockDay) {
                lastRestockDay = day;
                for (MerchantOffer o : offers) o.resetUses();
            }
        }
    }

    /** Rolls her looks, personality and name the first time she's in the world (also called by the tests). */
    public void ensureInitialised() {
        if (initialised || level().isClientSide) return;
        initialised = true;
        var r = getRandom();
        setSkin(r.nextInt(SKINS));
        personality = Personality.byId(r.nextInt(Personality.values().length));
        if (!hasCustomName()) setCustomName(Component.literal(Lines.NAMES[r.nextInt(Lines.NAMES.length)]));
        if (home == null) home = blockPosition();
        setPersistenceRequired();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return job.atWorkSpot() && !stack.isEmpty() && bag.canAddItem(stack);
    }

    @Override
    protected void pickUpItem(ItemEntity item) {
        ItemStack stack = item.getItem();
        int before = stack.getCount();
        ItemStack rest = bag.addItem(stack.copy());
        int taken = before - rest.getCount();
        if (taken <= 0) return;
        onItemPickup(item);
        take(item, taken);
        if (rest.isEmpty()) item.discard();
        else item.setItem(rest);
    }

    /** Puts something in her bag; whatever doesn't fit is dropped at her feet. */
    void store(ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack rest = bag.addItem(stack);
        if (!rest.isEmpty()) spawnAtLocation(rest);
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

    private void equipFor(Job j) {
        ItemStack tool = switch (j) {
            case FARM -> new ItemStack(Items.IRON_HOE);
            case WOOD -> new ItemStack(Items.IRON_AXE);
            case MINE -> new ItemStack(Items.IRON_PICKAXE);
            case FISH -> new ItemStack(Items.FISHING_ROD);
            case GUARD -> bestWeapon();
            case NONE -> ItemStack.EMPTY;
        };
        if (!ItemStack.isSameItemSameComponents(tool, getMainHandItem())) setItemSlot(EquipmentSlot.MAINHAND, tool);
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

    // ------------------------------------------------------------------ talking to her

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.NAME_TAG) || held.getItem() instanceof SpawnEggItem || !isAlive() || isTrading()) {
            return super.mobInteract(player, hand);
        }
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            startTalking(sp);
            sendMenu(sp, greeting(sp));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private void startTalking(ServerPlayer player) {
        talker = player.getUUID();
        talkTicks = 200;
        getNavigation().stop();
    }

    Bond bond(Player player) {
        Bond b = bonds.computeIfAbsent(player.getUUID(), u -> new Bond());
        b.today(level().getDayTime() / 24000L);
        return b;
    }

    public int affection(Player player) {
        return bond(player).affection;
    }

    public void setAffection(Player player, int value) {
        Bond b = bond(player);
        b.affection = Math.max(0, Math.min(Bond.MAX, value));
    }

    public int getMineStep() {
        return mineStep;
    }

    public boolean isMarried() {
        return spouse != null;
    }

    public boolean isMarriedTo(Player player) {
        return spouse != null && spouse.equals(player.getUUID());
    }

    private String greeting(Player p) {
        String n = p.getName().getString();
        if (isMarriedTo(p)) return "Welcome home, my love.";
        int a = affection(p);
        if (a < 20) return "Oh, hello. Can I help you?";
        if (a < 50) return "Hi, " + n + "!";
        if (a < 80) return n + "! I was hoping you'd come by.";
        return n + "! There you are. I missed you.";
    }

    public void sendMenu(ServerPlayer p, String message) {
        int married = spouse == null ? 0 : isMarriedTo(p) ? 1 : 2;
        int emp = employer == null ? 0 : employer.equals(p.getUUID()) ? 1 : 2;
        boolean bagOk = isMarriedTo(p) || p.getUUID().equals(employer) || p.getUUID().equals(lastEmployer);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new MaidenMenu(getId(), name(), personality.label,
                affection(p), married, job.ordinal(), job == Job.NONE ? 0 : shiftLeft, emp, employerName, bagOk, isMarriedTo(p), message));
    }

    private void say(ServerPlayer p, String line) {
        p.sendSystemMessage(Component.literal("<" + name() + "> ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    private void particles(ParticleOptions type, int count) {
        if (level() instanceof ServerLevel sl) sl.sendParticles(type, getX(), getY() + getBbHeight() + 0.3, getZ(), count, 0.4, 0.3, 0.4, 0.02);
    }

    /** A button from her menu. Everything a player can do with her goes through here. */
    public void handleAction(ServerPlayer p, int action, int arg) {
        if (!isAlive() || p.distanceToSqr(this) > 10 * 10) return;
        startTalking(p);
        String reply = switch (action) {
            case MaidenAction.CHAT, MaidenAction.JOKE, MaidenAction.COMPLIMENT, MaidenAction.FLIRT -> talk(p, action);
            case MaidenAction.GIFT -> gift(p);
            case MaidenAction.TRADE -> { startTrading(p); yield null; }
            case MaidenAction.BAG -> openBag(p);
            case MaidenAction.HIRE -> hire(p, Job.byId(arg));
            case MaidenAction.STOP -> stopWork(p);
            default -> greeting(p);
        };
        if (reply != null) {
            say(p, reply);
            sendMenu(p, reply);
        }
    }

    private static final float[] BASE = {0.75F, 0.55F, 0.65F, 0.45F};
    private static final int[] GOOD = {3, 4, 4, 6};
    private static final int[] BAD = {1, 3, 2, 5};

    private String talk(ServerPlayer p, int kind) {
        String n = p.getName().getString();
        Bond b = bond(p);
        var r = getRandom();
        if (isMarriedTo(p)) {
            b.add(1);
            particles(ParticleTypes.HEART, 3);
            return Lines.pick(Lines.SPOUSE, r, n);
        }
        if (kind == MaidenAction.FLIRT && spouse != null) return Lines.pick(Lines.MARRIED_ELSEWHERE, r, n);
        int count = switch (kind) {
            case MaidenAction.CHAT -> b.chats++;
            case MaidenAction.JOKE -> b.jokes++;
            case MaidenAction.COMPLIMENT -> b.compliments++;
            default -> b.flirts++;
        };
        if (count >= 3) return Lines.pick(Lines.TIRED, r, n);
        Personality pe = personality;
        float mod = switch (kind) {
            case MaidenAction.CHAT -> pe.chatMod;
            case MaidenAction.JOKE -> pe.jokeMod;
            case MaidenAction.COMPLIMENT -> pe.complimentMod;
            default -> pe.flirtMod;
        };
        float chance = BASE[kind] + mod + b.affection * 0.004F;
        if (kind == MaidenAction.FLIRT && b.affection < 20) chance -= 0.3F;
        chance = Math.max(0.05F, Math.min(0.95F, chance));
        boolean ok = r.nextFloat() < chance;
        int change = ok ? Math.max(1, GOOD[kind] / (1 + count)) : -BAD[kind];
        b.add(change);
        particles(ok ? ParticleTypes.HEART : ParticleTypes.ANGRY_VILLAGER, ok ? 3 : 2);
        String[] lines = switch (kind) {
            case MaidenAction.CHAT -> ok ? pe.chatGood : pe.chatBad;
            case MaidenAction.JOKE -> ok ? pe.jokeGood : pe.jokeBad;
            case MaidenAction.COMPLIMENT -> ok ? pe.complimentGood : pe.complimentBad;
            default -> ok ? pe.flirtGood : pe.flirtBad;
        };
        String line = Lines.pick(lines, r, n);
        // once she's fond of you, she sometimes lets slip what she'd love to be proposed with
        if (ok && b.affection >= 70 && r.nextFloat() < 0.35F) line += " " + Lines.pick(pe.hints, r, n);
        return line;
    }

    private String gift(ServerPlayer p) {
        String n = p.getName().getString();
        ItemStack held = p.getMainHandItem();
        var r = getRandom();
        if (held.isEmpty()) return "You're not holding anything, " + n + ".";
        if (held.is(personality.proposal)) {
            String answer = propose(p, held);
            if (answer != null) return answer;
        }
        Bond b = bond(p);
        String key = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (b.gifts >= 5) return "You've given me so much today already, " + n + ". Save some for tomorrow!";
        if (b.giftsToday.contains(key)) return "You already gave me one of those today.";
        int taste = personality.taste(held);
        int change = switch (taste) {
            case 2 -> 10;
            case 1 -> 5;
            case -1 -> -5;
            default -> Personality.VALUABLE.contains(held.getItem()) ? 3 : 1;
        };
        if (change > 0) change = Math.max(1, change * (5 - b.gifts) / 5);
        if (!p.getAbilities().instabuild) held.shrink(1);
        b.add(change);
        b.gifts++;
        b.giftsToday.add(key);
        particles(change > 0 ? ParticleTypes.HEART : ParticleTypes.ANGRY_VILLAGER, taste == 2 ? 6 : 2);
        return Lines.pick(switch (taste) {
            case 2 -> personality.giftLove;
            case 1 -> personality.giftLike;
            case -1 -> personality.giftDislike;
            default -> personality.giftNeutral;
        }, r, n);
    }

    /** The held item is the one she dreams of. Returns her answer, or null to treat it as an ordinary gift. */
    @Nullable
    private String propose(ServerPlayer p, ItemStack ring) {
        String n = p.getName().getString();
        if (isMarriedTo(p)) return "We're already married, silly! But thank you.";
        if (spouse != null) return Lines.pick(Lines.MARRIED_ELSEWHERE, getRandom(), n);
        Marriages m = Marriages.get(p.server);
        UUID theirs = m.spouse(p.getUUID());
        if (theirs != null && !theirs.equals(getUUID())) return "Aren't you already married, " + n + "?";
        Bond b = bond(p);
        if (b.affection < Bond.MAX) {
            return "Oh... it's beautiful, " + n + ". But I'm not ready for that. Not yet.";
        }
        if (!p.getAbilities().instabuild) ring.shrink(1);
        spouse = p.getUUID();
        spouseName = n;
        m.marry(p.getUUID(), getUUID());
        if (p.getUUID().equals(employer)) shiftLeft = -1;
        particles(ParticleTypes.HEART, 20);
        level().playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, getSoundSource(), 1.0F, 1.0F);
        return "Yes! Yes, " + n + ", I'll marry you! From now on, what's mine is yours.";
    }

    // ------------------------------------------------------------------ trading (a farmer's trades)

    private void startTrading(ServerPlayer p) {
        MerchantOffers list = getOffers();
        for (MerchantOffer o : list) {
            o.resetSpecialPriceDiff();
            if (isMarriedTo(p)) o.addToSpecialPriceDiff(-Math.max(1, o.getBaseCostA().getCount() / 2));
        }
        setTradingPlayer(p);
        openTradingScreen(p, getDisplayName(), tradeLevel);
    }

    @Override
    protected void updateTrades() {
        addTrades(1);
    }

    private void addTrades(int level) {
        var byLevel = VillagerTrades.TRADES.get(VillagerProfession.FARMER);
        if (byLevel == null) return;
        VillagerTrades.ItemListing[] listings = byLevel.get(level);
        if (listings != null) addOffersFromItemListings(getOffers(), listings, 2);
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        tradeXp += offer.getXp();
        if (offer.shouldRewardExp()) {
            level().addFreshEntity(new ExperienceOrb(level(), getX(), getY() + 0.5, getZ(), 3 + getRandom().nextInt(4)));
        }
        if (getTradingPlayer() instanceof ServerPlayer p) {
            Bond b = bond(p);
            if (b.tradeHearts < 5) {
                b.tradeHearts++;
                b.add(1);
            }
        }
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        super.setTradingPlayer(player);
        if (player == null && !level().isClientSide) {
            boolean up = false;
            while (tradeLevel < 5 && tradeXp >= NEXT_LEVEL[tradeLevel]) {
                tradeLevel++;
                addTrades(tradeLevel);
                up = true;
            }
            if (up) particles(ParticleTypes.HAPPY_VILLAGER, 8);
        }
    }

    public int getVillagerXp() {
        return tradeXp;
    }

    public boolean showProgressBar() {
        return true;
    }

    public boolean canRestock() {
        return true;
    }

    public int getTradeLevel() {
        return tradeLevel;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.EXPERIENCE_ORB_PICKUP;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean yes) {
        return yes ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.CHICKEN_EGG;
    }

    // ------------------------------------------------------------------ work

    private String openBag(ServerPlayer p) {
        boolean ok = isMarriedTo(p) || p.getUUID().equals(employer) || p.getUUID().equals(lastEmployer);
        if (!ok) return "That's my bag, " + p.getName().getString() + "! Hire me first.";
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, bag), Component.literal(name() + "'s bag")));
        return null;
    }

    private String hire(ServerPlayer p, Job newJob) {
        String n = p.getName().getString();
        if (newJob == Job.NONE) return stopWork(p);
        boolean mine = p.getUUID().equals(employer);
        boolean wife = isMarriedTo(p);
        if (employer != null && !mine) return "Sorry, " + n + ", I'm working for " + employerName + " today.";
        if (!mine && !wife && !p.getAbilities().instabuild) {
            if (p.getInventory().countItem(Items.EMERALD) < RENT) return "A day's work is " + RENT + " emeralds, " + n + ".";
            ContainerHelper.clearOrCountMatchingItems(p.getInventory(), s -> s.is(Items.EMERALD), RENT, false);
        }
        if (!mine) shiftLeft = wife ? -1 : SHIFT;
        employer = p.getUUID();
        employerName = n;
        lastEmployer = p.getUUID();
        startJob(newJob, p.getDirection());
        return switch (newJob) {
            case FARM -> "I'll tend the crops around my house and keep the harvest in my bag.";
            case GUARD -> "I've got your back, " + n + ". Put a sword in my bag and I'll use it.";
            case WOOD -> "I'll chop the trees around here and replant saplings if I have some.";
            case MINE -> "I'll dig a staircase down, " + workDir.getName() + " from here, then tunnel. Torches in my bag and I'll light it.";
            case FISH -> "I'll fish at the nearest water. Wish me luck!";
            case NONE -> "";
        } + (wife ? " For you, it's free, my love." : "");
    }

    /** Also used by the self test. */
    public void startJob(Job newJob, Direction facing) {
        job = newJob;
        workPos = newJob == Job.FARM && home != null ? home : blockPosition();
        workDir = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        mineStep = 0;
        setTarget(null);
        getNavigation().stop();
        equipFor(newJob);
    }

    private String stopWork(ServerPlayer p) {
        if (job == Job.NONE) return "I'm not working right now.";
        if (!p.getUUID().equals(employer) && !isMarriedTo(p)) return "You're not the one who hired me.";
        stop();
        return "Alright, I'll stop. Come see my bag for what I gathered.";
    }

    void stop() {
        job = Job.NONE;
        employer = null;
        employerName = "";
        shiftLeft = 0;
        setTarget(null);
        equipFor(Job.NONE);
    }

    /** Called by her work when she has to give up (lava ahead, a full bag...). */
    void giveUp(String why) {
        Player p = getEmployerPlayer();
        if (p instanceof ServerPlayer sp) say(sp, why);
        stop();
    }

    private void endShift() {
        Player p = getEmployerPlayer();
        if (p instanceof ServerPlayer sp) say(sp, "My shift's over! Come by my house to collect what I gathered.");
        stop();
    }

    /** For the self test: puts her to work without the menu or payment. */
    public void hireForTest(Job j, @Nullable Player who) {
        if (who != null) {
            employer = who.getUUID();
            employerName = who.getName().getString();
            lastEmployer = employer;
        }
        shiftLeft = SHIFT;
        startJob(j, Direction.NORTH);
    }

    // ------------------------------------------------------------------ the rest of her life

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            Containers.dropContents(level(), this, bag);
            if (spouse != null && level().getServer() != null) {
                Marriages.get(level().getServer()).widow(getUUID());
                if (level().getPlayerByUUID(spouse) instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.literal(name() + ", your wife, has died.").withStyle(ChatFormatting.RED));
                }
            }
        }
        super.die(source);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("MaidenReady", initialised);
        tag.putInt("Skin", getSkin());
        tag.putInt("Personality", personality.ordinal());
        if (home != null) tag.putLong("Home", home.asLong());
        ListTag list = new ListTag();
        bonds.forEach((u, b) -> {
            CompoundTag t = b.save();
            t.putUUID("Player", u);
            list.add(t);
        });
        tag.put("Bonds", list);
        if (spouse != null) tag.putUUID("Spouse", spouse);
        tag.putString("SpouseName", spouseName);
        tag.putInt("Job", job.ordinal());
        if (employer != null) tag.putUUID("Employer", employer);
        tag.putString("EmployerName", employerName);
        if (lastEmployer != null) tag.putUUID("LastEmployer", lastEmployer);
        tag.putInt("ShiftLeft", shiftLeft);
        if (workPos != null) tag.putLong("WorkPos", workPos.asLong());
        tag.putInt("WorkDir", workDir.get2DDataValue());
        tag.putInt("MineStep", mineStep);
        tag.put("Bag", bag.createTag(registryAccess()));
        tag.putInt("TradeLevel", tradeLevel);
        tag.putInt("TradeXp", tradeXp);
        tag.putLong("LastRestock", lastRestockDay);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        initialised = tag.getBoolean("MaidenReady");
        setSkin(tag.getInt("Skin"));
        personality = Personality.byId(tag.getInt("Personality"));
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        bonds.clear();
        ListTag list = tag.getList("Bonds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.hasUUID("Player")) bonds.put(t.getUUID("Player"), Bond.load(t));
        }
        spouse = tag.hasUUID("Spouse") ? tag.getUUID("Spouse") : null;
        spouseName = tag.getString("SpouseName");
        job = Job.byId(tag.getInt("Job"));
        employer = tag.hasUUID("Employer") ? tag.getUUID("Employer") : null;
        employerName = tag.getString("EmployerName");
        lastEmployer = tag.hasUUID("LastEmployer") ? tag.getUUID("LastEmployer") : null;
        shiftLeft = tag.getInt("ShiftLeft");
        workPos = tag.contains("WorkPos") ? BlockPos.of(tag.getLong("WorkPos")) : null;
        workDir = Direction.from2DDataValue(tag.getInt("WorkDir"));
        mineStep = tag.getInt("MineStep");
        bag.fromTag(tag.getList("Bag", Tag.TAG_COMPOUND), registryAccess());
        tradeLevel = Math.max(1, tag.getInt("TradeLevel"));
        tradeXp = tag.getInt("TradeXp");
        lastRestockDay = tag.getLong("LastRestock");
    }

    /** For the test command: a one-line summary of her. */
    public String describe(Player p) {
        return name() + " (" + personality.label + "), likes you " + affection(p) + "/" + Bond.MAX + ", dreams of: "
                + personality.proposal.getDescription().getString() + ", job: " + job.doing
                + (spouse != null ? ", married to " + spouseName : "") + ", trade level " + tradeLevel;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // her employer can't hurt her by accident while she guards
        if (source.getEntity() instanceof Player p && isGuarding() && p.getUUID().equals(employer)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        swing(InteractionHand.MAIN_HAND);
        return super.doHurtTarget(target);
    }

    boolean canWorkNow() {
        return !isTrading() && getTalker() == null;
    }

}

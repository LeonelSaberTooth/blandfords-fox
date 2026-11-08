package net.maidens.maiden;

import net.maidens.maiden.ai.MaidenAi;
import net.maidens.maiden.home.Residence;
import net.maidens.maiden.social.Dialogue;
import net.maidens.maiden.social.Relationships;
import net.maidens.maiden.trade.MaidenTrading;
import net.maidens.maiden.work.Employment;
import net.maidens.maiden.work.Job;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A maiden. This class is only the entity: her looks, name, personality and home, wiring her AI, and saving.
 * What she does lives in her parts:
 * <ul>
 *   <li>{@link Relationships}: affection per player, talking, gifts, marriage</li>
 *   <li>{@link Employment}: being hired, her jobs and her bag</li>
 *   <li>{@link MaidenTrading}: her trades and trade levels</li>
 *   <li>{@link Residence}: her village, bed, workstation and profession</li>
 *   <li>{@link MaidenMenu}: the right-click menu and its buttons</li>
 *   <li>{@link MaidenAi}: all her goals, in priority order</li>
 * </ul>
 */
public class Maiden extends AbstractVillager {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.INT);
    public static final int SKINS = 6;

    private final Relationships relationships = new Relationships(this);
    private final Employment employment = new Employment(this);
    private final MaidenTrading trading = new MaidenTrading(this);
    private final Residence residence = new Residence(this);

    private boolean initialised;
    private Personality personality = Personality.SWEET;
    @Nullable private BlockPos home;
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

    // ------------------------------------------------------------------ her parts

    public Relationships relationships() {
        return relationships;
    }

    public Employment employment() {
        return employment;
    }

    public MaidenTrading trading() {
        return trading;
    }

    public Residence residence() {
        return residence;
    }

    // ------------------------------------------------------------------ who she is

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

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home;
    }

    public String name() {
        return getName().getString();
    }

    /** Her profession from a village workstation, or null. */
    @Nullable
    public VillagerProfession profession() {
        return residence.profession();
    }

    /** "Sweet" or "Sweet · Librarian", for her menu. */
    public String title() {
        String job = residence.professionName();
        return job.isEmpty() ? personality.label : personality.label + " \u00b7 " + job;
    }

    /** Rolls her looks, personality and name the first time she's in the world (also called by the tests). */
    public void ensureInitialised() {
        if (initialised || level().isClientSide) return;
        initialised = true;
        var r = getRandom();
        setSkin(r.nextInt(SKINS));
        personality = Personality.byId(r.nextInt(Personality.values().length));
        if (!hasCustomName()) setCustomName(Component.literal(Dialogue.NAMES[r.nextInt(Dialogue.NAMES.length)]));
        if (home == null) home = blockPosition();
        setPersistenceRequired();
    }

    // ------------------------------------------------------------------ state other parts ask about

    /** Not talking to anyone and not trading: free to work or wander. */
    public boolean isFree() {
        return !isTrading() && getTalker() == null;
    }

    /** Fights monsters: a hired guard. */
    public boolean isFighter() {
        return employment.isGuarding();
    }

    @Nullable
    public Player getTalker() {
        return talkTicks > 0 && talker != null ? level().getPlayerByUUID(talker) : null;
    }

    public void startTalking(Player player) {
        talker = player.getUUID();
        talkTicks = 200;
        getNavigation().stop();
    }

    /** A player changed how she feels about them (hook for village gossip). */
    public void onAffectionChanged(Player player, int amount) {
    }

    /** A player traded with her (hook for village citizenship). */
    public void onTraded(Player player) {
    }

    // ------------------------------------------------------------------ small helpers for her parts

    /** Says a line to one player, in chat. */
    public void say(ServerPlayer p, String line) {
        p.sendSystemMessage(Component.literal("<" + name() + "> ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    /** Particles above her head. */
    public void burst(ParticleOptions type, int count) {
        if (level() instanceof ServerLevel sl) sl.sendParticles(type, getX(), getY() + getBbHeight() + 0.3, getZ(), count, 0.4, 0.3, 0.4, 0.02);
    }

    public void addOffers(VillagerTrades.ItemListing[] listings, int count) {
        addOffersFromItemListings(getOffers(), listings, count);
    }

    @Nullable
    public MerchantOffers existingOffers() {
        return offers;
    }

    public void clearOffers() {
        offers = null;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    protected void registerGoals() {
        MaidenAi.register(this, goalSelector, targetSelector);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        ensureInitialised();
        if (talkTicks > 0) talkTicks--;
        employment.tick();
        trading.tick();
        residence.tick();
        if (tickCount % 20 == 0) updateRestriction();
        if (tickCount % 100 == 0 && getHealth() < getMaxHealth() && getTarget() == null) heal(1.0F);
    }

    /** Where she's allowed to wander: around her work spot when working, nowhere in particular when guarding, else home. */
    private void updateRestriction() {
        Job job = employment.job();
        if (job == Job.GUARD) clearRestriction();
        else if (job.atWorkSpot() && employment.workPos() != null) restrictTo(employment.workPos(), job == Job.MINE ? 96 : 24);
        else if (home != null) restrictTo(home, 16);
        else clearRestriction();
    }

    // ------------------------------------------------------------------ vanilla hooks

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.NAME_TAG) || held.getItem() instanceof SpawnEggItem || !isAlive() || isTrading()) {
            return super.mobInteract(player, hand);
        }
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) MaidenMenu.open(this, sp);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void updateTrades() {
        trading.fillFirstLevel();
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        trading.onTrade(offer);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        super.setTradingPlayer(player);
        if (player == null && !level().isClientSide) trading.onClosed();
    }

    public int getVillagerXp() {
        return trading.xp();
    }

    public boolean showProgressBar() {
        return true;
    }

    public boolean canRestock() {
        return true;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.EXPERIENCE_ORB_PICKUP;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean yes) {
        return yes ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.CHICKEN_EGG;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return employment.job().atWorkSpot() && !stack.isEmpty() && employment.bag().canAddItem(stack);
    }

    @Override
    protected void pickUpItem(ItemEntity item) {
        ItemStack stack = item.getItem();
        int before = stack.getCount();
        ItemStack rest = employment.bag().addItem(stack.copy());
        int taken = before - rest.getCount();
        if (taken <= 0) return;
        onItemPickup(item);
        take(item, taken);
        if (rest.isEmpty()) item.discard();
        else item.setItem(rest);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // whoever hired her as a guard can't hurt her by accident
        if (source.getEntity() instanceof Player p && employment.isGuarding() && employment.employerFor(p) == 1) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        swing(InteractionHand.MAIN_HAND);
        return super.doHurtTarget(target);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            Containers.dropContents(level(), this, employment.bag());
            UUID spouse = relationships.spouse();
            relationships.onDeath();
            residence.release();
            if (spouse != null && level().getPlayerByUUID(spouse) instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal(name() + ", your wife, has died.").withStyle(ChatFormatting.RED));
            }
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
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

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("MaidenReady", initialised);
        tag.putInt("Skin", getSkin());
        tag.putInt("Personality", personality.ordinal());
        if (home != null) tag.putLong("Home", home.asLong());
        relationships.save(tag);
        employment.save(tag);
        trading.save(tag);
        residence.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        initialised = tag.getBoolean("MaidenReady");
        setSkin(tag.getInt("Skin"));
        personality = Personality.byId(tag.getInt("Personality"));
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        relationships.load(tag);
        employment.load(tag);
        trading.load(tag);
        residence.load(tag);
    }

    /** For the test command: a one-line summary of her. */
    public String describe(Player p) {
        return name() + " (" + personality.label + "), likes you " + relationships.affection(p) + "/100, dreams of: "
                + personality.proposal.getDescription().getString() + ", job: " + employment.job().doing
                + (relationships.isMarried() ? ", married to " + relationships.spouseName() : "")
                + ", " + trading.profession() + " level " + trading.level();
    }
}

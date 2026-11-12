package net.maidens.maiden;

import net.maidens.maiden.ai.MaidenAi;
import net.maidens.maiden.home.Residence;
import net.maidens.maiden.home.Schedule;
import net.maidens.maiden.social.Wishes;
import net.maidens.maiden.social.Dialogue;
import net.maidens.maiden.social.Relationships;
import net.maidens.maiden.trade.MaidenTrading;
import net.maidens.maiden.work.Employment;
import net.maidens.maiden.work.Job;
import net.maidens.maiden.war.Raider;
import net.maidens.politics.WarParties;
import net.maidens.village.Royals;
import net.maidens.village.VillageSocial;
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
import net.minecraft.core.particles.ParticleTypes;
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
 *   <li>{@link Residence}: her village, bed, workstation and profession; {@link Schedule}: her day</li>
 *   <li>{@link Wishes}: her request on the village's requests board</li>
 *   <li>{@link MaidenMenu}: the right-click menu and its buttons</li>
 *   <li>{@link MaidenAi}: all her goals, in priority order</li>
 * </ul>
 */
public class Maiden extends AbstractVillager {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.INT);
    /** A village royal (wears a crown; see {@link net.maidens.village.Royals}). */
    private static final EntityDataAccessor<Boolean> ROYAL = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.BOOLEAN);
    /** Woman, man, old woman, old man (children are the vanilla "baby" of any of them). */
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.INT);
    /** Her looks, each picked separately (skin tone is SKIN): clothes, eyes, hair. */
    private static final EntityDataAccessor<Integer> OUTFIT = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EYES = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HAIR = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.INT);
    /** In her casual clothes (at home, indoors). */
    private static final EntityDataAccessor<Boolean> CASUAL = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.BOOLEAN);
    /** The villager profession whose hat she wears while working ("" for none). */
    private static final EntityDataAccessor<String> HAT = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.STRING);
    /** Freckles (some grown women have them). */
    private static final EntityDataAccessor<Boolean> FRECKLES = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.BOOLEAN);
    /** Named with a "model" name tag: the proportions saved in the model viewer, and the runway walk. */
    private static final EntityDataAccessor<Boolean> MODEL_POSE = SynchedEntityData.defineId(Maiden.class, EntityDataSerializers.BOOLEAN);
    /** Entity events: she blushes (her hearts went up), she smiles (introducing herself). */
    public static final byte EVENT_BLUSH = 80, EVENT_SMILE = 81;
    public static final int SKINS = 6;
    /** How many of each part of her looks there are (the layers tools/make_layers.py makes). */
    public static final int TONES = 5, OUTFITS = 21, EYE_COLOURS = 8, HAIRS = 21;
    /** How long a child takes to grow up (ticks): three days. */
    public static final int CHILDHOOD = 3 * 24000;

    public enum Kind {
        WOMAN(false, false), MAN(true, false), OLD_WOMAN(false, true), OLD_MAN(true, true);

        public final boolean male, old;

        Kind(boolean male, boolean old) {
            this.male = male;
            this.old = old;
        }

        public static Kind of(boolean male, boolean old) {
            for (Kind k : values()) if (k.male == male && k.old == old) return k;
            return WOMAN;
        }
    }

    /** Married to another villager (not a player): who, and her (or his) name. */
    @Nullable private UUID spouse;
    private String spouseName = "";
    /** Marrying another villager: 0 not decided yet, 1 stays single, 2 looking for someone (or married). */
    private int marriageState;
    /** A player she's walking with (the menu's Follow me), or null. */
    @Nullable private UUID follow;

    private final Relationships relationships = new Relationships(this);
    private final Employment employment = new Employment(this);
    private final MaidenTrading trading = new MaidenTrading(this);
    private final Residence residence = new Residence(this);
    private final Wishes wishes = new Wishes(this);
    private final Raider raider = new Raider(this);
    private final net.maidens.maiden.work.VillageWork villageWork = new net.maidens.maiden.work.VillageWork(this);
    /** Fighting for her village in a raid (checked every 2 s). */
    private boolean defending;

    private boolean initialised;
    private Personality personality = Personality.SWEET;
    @Nullable private BlockPos home;
    @Nullable private UUID talker;
    private int talkTicks;

    public Maiden(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation nav) nav.setCanOpenDoors(true);
        // what she holds and wears are copies of things in her bag (which drops in full when she dies)
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST}) {
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.42)
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

    public Wishes wishes() {
        return wishes;
    }

    /** Her job for her village (in work hours). */
    public net.maidens.maiden.work.VillageWork villageWork() {
        return villageWork;
    }

    /** Set when she was sent to raid another village. */
    public Raider raider() {
        return raider;
    }

    // ------------------------------------------------------------------ who she is

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
        builder.define(ROYAL, false);
        builder.define(KIND, 0);
        builder.define(OUTFIT, 0);
        builder.define(EYES, 0);
        builder.define(HAIR, 0);
        builder.define(CASUAL, false);
        builder.define(HAT, "");
        builder.define(FRECKLES, false);
        builder.define(MODEL_POSE, false);
    }

    public int getSkin() {
        return Math.floorMod(entityData.get(SKIN), TONES);
    }

    public void setSkin(int skin) {
        entityData.set(SKIN, Math.floorMod(skin, TONES));
    }

    public boolean isRoyal() {
        return entityData.get(ROYAL);
    }

    public int outfit() {
        return Math.floorMod(entityData.get(OUTFIT), OUTFITS);
    }

    public int eyes() {
        return Math.floorMod(entityData.get(EYES), EYE_COLOURS);
    }

    public int hair() {
        return Math.floorMod(entityData.get(HAIR), HAIRS);
    }

    /** Her looks: skin tone, clothes, eyes, hair (each one of those we have: see tools/make_layers.py). */
    public void setLooks(int tone, int outfit, int eyes, int hair) {
        setSkin(tone);
        entityData.set(OUTFIT, Math.floorMod(outfit, OUTFITS));
        entityData.set(EYES, Math.floorMod(eyes, EYE_COLOURS));
        entityData.set(HAIR, Math.floorMod(hair, HAIRS));
    }

    public Kind kind() {
        int k = entityData.get(KIND);
        return k >= 0 && k < Kind.values().length ? Kind.values()[k] : Kind.WOMAN;
    }

    /** Sets what she is (and gives a man a man's name). */
    public void setKind(Kind k) {
        boolean wasMale = kind().male;
        entityData.set(KIND, k.ordinal());
        if (k.male != wasMale) setCustomName(Component.literal(randomName(getRandom(), k.male)));
    }

    public boolean isMale() {
        return kind().male;
    }

    public boolean isOld() {
        return kind().old;
    }

    /** A child (vanilla's baby: she grows up on her own). */
    public boolean isChild() {
        return isBaby();
    }

    public void makeChild() {
        setAge(-CHILDHOOD);
    }

    public boolean isCasual() {
        return entityData.get(CASUAL);
    }

    /** A grown woman (the base model and her own looks; men, the old and children are drawn as before). */
    public boolean isWoman() {
        return kind() == Kind.WOMAN && !isBaby();
    }

    public boolean hasFreckles() {
        return entityData.get(FRECKLES);
    }

    /** For the tests. */
    public void setModelPose(boolean on) {
        entityData.set(MODEL_POSE, on);
    }

    public void setFreckles(boolean on) {
        entityData.set(FRECKLES, on);
    }

    public boolean isModelPose() {
        return entityData.get(MODEL_POSE) && isWoman();
    }

    // ------------------------------------------------------------------ her face for a moment (client side)

    private int blushUntil = -1, smileUntil = -1;

    /** She blushes for 3 seconds: her hearts for someone just went up. */
    public void blush() {
        if (!level().isClientSide) level().broadcastEntityEvent(this, EVENT_BLUSH);
    }

    /** She smiles for a second: she's introducing herself. */
    public void smile() {
        if (!level().isClientSide) level().broadcastEntityEvent(this, EVENT_SMILE);
    }

    public boolean isBlushing() {
        return tickCount < blushUntil;
    }

    public boolean isSmiling() {
        return tickCount < smileUntil;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_BLUSH) blushUntil = tickCount + 60;
        else if (id == EVENT_SMILE) smileUntil = tickCount + 20;
        else super.handleEntityEvent(id);
    }

    public String hat() {
        return entityData.get(HAT);
    }

    @Nullable
    public UUID spouse() {
        return spouse;
    }

    public void setSpouse(@Nullable UUID spouse) {
        this.spouse = spouse;
        if (spouse == null) spouseName = "";
    }

    public void setSpouse(Maiden other) {
        spouse = other.getUUID();
        spouseName = other.name();
    }

    /** Her villager husband's (or wife's) name: remembered, or looked up if she's nearby. */
    public String spouseName() {
        if (spouse == null) return "";
        if (spouseName.isEmpty() && level() instanceof ServerLevel sl && sl.getEntity(spouse) instanceof Maiden other) spouseName = other.name();
        return spouseName.isEmpty() ? "someone" : spouseName;
    }

    public int marriageState() {
        return marriageState;
    }

    public void setMarriageState(int state) {
        marriageState = state;
    }

    /** A village guard (keeps a gate or walks round the walls in work hours, fights monsters). */
    public boolean isGuard() {
        return villageWork.job() == net.maidens.maiden.work.Job.GUARD;
    }

    public void setGuard(boolean guard) {
        if (guard) villageWork.set(net.maidens.maiden.work.Job.GUARD, null, villageWork.post());
        else if (isGuard()) villageWork.clear();
    }

    public boolean isDefending() {
        return defending;
    }

    // ------------------------------------------------------------------ walking with a player

    public boolean isFollowing() {
        return follow != null;
    }

    @Nullable
    public Player followed() {
        return follow == null ? null : level().getPlayerByUUID(follow);
    }

    public boolean isFollowing(Player p) {
        return p.getUUID().equals(follow);
    }

    /** Hearts it takes for her to follow you round her village, and anywhere. */
    public static final int FOLLOW_VILLAGE = 20, FOLLOW_ANYWHERE = 80;

    /** Starts or stops following a player (the menu's Follow me). Returns what she says. */
    public String toggleFollow(ServerPlayer p) {
        String n = p.getName().getString();
        if (isFollowing(p)) {
            follow = null;
            getNavigation().stop();
            return "Alright, I'll stay here.";
        }
        if (isBaby()) return "I'm not allowed to wander off, " + n + "!";
        if (employment.isHired()) return "I'm working right now, " + n + ". Maybe after?";
        if (Schedule.bedtime(this)) return "It's late, " + n + ". I'm off to bed soon.";
        int a = relationships.affection(p);
        boolean wife = relationships.isMarriedTo(p);
        if (!wife && a < FOLLOW_VILLAGE) return "I don't know you that well yet, " + n + ".";
        if (!wife && a < FOLLOW_ANYWHERE && !inVillageArea(p)) return "I don't go outside the village with people I don't know well, " + n + ".";
        follow = p.getUUID();
        return wife || a >= FOLLOW_ANYWHERE ? "Lead the way, " + n + "! I'll go anywhere with you." : "Sure, I'll walk with you, " + n + ". Just around the village, though.";
    }

    /** Within her village (its walls, or its houses), with a little to spare. */
    public boolean inVillageArea(Entity e) {
        net.maidens.village.VillageRecord v = VillageSocial.villageOf(this);
        if (v == null) return false;
        net.minecraft.world.level.levelgen.structure.BoundingBox b = v.wallBox != null ? v.wallBox : v.box;
        return e.getX() >= b.minX() - 8 && e.getX() <= b.maxX() + 9 && e.getZ() >= b.minZ() - 8 && e.getZ() <= b.maxZ() + 9;
    }

    /** Twice a second: stops following someone who has gone, or gone too far for her, or at bedtime. */
    private void checkFollow() {
        if (follow == null) return;
        Player p = followed();
        if (p == null || !p.isAlive() || p.level() != level() || p.isSpectator()) {
            follow = null;
            return;
        }
        String why;
        int a = relationships.affection(p);
        boolean wife = relationships.isMarriedTo(p);
        if (Schedule.bedtime(this)) why = "It's getting late. I'm going home to bed. Goodnight!";
        else if (employment.isHired()) why = "";
        else if (!wife && a < FOLLOW_VILLAGE) why = "I'd rather not, actually.";
        else if (!wife && a < FOLLOW_ANYWHERE && !inVillageArea(p)) why = "This is as far as I go. I'll head back now!";
        else return;
        follow = null;
        getNavigation().stop();
        if (!why.isEmpty() && p instanceof ServerPlayer sp) say(sp, why);
    }

    private static String randomName(net.minecraft.util.RandomSource r, boolean male) {
        String[] names = male ? Dialogue.MALE_NAMES : Dialogue.NAMES;
        return names[r.nextInt(names.length)];
    }

    public void setRoyal(boolean royal) {
        entityData.set(ROYAL, royal);
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

    /** "Sweet", "Sweet · Librarian" or "Sweet · Royal", for her menu. */
    public String title() {
        String job = isRoyal() ? "Royal" : residence.professionName();
        return job.isEmpty() ? personality.label : personality.label + " \u00b7 " + job;
    }

    /** Rolls her looks, personality and name the first time she's in the world (also called by the tests). */
    public void ensureInitialised() {
        if (initialised || level().isClientSide) return;
        initialised = true;
        var r = getRandom();
        setLooks(r.nextInt(TONES), r.nextInt(OUTFITS), r.nextInt(EYE_COLOURS), r.nextInt(HAIRS));
        entityData.set(FRECKLES, r.nextFloat() < 0.25F);
        personality = Personality.byId(r.nextInt(Personality.values().length));
        if (!hasCustomName()) setCustomName(Component.literal(randomName(r, isMale())));
        if (home == null) home = blockPosition();
        setPersistenceRequired();
    }

    // ------------------------------------------------------------------ state other parts ask about

    /** Not talking to anyone and not trading: free to work or wander. */
    public boolean isFree() {
        return !isTrading() && getTalker() == null;
    }

    /** A village maiden nobody has claimed: not hired and not married. Only she does village chores and routine. */
    public boolean hasVillageDuties() {
        return residence.inVillage() && !employment.isHired() && !relationships.isMarried() && !isBaby() && !raider.active() && follow == null;
    }

    /** Fights monsters: a hired guard, or a village maiden defending her village in a raid. */
    public boolean isFighter() {
        return employment.isGuarding() || defending || raider.active() && !raider.surrendered() || isGuard() && hasVillageDuties();
    }

    /** A raid on her village: free village maidens (and guards) take up a sword; when it's over, they put it away. */
    private void updateDefending() {
        boolean raid = residence.inVillage() && (hasVillageDuties() || employment.isGuarding()) && level() instanceof ServerLevel sl
                && (WarParties.raidOn(residence.village()) || sl.getRaidAt(blockPosition()) != null && sl.getRaidAt(blockPosition()).isActive());
        if (raid == defending) return;
        defending = raid;
        if (employment.isHired()) return;
        if (raid) {
            if (isSleeping()) stopSleeping();
            if (getMainHandItem().isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        } else if (getMainHandItem().is(Items.IRON_SWORD) && !isGuard()) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            setTarget(null);
        }
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

    /** A player changed how she feels about them: her village hears about it. */
    public void onAffectionChanged(Player player, int amount) {
        if (amount > 0) blush();
        VillageSocial.onAffectionChanged(this, player, amount);
    }

    /** A player traded with her: counts towards citizenship of her village. */
    public void onTraded(Player player) {
        VillageSocial.onTraded(this, player);
    }

    /** She just married this player. */
    public void onMarried(Player player) {
        employment.onMarried(player);
        VillageSocial.onMarried(this, player);
    }

    // ------------------------------------------------------------------ small helpers for her parts

    /** Says a line to one player, in chat. */
    public void say(ServerPlayer p, String line) {
        p.sendSystemMessage(Component.literal("<" + name() + "> ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    /**
     * When she's been stuck a long way from where she needs to be (a cliff, a river, a path the pathfinder can't
     * plan), she finds her own way: puts her on a free standing spot within 3 blocks of it. Returns false if none.
     */
    public boolean arriveNear(BlockPos target) {
        // never in front of anyone: popping from place to place looks wrong (she walks, or does something else)
        if (level().getNearestPlayer(this, 32) != null) return false;
        return moveNear(target);
    }

    /** Moves her to a free spot next to target, seen or not (getting out of a wall, out of the way of a new wall). */
    public boolean moveNear(BlockPos target) {
        for (BlockPos p : BlockPos.withinManhattan(target, 3, 3, 3)) {
            if (p.equals(target) || p.above().equals(target)) continue;     // not in the spot she's about to fill
            if (level().getBlockState(p.below()).isFaceSturdy(level(), p.below(), net.minecraft.core.Direction.UP)
                    && level().getBlockState(p).getCollisionShape(level(), p).isEmpty()
                    && level().getBlockState(p.above()).getCollisionShape(level(), p.above()).isEmpty()
                    && level().getFluidState(p).isEmpty()) {
                getNavigation().stop();
                teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                return true;
            }
        }
        return false;
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
        // villagers don't keep their swing going (monsters and players do): without this her arm never moves
        updateSwingTime();
        super.aiStep();
        if (level().isClientSide) return;
        if (tickCount % 20 == 7) updateLooks();
        // anything that makes her move (fleeing, a friend, a raid) gets her out of bed first: never walking lying down
        if (isSleeping() && (!getNavigation().isDone() || getSleepingPos().map(b -> distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5) > 4.0).orElse(false))) {
            getNavigation().stop();
            stopSleeping();
        }
        ensureInitialised();
        if (talkTicks > 0) talkTicks--;
        employment.tick();
        trading.tick();
        residence.tick();
        wishes.tick();
        if (tickCount % 10 == 3) {
            Schedule.tick(this);
            checkFollow();
        }
        if (tickCount % 20 == 11) villageWork.dress();
        if (tickCount % 40 == 0) updateDefending();
        if (raider.active() && tickCount % 100 == 0 && WarParties.stale(this)) {   // her raid ended while she was away
            if (raider.resident()) raider.finish();
            else discard();
        }
        if (tickCount % 20 == 0) updateRestriction();
        if (tickCount % 100 == 0 && getHealth() < getMaxHealth() && getTarget() == null) heal(1.0F);
    }

    /** Where she's allowed to wander: around her work spot when working, nowhere in particular when guarding, else home. */
    private void updateRestriction() {
        Job job = employment.job();
        if (job == Job.GUARD || raider.active()) clearRestriction();      // guards follow, raiders go where the raid is
        else if (follow != null) clearRestriction();                      // where the player goes
        else if (Schedule.keeps(this) && VillageSocial.villageOf(this) instanceof net.maidens.village.VillageRecord v) {
            // her day: home, work, the market (a child playing and a guard walking round have the run of the village)
            Schedule.Activity a = Schedule.now(this);
            BlockPos at = a == Schedule.Activity.OUTSIDE ? null : Schedule.place(this, a);
            if (at != null) restrictTo(at, a == Schedule.Activity.WORK ? 16 : a == Schedule.Activity.MARKET ? 8 : 5);
            else restrictTo(v.centre(), Math.max(v.box.getXSpan(), v.box.getZSpan()) / 2 + 24);
        }
        else if (job.atWorkSpot() && employment.workPos() != null) restrictTo(employment.workPos(), job == Job.MINE ? 96 : 24);
        else if (home != null) restrictTo(home, 16);
        else clearRestriction();
    }

    // ------------------------------------------------------------------ vanilla hooks

    /**
     * A name tag that says "model" (the game names her with it before she sees the click): instead of being renamed,
     * a woman takes the proportions saved in the model viewer and walks the runway walk (again: back as she was). She
     * keeps her own name; the tag is used up as any name tag is.
     */
    @Override
    public void setCustomName(@Nullable Component name) {
        if (name != null && !level().isClientSide && hasCustomName() && isWoman() && isModelTag(name)) {
            entityData.set(MODEL_POSE, !entityData.get(MODEL_POSE));
            burst(ParticleTypes.HAPPY_VILLAGER, 6);
            return;
        }
        super.setCustomName(name);
    }

    private static boolean isModelTag(Component name) {
        return "model".equalsIgnoreCase(name.getString().trim());
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.NAME_TAG) || held.getItem() instanceof SpawnEggItem || !isAlive() || isTrading()) {
            return super.mobInteract(player, hand);
        }
        if (hand != InteractionHand.MAIN_HAND || raider.active()) return InteractionResult.PASS;
        if (isSleeping()) stopSleeping();
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

    /** Only at work does she pick things up (vanilla otherwise looks for items around her every tick). */
    @Override
    public boolean canPickUpLoot() {
        return employment != null && employment.job().atWorkSpot();
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
        // stuck in a block (one put down where she stood): she steps out instead of choking
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            if (!level().isClientSide) moveNear(blockPosition());
            return false;
        }
        // whoever hired her as a guard can't hurt her by accident
        if (source.getEntity() instanceof Player p && employment.isGuarding() && employment.employerFor(p) == 1) return false;
        if (!level().isClientSide && raider.active()) {
            if (source.getEntity() instanceof Player p) WarParties.onRaiderHurt(this, p);
            // a raider who has given up isn't fought any more by maidens (finishing her off is the player's choice)
            if (raider.surrendered() && source.getEntity() instanceof Maiden other) {
                if (other.getTarget() == this) other.setTarget(null);
                return false;
            }
            // a beaten raider gives up instead of dying
            float floor = getMaxHealth() * 0.3F;
            if (!raider.surrendered() && getHealth() - amount < floor) {
                boolean hurt = super.hurt(source, Math.max(0.0F, getHealth() - floor));
                raider.surrender();
                return hurt;
            }
        }
        // raiders knock a village's maidens down, they don't kill them
        if (!raider.active() && source.getEntity() instanceof Maiden r && r.raider().active() && getHealth() - amount < 2.0F) {
            amount = Math.max(0.0F, getHealth() - 2.0F);
            setTarget(null);
        }
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
            // her villager husband (or wife) is free to marry again
            if (this.spouse != null && level() instanceof ServerLevel sl && sl.getEntity(this.spouse) instanceof Maiden other
                    && getUUID().equals(other.spouse())) other.setSpouse((UUID) null);
            relationships.onDeath();
            Royals.onDeath(this);
            residence.release();
            if (spouse != null && level().getPlayerByUUID(spouse) instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal(name() + ", your wife, has died.").withStyle(ChatFormatting.RED));
            }
        }
        super.die(source);
    }

    /**
     * Once a second: what she wears. A resident keeping to her day (grown men and women only): casual clothes at home
     * and in bed, her trade's things at work, her usual clothes (a woman's dress) at the market. Hired, her job's
     * things while she works. Anyone else: casual clothes indoors (grown women). Children and the old don't change.
     */
    private void updateLooks() {
        String hat = "";
        boolean casual = false;
        boolean unarmoured = getItemBySlot(EquipmentSlot.CHEST).isEmpty() && getItemBySlot(EquipmentSlot.LEGS).isEmpty() && getItemBySlot(EquipmentSlot.FEET).isEmpty();
        if (Schedule.adult(this) && !isRoyal()) {
            Schedule.Activity a = Schedule.activity(this);
            if (employment.busyNow()) hat = villageWork.trade(employment.job());
            else if (a == Schedule.Activity.WORK && villageWork.onDuty()) hat = villageWork.trade(villageWork.job());
            else if (a == Schedule.Activity.HOME || a == Schedule.Activity.SLEEP) casual = true;
            else if (a == null) casual = kind() == Kind.WOMAN && !level().canSeeSky(blockPosition().above())
                    && level().getBrightness(net.minecraft.world.level.LightLayer.SKY, blockPosition().above()) < 14
                    && blockPosition().getY() > level().getSeaLevel() - 8;
            casual &= getTarget() == null && unarmoured;
        }
        if (!hat.equals(entityData.get(HAT))) entityData.set(HAT, hat);
        if (casual != isCasual()) entityData.set(CASUAL, casual);
    }

    @Override
    public void stopSleeping() {
        super.stopSleeping();
        // up and facing the way she'll walk (not still turned the way she lay)
        yBodyRot = yHeadRot = getYRot();
        yBodyRotO = yHeadRotO = getYRot();
        setPose(net.minecraft.world.entity.Pose.STANDING);
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
        tag.putBoolean("Royal", isRoyal());
        tag.putInt("Kind", kind().ordinal());
        if (spouse != null) tag.putUUID("Spouse", spouse);
        tag.putString("SpouseName", spouseName);
        tag.putInt("Marriage", marriageState);
        villageWork.save(tag);
        raider.save(tag);
        tag.putBoolean("MaidenReady", initialised);
        tag.putInt("Skin", getSkin());
        tag.putInt("Outfit", outfit());
        tag.putInt("Eyes", eyes());
        tag.putInt("Hair", hair());
        tag.putBoolean("Freckles", hasFreckles());
        tag.putBoolean("ModelPose", entityData.get(MODEL_POSE));
        tag.putInt("Personality", personality.ordinal());
        if (home != null) tag.putLong("Home", home.asLong());
        relationships.save(tag);
        employment.save(tag);
        trading.save(tag);
        residence.save(tag);
        wishes.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setRoyal(tag.getBoolean("Royal"));
        entityData.set(KIND, tag.getInt("Kind"));
        spouse = tag.hasUUID("Spouse") ? tag.getUUID("Spouse") : null;
        spouseName = tag.getString("SpouseName");
        marriageState = tag.getInt("Marriage");
        villageWork.load(tag);
        raider.load(tag);
        initialised = tag.getBoolean("MaidenReady");
        setSkin(tag.getInt("Skin"));
        // from before her looks were picked separately: all of them from her one skin
        int skin = getSkin();
        setLooks(skin, tag.contains("Outfit") ? tag.getInt("Outfit") : skin, tag.contains("Eyes") ? tag.getInt("Eyes") : skin, tag.contains("Hair") ? tag.getInt("Hair") : skin);
        // freckles: from before they were picked, a quarter of the women by their id (so it's always the same ones)
        entityData.set(FRECKLES, tag.contains("Freckles") ? tag.getBoolean("Freckles") : Math.floorMod(getUUID().hashCode(), 4) == 0);
        entityData.set(MODEL_POSE, tag.getBoolean("ModelPose"));
        // named "model" by a name tag before that worked: she gets a name again and the model's looks and walk
        if (isWoman() && getCustomName() != null && isModelTag(getCustomName())) {
            super.setCustomName(Component.literal(randomName(getRandom(), false)));
            entityData.set(MODEL_POSE, true);
        }
        personality = Personality.byId(tag.getInt("Personality"));
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        relationships.load(tag);
        employment.load(tag);
        trading.load(tag);
        residence.load(tag);
        wishes.load(tag);
    }

    /** For the self test: the goals running right now. */
    public String runningGoals() {
        StringBuilder s = new StringBuilder();
        for (var g : goalSelector.getAvailableGoals()) if (g.isRunning()) s.append(g.getGoal().getClass().getSimpleName()).append(' ');
        return s.toString().trim();
    }

    /** For the test command: a one-line summary of her. */
    public String describe(Player p) {
        return name() + " (" + personality.label + "), likes you " + relationships.affection(p) + "/100, dreams of: "
                + personality.proposal.getDescription().getString() + ", job: " + employment.job().doing
                + (relationships.isMarried() ? ", married to " + relationships.spouseName() : "")
                + ", " + trading.profession() + " level " + trading.level();
    }
}

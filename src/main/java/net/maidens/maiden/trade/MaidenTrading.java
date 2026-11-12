package net.maidens.maiden.trade;

import net.maidens.maiden.Maiden;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * A maiden's trades: the vanilla trades of her profession (farmer when she has no workstation), two new ones per
 * level, levelling up from Novice to Master like a villager, restocking once a day. Prices drop with how much
 * she likes you, and are halved for her husband.
 */
public final class MaidenTrading {
    /** Trading experience needed to leave each level, as for villagers. */
    private static final int[] NEXT_LEVEL = {0, 10, 70, 150, 250};

    private final Maiden m;
    private int level = 1, xp;
    private long lastRestockDay = -1;

    public MaidenTrading(Maiden m) {
        this.m = m;
    }

    public int level() {
        return level;
    }

    public int xp() {
        return xp;
    }

    /** The profession whose trades she offers (Phase 1 sets this from her workstation). */
    public VillagerProfession profession() {
        VillagerProfession p = m.profession();
        return p == null || p == VillagerProfession.NONE || p == VillagerProfession.NITWIT ? VillagerProfession.FARMER : p;
    }

    /** Called when her offer list is first made: the novice trades. */
    public void fillFirstLevel() {
        addLevel(1);
    }

    private void addLevel(int lvl) {
        var byLevel = VillagerTrades.TRADES.get(profession());
        if (byLevel == null) return;
        VillagerTrades.ItemListing[] listings = byLevel.get(lvl);
        if (listings != null) m.addOffers(listings, 2);
    }

    /** Opens the trading screen with this player's prices. */
    /** Why she won't trade now, or null: a village resident only trades at the market, in the afternoon by the bell. */
    @org.jetbrains.annotations.Nullable
    public String closed(ServerPlayer p) {
        var a = net.maidens.maiden.home.Schedule.activity(m);
        if (a == null || a == net.maidens.maiden.home.Schedule.Activity.MARKET) return null;
        String n = p.getName().getString();
        if (m.isBaby()) return "I don't have anything to trade, " + n + "!";
        return switch (a) {
            case SLEEP -> "It's the middle of the night, " + n + "! Come to the market tomorrow afternoon.";
            case WORK -> "I'm at work, " + n + ". Come and find me at the market this afternoon, by the bell.";
            default -> "The market's in the afternoon, by the bell, " + n + ". I'll trade with you there!";
        };
    }

    public void open(ServerPlayer p) {
        MerchantOffers offers = m.getOffers();
        double factor = m.relationships().priceFactor(p);
        for (MerchantOffer o : offers) {
            o.resetSpecialPriceDiff();
            int base = o.getBaseCostA().getCount();
            int off = (int) Math.floor(base * (1.0 - factor));
            if (off > 0) o.addToSpecialPriceDiff(-off);
        }
        m.setTradingPlayer(p);
        m.openTradingScreen(p, m.getDisplayName(), level);
    }

    public void onTrade(MerchantOffer offer) {
        xp += offer.getXp();
        if (offer.shouldRewardExp()) {
            m.level().addFreshEntity(new ExperienceOrb(m.level(), m.getX(), m.getY() + 0.5, m.getZ(), 3 + m.getRandom().nextInt(4)));
        }
        if (m.getTradingPlayer() instanceof ServerPlayer p) {
            m.relationships().onTrade(p);
            m.onTraded(p);
        }
    }

    /** The trading screen closed: level up if she earned it. */
    public void onClosed() {
        boolean up = false;
        while (level < 5 && xp >= NEXT_LEVEL[level]) {
            level++;
            addLevel(level);
            up = true;
        }
        if (up) m.burst(ParticleTypes.HAPPY_VILLAGER, 8);
    }

    /** Once a day her stock comes back. */
    public void tick() {
        if (m.tickCount % 100 != 0) return;
        long day = m.level().getDayTime() / 24000L;
        MerchantOffers offers = m.existingOffers();
        if (offers != null && day != lastRestockDay) {
            lastRestockDay = day;
            for (MerchantOffer o : offers) o.resetUses();
        }
    }

    /** Her profession changed (a new workstation): her trades start over at novice. */
    public void resetForNewProfession() {
        level = 1;
        xp = 0;
        m.clearOffers();
    }

    public void save(CompoundTag tag) {
        tag.putInt("TradeLevel", level);
        tag.putInt("TradeXp", xp);
        tag.putLong("LastRestock", lastRestockDay);
    }

    public void load(CompoundTag tag) {
        level = Math.max(1, tag.getInt("TradeLevel"));
        xp = tag.getInt("TradeXp");
        lastRestockDay = tag.getLong("LastRestock");
    }
}

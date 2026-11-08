package net.maidens.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every tunable number in the mod, in one file (config/maidens-common.toml in the game folder).
 * Code reads these with .get(); nothing else hard-codes a balance number.
 */
public final class MaidensConfig {
    private MaidensConfig() {}

    public static final ModConfigSpec SPEC;

    // work
    public static final ModConfigSpec.IntValue RENT_EMERALDS;
    public static final ModConfigSpec.IntValue SHIFT_TICKS;
    // relationships
    public static final ModConfigSpec.IntValue TALKS_PER_DAY;
    public static final ModConfigSpec.IntValue GIFTS_PER_DAY;
    public static final ModConfigSpec.IntValue TRADE_HEARTS_PER_DAY;
    public static final ModConfigSpec.DoubleValue MARRIED_PRICE_FACTOR;
    public static final ModConfigSpec.DoubleValue MAX_AFFECTION_DISCOUNT;
    // villages
    public static final ModConfigSpec.DoubleValue MAIDEN_VILLAGE_CHANCE;
    public static final ModConfigSpec.IntValue ANGER_PER_BLOCK;
    public static final ModConfigSpec.IntValue ANGER_PER_DAY;
    public static final ModConfigSpec.IntValue GOSSIP_SHARE_PERCENT;
    public static final ModConfigSpec.IntValue CITIZEN_AVERAGE_AFFECTION;
    public static final ModConfigSpec.IntValue CITIZEN_TRADES;
    public static final ModConfigSpec.BooleanValue REPAIRS;
    public static final ModConfigSpec.IntValue REPAIR_DELAY;
    public static final ModConfigSpec.BooleanValue HOUSING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("work");
        RENT_EMERALDS = b.comment("Emeralds to hire a maiden for one shift.").defineInRange("rentEmeralds", 5, 0, 64);
        SHIFT_TICKS = b.comment("How long a hired shift lasts, in ticks (24000 = one Minecraft day).").defineInRange("shiftTicks", 24000, 1200, 240000);
        b.pop();
        b.push("relationships");
        TALKS_PER_DAY = b.comment("How many times a day each kind of chat (chat, joke, compliment, flirt) still has an effect.").defineInRange("talksPerDay", 3, 1, 50);
        GIFTS_PER_DAY = b.comment("Gifts per player per maiden per day.").defineInRange("giftsPerDay", 5, 1, 50);
        TRADE_HEARTS_PER_DAY = b.comment("Affection a player can earn from trades with one maiden per day.").defineInRange("tradeHeartsPerDay", 5, 0, 100);
        MARRIED_PRICE_FACTOR = b.comment("Trade prices for a maiden's husband are multiplied by this.").defineInRange("marriedPriceFactor", 0.5, 0.0, 1.0);
        MAX_AFFECTION_DISCOUNT = b.comment("Discount at 100 affection (it grows evenly from 0 at 0 affection).").defineInRange("maxAffectionDiscount", 0.3, 0.0, 0.9);
        b.pop();
        b.push("villages");
        MAIDEN_VILLAGE_CHANCE = b.comment("Chance that a newly generated village is lived in by maidens instead of villagers.").defineInRange("maidenVillageChance", 0.5, 0.0, 1.0);
        ANGER_PER_BLOCK = b.comment("Affection every maiden of a village loses when a non-citizen breaks one of its blocks.").defineInRange("angerPerBlock", 2, 0, 100);
        ANGER_PER_DAY = b.comment("Most affection a player can lose per maiden per day from breaking village blocks.").defineInRange("angerPerDay", 20, 0, 100);
        GOSSIP_SHARE_PERCENT = b.comment("Percent chance that each other maiden of her village hears about an affection change a player caused.").defineInRange("gossipSharePercent", 25, 0, 100);
        CITIZEN_AVERAGE_AFFECTION = b.comment("Average affection of a village's maidens that makes a player a citizen.").defineInRange("citizenAverageAffection", 50, 0, 100);
        CITIZEN_TRADES = b.comment("Trades in one village that make a player a citizen.").defineInRange("citizenTrades", 30, 1, 10000);
        REPAIRS = b.comment("Maidens repair their village and clear its roads.").define("repairs", true);
        REPAIR_DELAY = b.comment("Ticks a block must stay broken (or a road blocked) before a maiden fixes it.").defineInRange("repairDelay", 600, 0, 1000000);
        HOUSING = b.comment("Maiden villages build houses for maidens without a bed.").define("housing", true);
        b.pop();
        SPEC = b.build();
    }
}

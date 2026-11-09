package net.maidens.maiden;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.maidens.maiden.social.Dialogue;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Who a maiden is: what she loves, likes and dislikes as a gift, how each kind of chat goes down with her,
 * the gift she wants as a proposal, and what she says. Rolled at random when she first appears.
 * Her lines are in data/maidens/dialogue/&lt;personality&gt;.json (%s is the player's name).
 */
public enum Personality {
    SWEET("Sweet", Items.CAKE,
            s -> s.is(Items.CAKE) || s.is(Items.COOKIE) || s.is(Items.PUMPKIN_PIE) || s.is(Items.HONEY_BOTTLE) || s.is(ItemTags.FLOWERS),
            s -> s.is(Items.SWEET_BERRIES) || s.is(Items.GLOW_BERRIES) || s.is(Items.APPLE) || s.is(Items.BREAD) || s.is(Items.MILK_BUCKET),
            s -> s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.BONE) || s.is(Items.GUNPOWDER) || s.is(Items.POISONOUS_POTATO),
            // chat, joke, compliment, flirt: how much better (or worse) each goes than average
            0.10F, -0.05F, 0.15F, 0.05F, "sweet"),

    HARDWORKING("Hardworking", Items.DIAMOND_PICKAXE,
            s -> s.is(Items.DIAMOND) || s.is(Items.EMERALD) || s.is(Items.GOLD_INGOT) || s.is(Items.IRON_INGOT) || s.is(Items.NETHERITE_SCRAP),
            s -> s.is(ItemTags.PICKAXES) || s.is(ItemTags.AXES) || s.is(ItemTags.HOES) || s.is(Items.COAL) || s.is(Items.COPPER_INGOT) || s.is(Items.REDSTONE) || s.is(Items.LAPIS_LAZULI),
            s -> s.is(ItemTags.FLOWERS) || s.is(Items.DEAD_BUSH) || s.is(Items.ROTTEN_FLESH) || s.is(Items.POISONOUS_POTATO),
            0.10F, -0.15F, 0.05F, -0.10F, "hardworking"),

    FREE_SPIRITED("Free-spirited", Items.GOAT_HORN,
            s -> s.has(DataComponents.JUKEBOX_PLAYABLE) || s.is(Items.AMETHYST_SHARD) || s.is(Items.NOTE_BLOCK) || s.is(Items.GOAT_HORN) || s.is(Items.FEATHER),
            s -> s.is(ItemTags.FLOWERS) || s.is(Items.GLOW_BERRIES) || s.is(Items.SWEET_BERRIES) || s.is(Items.BOOK) || s.is(Items.PAPER) || s.is(Items.GLOW_INK_SAC),
            s -> s.is(Items.IRON_INGOT) || s.is(Items.GOLD_INGOT) || s.is(Items.COAL) || s.is(Items.ROTTEN_FLESH) || s.is(Items.POISONOUS_POTATO),
            0.05F, 0.20F, 0.00F, 0.10F, "free_spirited"),

    ADVENTUROUS("Adventurous", Items.SADDLE,
            s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.GOLDEN_CARROT) || s.is(Items.LEATHER) || s.is(Items.MAP) || s.is(Items.COMPASS) || s.is(Items.SADDLE),
            s -> s.is(Items.APPLE) || s.is(Items.CARROT) || s.is(Items.HAY_BLOCK) || s.is(Items.COOKED_BEEF) || s.is(Items.COOKED_PORKCHOP) || s.is(Items.LEAD),
            s -> s.is(Items.CAKE) || s.is(Items.COOKIE) || s.is(Items.ROTTEN_FLESH) || s.is(Items.POISONOUS_POTATO) || s.is(Items.SPIDER_EYE),
            0.00F, 0.10F, -0.05F, 0.15F, "adventurous");

    public final String label;
    public final Item proposal;
    private final Predicate<ItemStack> loves, likes, dislikes;
    public final float chatMod, jokeMod, complimentMod, flirtMod;
    public final String[] chatGood, chatBad, jokeGood, jokeBad, complimentGood, complimentBad, flirtGood, flirtBad,
            giftLove, giftLike, giftNeutral, giftDislike, hints;

    Personality(String label, Item proposal, Predicate<ItemStack> loves, Predicate<ItemStack> likes, Predicate<ItemStack> dislikes,
                float chatMod, float jokeMod, float complimentMod, float flirtMod, String lines) {
        this.label = label; this.proposal = proposal; this.loves = loves; this.likes = likes; this.dislikes = dislikes;
        this.chatMod = chatMod; this.jokeMod = jokeMod; this.complimentMod = complimentMod; this.flirtMod = flirtMod;
        Map<String, String[]> l = Dialogue.load(lines);
        this.chatGood = l.get("chat_good"); this.chatBad = l.get("chat_bad");
        this.jokeGood = l.get("joke_good"); this.jokeBad = l.get("joke_bad");
        this.complimentGood = l.get("compliment_good"); this.complimentBad = l.get("compliment_bad");
        this.flirtGood = l.get("flirt_good"); this.flirtBad = l.get("flirt_bad");
        this.giftLove = l.get("gift_love"); this.giftLike = l.get("gift_like");
        this.giftNeutral = l.get("gift_neutral"); this.giftDislike = l.get("gift_dislike"); this.hints = l.get("hints");
    }

    /** 2 loves, 1 likes, 0 neutral, -1 dislikes. */
    public int taste(ItemStack stack) {
        if (dislikes.test(stack)) return -1;
        if (loves.test(stack)) return 2;
        if (likes.test(stack)) return 1;
        return 0;
    }

    public static Personality byId(int id) {
        Personality[] v = values();
        return v[Math.floorMod(id, v.length)];
    }

    /** Items worth a gift at all: these always count as neutral at least. */
    public static final Set<Item> VALUABLE = Set.of(Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT);
}

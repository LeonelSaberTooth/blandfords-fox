package net.maidens.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Who a maiden is: what she loves, likes and dislikes as a gift, how each kind of chat goes down with her,
 * the gift she wants as a proposal, and what she says. Rolled at random when she first appears.
 * In the lines, %s is the player's name.
 */
public enum Personality {
    SWEET("Sweet", Items.CAKE,
            s -> s.is(Items.CAKE) || s.is(Items.COOKIE) || s.is(Items.PUMPKIN_PIE) || s.is(Items.HONEY_BOTTLE) || s.is(ItemTags.FLOWERS),
            s -> s.is(Items.SWEET_BERRIES) || s.is(Items.GLOW_BERRIES) || s.is(Items.APPLE) || s.is(Items.BREAD) || s.is(Items.MILK_BUCKET),
            s -> s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.BONE) || s.is(Items.GUNPOWDER) || s.is(Items.POISONOUS_POTATO),
            // chat, joke, compliment, flirt: how much better (or worse) each goes than average
            0.10F, -0.05F, 0.15F, 0.05F,
            new String[]{"Oh, %s! I was just thinking it was a lovely day for a visit.", "I baked something this morning. It didn't rise, but it was still nice.", "Did you see the sunrise today? Pink all the way across."},
            new String[]{"Mm... I'm a little tired today, %s.", "Sorry, my mind was somewhere else."},
            new String[]{"Hehe! That's silly, %s.", "Oh no, that's terrible. Tell me another one!", "I'll be giggling about that all afternoon."},
            new String[]{"I... don't think I get it.", "That's a bit mean, isn't it?"},
            new String[]{"You think so? That's so kind of you, %s.", "Oh, stop it, you'll make me blush!", "Thank you. Nobody's said that to me in ages."},
            new String[]{"Hm. You say that to everyone, don't you?", "That sounded a bit rehearsed, %s."},
            new String[]{"Oh! %s... you're making my heart race.", "Maybe we could take a walk together sometime?", "I keep hoping you'll visit."},
            new String[]{"W-what? We hardly know each other!", "That's... a bit forward, %s."},
            new String[]{"For me? Oh, it's perfect! I love it!", "You remembered what I like! Thank you, %s!"},
            new String[]{"That's sweet of you, thank you.", "Oh, how nice. Thank you!"},
            new String[]{"Thank you, I'll find a use for it.", "Oh. Thank you, I suppose."},
            new String[]{"Eww! Why would you give me that?", "Please take that away, %s."},
            new String[]{"I've always dreamed someone would bake me a whole cake, just for me...", "If anyone ever brings me a cake, I'll know they really mean it."}),

    HARDWORKING("Hardworking", Items.DIAMOND_PICKAXE,
            s -> s.is(Items.DIAMOND) || s.is(Items.EMERALD) || s.is(Items.GOLD_INGOT) || s.is(Items.IRON_INGOT) || s.is(Items.NETHERITE_SCRAP),
            s -> s.is(ItemTags.PICKAXES) || s.is(ItemTags.AXES) || s.is(ItemTags.HOES) || s.is(Items.COAL) || s.is(Items.COPPER_INGOT) || s.is(Items.REDSTONE) || s.is(Items.LAPIS_LAZULI),
            s -> s.is(ItemTags.FLOWERS) || s.is(Items.DEAD_BUSH) || s.is(Items.ROTTEN_FLESH) || s.is(Items.POISONOUS_POTATO),
            0.10F, -0.15F, 0.05F, -0.10F,
            new String[]{"Morning, %s. Busy day, but I've got a minute.", "I fixed the fence and the roof before breakfast.", "You look like someone who gets things done."},
            new String[]{"Can it wait? I'm in the middle of something.", "Mm-hm. Sure. Anyway..."},
            new String[]{"Ha! Alright, that one was good.", "Okay, okay, you got me.", "Heh. Don't tell anyone I laughed."},
            new String[]{"...Was that a joke?", "I don't really have time for jokes, %s."},
            new String[]{"Thanks. I work hard for it.", "Coming from you, that means something.", "Hah, you're not so bad yourself, %s."},
            new String[]{"Flattery won't get the work done.", "Right. Thanks, I guess."},
            new String[]{"I... wouldn't mind spending more time with you, %s.", "Don't distract me like that. I'll mess up my work.", "You're the best part of my day, you know."},
            new String[]{"I'm not interested in that kind of thing, %s.", "Let's keep this professional."},
            new String[]{"Now THIS is useful. Thank you, %s!", "Good quality! You've got an eye for it."},
            new String[]{"Handy. Thanks.", "I can use this, thank you."},
            new String[]{"Thanks. I'll put it somewhere.", "Okay. Thanks, %s."},
            new String[]{"What am I supposed to do with this?", "That's useless to me, %s."},
            new String[]{"A diamond pickaxe... now that's a gift that says forever.", "Whoever brings me a diamond pickaxe, I'd follow them anywhere."}),

    FREE_SPIRITED("Free-spirited", Items.GOAT_HORN,
            s -> s.has(DataComponents.JUKEBOX_PLAYABLE) || s.is(Items.AMETHYST_SHARD) || s.is(Items.NOTE_BLOCK) || s.is(Items.GOAT_HORN) || s.is(Items.FEATHER),
            s -> s.is(ItemTags.FLOWERS) || s.is(Items.GLOW_BERRIES) || s.is(Items.SWEET_BERRIES) || s.is(Items.BOOK) || s.is(Items.PAPER) || s.is(Items.GLOW_INK_SAC),
            s -> s.is(Items.IRON_INGOT) || s.is(Items.GOLD_INGOT) || s.is(Items.COAL) || s.is(Items.ROTTEN_FLESH) || s.is(Items.POISONOUS_POTATO),
            0.05F, 0.20F, 0.00F, 0.10F,
            new String[]{"%s! I was just listening to the wind. Did you hear it?", "I had the strangest dream about flying over the mountains.", "Let's go somewhere we've never been!"},
            new String[]{"Hm? Sorry, I was watching a cloud.", "I don't feel like talking right now."},
            new String[]{"Hahaha! %s, you're hilarious!", "Oh, I'm going to put that in a song!", "Stop, stop, my sides hurt!"},
            new String[]{"Hmm. That one fell a bit flat.", "I've heard that one before, %s."},
            new String[]{"Aww, you're sweet. Like a little song.", "Thank you! You've got a nice spark too.", "That made me smile, %s."},
            new String[]{"Hm. Words are just words.", "You don't have to say that."},
            new String[]{"You make my heart hum, %s.", "Run away with me sometime? Just for a day.", "I think I'd like having you around."},
            new String[]{"Woah, slow down there!", "Ha... maybe not, %s."},
            new String[]{"Oh, it's beautiful! It sings to me!", "You understand me, %s. Thank you!"},
            new String[]{"Ooh, pretty. Thank you!", "Nice! I like it."},
            new String[]{"Oh. Thanks, I guess.", "Hm, okay. Thanks."},
            new String[]{"Ugh, that's so dull, %s.", "Why would I want that?"},
            new String[]{"Have you ever heard a goat horn at dawn? That's the sound of forever.", "If someone gave me a goat horn, I'd know they hear the music too."}),

    ADVENTUROUS("Adventurous", Items.SADDLE,
            s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.GOLDEN_CARROT) || s.is(Items.LEATHER) || s.is(Items.MAP) || s.is(Items.COMPASS) || s.is(Items.SADDLE),
            s -> s.is(Items.APPLE) || s.is(Items.CARROT) || s.is(Items.HAY_BLOCK) || s.is(Items.COOKED_BEEF) || s.is(Items.COOKED_PORKCHOP) || s.is(Items.LEAD),
            s -> s.is(Items.CAKE) || s.is(Items.COOKIE) || s.is(Items.ROTTEN_FLESH) || s.is(Items.POISONOUS_POTATO) || s.is(Items.SPIDER_EYE),
            0.00F, 0.10F, -0.05F, 0.15F,
            new String[]{"Hey, %s! Seen anything wild out there lately?", "I rode out past the hills yesterday. You should've seen the view.", "Got any good stories for me?"},
            new String[]{"Yeah, yeah. Talk later, I'm restless.", "Hm. Boring."},
            new String[]{"Ha! Nice one, %s!", "Haha, okay, that's going in my book of good ones.", "You're funnier than you look!"},
            new String[]{"Pfft. Weak.", "That joke died out on the plains, %s."},
            new String[]{"Hah, thanks! You're pretty brave yourself.", "Yeah? Thanks, %s.", "Careful, I'll get a big head."},
            new String[]{"Save the sweet talk, %s.", "Meh."},
            new String[]{"You and me, %s, out on the open road... I like the sound of that.", "Heh. You've got guts, I'll give you that.", "Maybe I'll let you ride with me."},
            new String[]{"Ha! Nice try, %s.", "You'll have to impress me more than that."},
            new String[]{"Whoa! Now we're talking! Thanks, %s!", "This is perfect for the road!"},
            new String[]{"Nice, this'll come in handy.", "Thanks! Good stuff."},
            new String[]{"Thanks, I guess.", "Hm. Alright."},
            new String[]{"Ugh, too sweet. Not my thing.", "What am I supposed to do with that, %s?"},
            new String[]{"Every rider dreams of a saddle of her own... from someone special.", "If someone gave me a saddle, I'd ride off into the sunset with them."});

    public final String label;
    public final Item proposal;
    private final Predicate<ItemStack> loves, likes, dislikes;
    public final float chatMod, jokeMod, complimentMod, flirtMod;
    public final String[] chatGood, chatBad, jokeGood, jokeBad, complimentGood, complimentBad, flirtGood, flirtBad,
            giftLove, giftLike, giftNeutral, giftDislike, hints;

    Personality(String label, Item proposal, Predicate<ItemStack> loves, Predicate<ItemStack> likes, Predicate<ItemStack> dislikes,
                float chatMod, float jokeMod, float complimentMod, float flirtMod,
                String[] chatGood, String[] chatBad, String[] jokeGood, String[] jokeBad, String[] complimentGood, String[] complimentBad,
                String[] flirtGood, String[] flirtBad, String[] giftLove, String[] giftLike, String[] giftNeutral, String[] giftDislike,
                String[] hints) {
        this.label = label; this.proposal = proposal; this.loves = loves; this.likes = likes; this.dislikes = dislikes;
        this.chatMod = chatMod; this.jokeMod = jokeMod; this.complimentMod = complimentMod; this.flirtMod = flirtMod;
        this.chatGood = chatGood; this.chatBad = chatBad; this.jokeGood = jokeGood; this.jokeBad = jokeBad;
        this.complimentGood = complimentGood; this.complimentBad = complimentBad; this.flirtGood = flirtGood; this.flirtBad = flirtBad;
        this.giftLove = giftLove; this.giftLike = giftLike; this.giftNeutral = giftNeutral; this.giftDislike = giftDislike; this.hints = hints;
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
    static final Set<Item> VALUABLE = Set.of(Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT);
}

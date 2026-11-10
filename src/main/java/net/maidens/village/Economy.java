package net.maidens.village;

import net.maidens.maiden.Maiden;
import net.maidens.politics.Network;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A village's store and what it lives on. Its farmers bring in crops, its woodcutters logs, its miners stone (and
 * whatever ore they find), its fishers fish. Every day its people eat (one meal for every two of them); a newcomer
 * only moves in if there's food to spare, and the village only builds a house for more people when it has the
 * logs and stone for it. What a citizen orders built is built for free.
 */
public final class Economy {
    public static final int NEWCOMER_FOOD = 8, HOUSE_LOGS = 24, HOUSE_STONE = 24;
    private static final int DAY = 24000;
    private static final Set<Item> NOT_FOOD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH);
    private static final Set<Item> STONES = Set.of(Items.COBBLESTONE, Items.STONE, Items.COBBLED_DEEPSLATE, Items.DEEPSLATE, Items.ANDESITE,
            Items.DIORITE, Items.GRANITE, Items.TUFF, Items.SANDSTONE, Items.BLACKSTONE, Items.CALCITE);

    public static final Predicate<Item> FOOD = i -> i == Items.WHEAT || !NOT_FOOD.contains(i) && i.components().has(DataComponents.FOOD);
    public static final Predicate<Item> LOGS = i -> i.builtInRegistryHolder().is(ItemTags.LOGS);
    public static final Predicate<Item> STONE = STONES::contains;

    private Economy() {}

    public static int food(VillageRecord v) {
        return v.count(FOOD);
    }

    public static boolean canBuildHouse(VillageRecord v) {
        return v.count(LOGS) >= HOUSE_LOGS && v.count(STONE) >= HOUSE_STONE;
    }

    public static void payForHouse(VillageRecord v) {
        v.take(LOGS, HOUSE_LOGS);
        v.take(STONE, HOUSE_STONE);
    }

    /** Once a minute (from {@link Housing}): a new village's first stock, and the day's meals. */
    static void tick(ServerLevel level, VillageRecord v, List<Maiden> residents) {
        if (!v.stocked) {
            v.stocked = true;
            v.storeAdd(Items.WHEAT, 48);
            v.storeAdd(Items.BREAD, 16);
            v.storeAdd(Items.OAK_LOG, 32);
            v.storeAdd(Items.COBBLESTONE, 32);
            Villages.get(level).setDirty();
        }
        long day = level.getDayTime() / DAY;
        if (v.lastMeal < 0) v.lastMeal = day;
        if (day <= v.lastMeal) return;
        v.lastMeal = day;
        int meals = (residents.size() + 1) / 2;
        if (!v.take(FOOD, meals)) {
            v.take(FOOD, food(v));
            Network.get(level).news(level, v.id, v.name + " is short of food. Nobody new will move in until its farmers catch up.");
        }
        Villages.get(level).setDirty();
    }
}

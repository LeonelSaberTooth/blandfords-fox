package net.maidens.maiden.social;

import net.maidens.maiden.Maiden;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A village maiden's request (the village's requests board): now and then she wants a stack of something for
 * her work, and pays emeralds and likes you more for bringing it. A request lasts three days.
 */
public final class Wishes {
    private static final int AFFECTION = 6, DAYS = 3;

    private final Maiden m;
    @Nullable private Item item;
    private int count, reward;
    private long untilDay;

    public Wishes(Maiden m) {
        this.m = m;
    }

    @Nullable
    public Item item() {
        return item;
    }

    /** "12 wheat for 3 emeralds", or "" if she wants nothing. */
    public String describe() {
        if (item == null) return "";
        return count + " " + new ItemStack(item).getHoverName().getString().toLowerCase() + " for " + reward + " emerald" + (reward == 1 ? "" : "s");
    }

    /** Once a minute: old requests expire, and sometimes she thinks of a new one. */
    public void tick() {
        if (m.tickCount % 1200 != 600 || !m.residence().inVillage()) return;
        long day = m.level().getDayTime() / 24000L;
        if (item != null && day > untilDay) item = null;
        RandomSource r = m.getRandom();
        if (item == null && r.nextInt(4) == 0) {
            Item[] wanted = wanted(m.profession());
            item = wanted[r.nextInt(wanted.length)];
            count = 4 + r.nextInt(13);
            if (item.getDefaultMaxStackSize() < count) count = item.getDefaultMaxStackSize();
            reward = 1 + count / 5;
            untilDay = day + DAYS;
        }
    }

    /** For the self test. */
    public void setForTest(Item wanted, int amount) {
        item = wanted;
        count = amount;
        reward = 1 + amount / 5;
        untilDay = m.level().getDayTime() / 24000L + DAYS;
    }

    /** The player handed her what she asked for: returns her reply, or null if it isn't what she wants. */
    @Nullable
    public String fulfil(ServerPlayer p) {
        ItemStack held = p.getMainHandItem();
        if (item == null || !held.is(item)) return null;
        String n = p.getName().getString();
        if (held.getCount() < count && !p.getAbilities().instabuild) return "That's it! But I need " + count + ", " + n + ". You only have " + held.getCount() + ".";
        if (!p.getAbilities().instabuild) held.shrink(count);
        ItemStack pay = new ItemStack(Items.EMERALD, reward);
        if (!p.getInventory().add(pay)) p.drop(pay, false);
        m.relationships().change(p, AFFECTION);
        m.burst(ParticleTypes.HAPPY_VILLAGER, 6);
        item = null;
        return "Just what I needed! Here, " + reward + " emerald" + (reward == 1 ? "" : "s") + ". Thank you, " + n + "!";
    }

    /** What she might ask for, by her work. */
    private static Item[] wanted(@Nullable VillagerProfession p) {
        if (p == VillagerProfession.FARMER) return new Item[]{Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.BONE_MEAL};
        if (p == VillagerProfession.LIBRARIAN) return new Item[]{Items.PAPER, Items.BOOK, Items.INK_SAC};
        if (p == VillagerProfession.FLETCHER) return new Item[]{Items.STICK, Items.STRING, Items.FEATHER, Items.FLINT};
        if (p == VillagerProfession.FISHERMAN) return new Item[]{Items.STRING, Items.COAL, Items.COD};
        if (p == VillagerProfession.SHEPHERD) return new Item[]{Items.WHITE_WOOL, Items.SHEARS, Items.WHITE_DYE};
        if (p == VillagerProfession.BUTCHER) return new Item[]{Items.COAL, Items.PORKCHOP, Items.CHICKEN, Items.RABBIT};
        if (p == VillagerProfession.CLERIC) return new Item[]{Items.ROTTEN_FLESH, Items.GOLD_INGOT, Items.GLASS_BOTTLE};
        if (p == VillagerProfession.CARTOGRAPHER) return new Item[]{Items.PAPER, Items.GLASS_PANE, Items.COMPASS};
        if (p == VillagerProfession.LEATHERWORKER) return new Item[]{Items.LEATHER, Items.RABBIT_HIDE, Items.FLINT};
        if (p == VillagerProfession.MASON) return new Item[]{Items.CLAY_BALL, Items.STONE, Items.GRANITE, Items.ANDESITE};
        if (p == VillagerProfession.ARMORER || p == VillagerProfession.TOOLSMITH || p == VillagerProfession.WEAPONSMITH)
            return new Item[]{Items.IRON_INGOT, Items.COAL, Items.LAVA_BUCKET};
        return new Item[]{Items.BREAD, Items.APPLE, Items.POPPY, Items.DANDELION, Items.TORCH};
    }

    public void save(CompoundTag tag) {
        if (item == null) return;
        CompoundTag t = new CompoundTag();
        t.putString("Item", BuiltInRegistries.ITEM.getKey(item).toString());
        t.putInt("Count", count);
        t.putInt("Reward", reward);
        t.putLong("Until", untilDay);
        tag.put("Wish", t);
    }

    public void load(CompoundTag tag) {
        item = null;
        if (!tag.contains("Wish")) return;
        CompoundTag t = tag.getCompound("Wish");
        ResourceLocation key = ResourceLocation.tryParse(t.getString("Item"));
        item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (item == Items.AIR) item = null;
        count = t.getInt("Count");
        reward = t.getInt("Reward");
        untilDay = t.getLong("Until");
    }
}

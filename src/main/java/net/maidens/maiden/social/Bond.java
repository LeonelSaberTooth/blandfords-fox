package net.maidens.maiden.social;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Set;

/** How one maiden feels about one player, and what they've already done together today. */
public final class Bond {
    public static final int MAX = 100;

    public int affection;
    long day = -1;
    int chats, jokes, compliments, flirts, gifts, tradeHearts;
    /** Affection lost today to broken village blocks (capped by config). */
    int angerToday;
    final Set<String> giftsToday = new HashSet<>();

    /** Starts a new day's counts when the day changes. */
    void today(long day) {
        if (this.day == day) return;
        this.day = day;
        chats = jokes = compliments = flirts = gifts = tradeHearts = angerToday = 0;
        giftsToday.clear();
    }

    public void add(int amount) {
        affection = Math.max(0, Math.min(MAX, affection + amount));
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Affection", affection);
        t.putLong("Day", day);
        t.putInt("Chats", chats);
        t.putInt("Jokes", jokes);
        t.putInt("Compliments", compliments);
        t.putInt("Flirts", flirts);
        t.putInt("Gifts", gifts);
        t.putInt("TradeHearts", tradeHearts);
        t.putInt("AngerToday", angerToday);
        ListTag list = new ListTag();
        for (String s : giftsToday) list.add(StringTag.valueOf(s));
        t.put("GiftsToday", list);
        return t;
    }

    static Bond load(CompoundTag t) {
        Bond b = new Bond();
        b.affection = t.getInt("Affection");
        b.day = t.getLong("Day");
        b.chats = t.getInt("Chats");
        b.jokes = t.getInt("Jokes");
        b.compliments = t.getInt("Compliments");
        b.flirts = t.getInt("Flirts");
        b.gifts = t.getInt("Gifts");
        b.tradeHearts = t.getInt("TradeHearts");
        b.angerToday = t.getInt("AngerToday");
        ListTag list = t.getList("GiftsToday", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) b.giftsToday.add(list.getString(i));
        return b;
    }
}

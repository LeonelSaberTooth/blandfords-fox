package net.maidens.maiden.social;

import net.maidens.core.MaidensConfig;
import net.maidens.maiden.Maiden;
import net.maidens.maiden.Personality;
import net.maidens.network.ActionPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How a maiden feels about each player (affection 0-100, see {@link Bond}), and who she's married to.
 * Owns talking, gifts and proposals. Every change a player causes goes through {@link #change}, which tells
 * the maiden (and so her village's gossip) in one place.
 */
public final class Relationships {
    private final Maiden m;
    private final Map<UUID, Bond> bonds = new HashMap<>();
    @Nullable private UUID spouse;
    private String spouseName = "";

    public Relationships(Maiden m) {
        this.m = m;
    }

    // ------------------------------------------------------------------ state

    Bond bond(UUID player) {
        Bond b = bonds.computeIfAbsent(player, u -> new Bond());
        b.today(m.level().getDayTime() / 24000L);
        return b;
    }

    public int affection(Player p) {
        return bond(p.getUUID()).affection;
    }

    public int affection(UUID p) {
        return bond(p).affection;
    }

    public void setAffection(Player p, int value) {
        bond(p.getUUID()).affection = Math.max(0, Math.min(Bond.MAX, value));
    }

    /** A change caused by a player's own action; spreads as gossip. */
    public void change(Player p, int amount) {
        bond(p.getUUID()).add(amount);
        if (amount != 0) m.onAffectionChanged(p, amount);
    }

    /** A change heard about from someone else (gossip): doesn't spread further. */
    public void hear(UUID player, int amount) {
        bond(player).add(amount);
    }

    /** Village anger: loses affection, at most the daily cap. Returns what was actually taken. */
    public int anger(UUID player, int amount) {
        Bond b = bond(player);
        int room = Math.max(0, MaidensConfig.ANGER_PER_DAY.get() - b.angerToday);
        int take = Math.min(room, amount);
        b.angerToday += take;
        b.add(-take);
        return take;
    }

    public boolean isMarried() {
        return spouse != null;
    }

    public boolean isMarriedTo(Player p) {
        return spouse != null && spouse.equals(p.getUUID());
    }

    @Nullable
    public UUID spouse() {
        return spouse;
    }

    public String spouseName() {
        return spouseName;
    }

    /** Discount factor for a player (1 = full price): married, or by affection. */
    public double priceFactor(Player p) {
        if (isMarriedTo(p)) return MaidensConfig.MARRIED_PRICE_FACTOR.get();
        return 1.0 - MaidensConfig.MAX_AFFECTION_DISCOUNT.get() * affection(p) / Bond.MAX;
    }

    // ------------------------------------------------------------------ talking

    public String greeting(Player p) {
        String n = p.getName().getString();
        if (isMarriedTo(p)) return "Welcome home, my love.";
        int a = affection(p);
        if (a < 20) return "Oh, hello. Can I help you?";
        if (a < 50) return "Hi, " + n + "!";
        if (a < 80) return n + "! I was hoping you'd come by.";
        return n + "! There you are. I missed you.";
    }

    private static final float[] BASE = {0.75F, 0.55F, 0.65F, 0.45F};
    private static final int[] GOOD = {3, 4, 4, 6};
    private static final int[] BAD = {1, 3, 2, 5};

    /** Chat, joke, compliment or flirt (ActionPayload.CHAT..FLIRT). Returns her reply. */
    public String talk(ServerPlayer p, int kind) {
        String n = p.getName().getString();
        Bond b = bond(p.getUUID());
        RandomSource r = m.getRandom();
        if (isMarriedTo(p)) {
            b.add(1);
            m.burst(ParticleTypes.HEART, 3);
            return Dialogue.pick(Dialogue.SPOUSE, r, n);
        }
        if (kind == ActionPayload.FLIRT && spouse != null) return Dialogue.pick(Dialogue.MARRIED_ELSEWHERE, r, n);
        int count = switch (kind) {
            case ActionPayload.CHAT -> b.chats++;
            case ActionPayload.JOKE -> b.jokes++;
            case ActionPayload.COMPLIMENT -> b.compliments++;
            default -> b.flirts++;
        };
        if (count >= MaidensConfig.TALKS_PER_DAY.get()) return Dialogue.pick(Dialogue.TIRED, r, n);
        Personality pe = m.getPersonality();
        float mod = switch (kind) {
            case ActionPayload.CHAT -> pe.chatMod;
            case ActionPayload.JOKE -> pe.jokeMod;
            case ActionPayload.COMPLIMENT -> pe.complimentMod;
            default -> pe.flirtMod;
        };
        float chance = BASE[kind] + mod + b.affection * 0.004F;
        if (kind == ActionPayload.FLIRT && b.affection < 20) chance -= 0.3F;
        chance = Math.max(0.05F, Math.min(0.95F, chance));
        boolean ok = r.nextFloat() < chance;
        change(p, ok ? Math.max(1, GOOD[kind] / (1 + count)) : -BAD[kind]);
        m.burst(ok ? ParticleTypes.HEART : ParticleTypes.ANGRY_VILLAGER, ok ? 3 : 2);
        String[] lines = switch (kind) {
            case ActionPayload.CHAT -> ok ? pe.chatGood : pe.chatBad;
            case ActionPayload.JOKE -> ok ? pe.jokeGood : pe.jokeBad;
            case ActionPayload.COMPLIMENT -> ok ? pe.complimentGood : pe.complimentBad;
            default -> ok ? pe.flirtGood : pe.flirtBad;
        };
        String line = Dialogue.pick(lines, r, n);
        // once she's fond of you, she sometimes lets slip what she'd love to be proposed with
        if (ok && b.affection >= 70 && r.nextFloat() < 0.35F) line += " " + Dialogue.pick(pe.hints, r, n);
        return line;
    }

    // ------------------------------------------------------------------ gifts and proposals

    /** Gives her the item in the player's main hand. Returns her reply. */
    public String gift(ServerPlayer p) {
        String n = p.getName().getString();
        ItemStack held = p.getMainHandItem();
        RandomSource r = m.getRandom();
        Personality pe = m.getPersonality();
        if (held.isEmpty()) return "You're not holding anything, " + n + ".";
        if (held.is(pe.proposal)) {
            String answer = propose(p, held);
            if (answer != null) return answer;
        }
        Bond b = bond(p.getUUID());
        String key = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int perDay = MaidensConfig.GIFTS_PER_DAY.get();
        if (b.gifts >= perDay) return "You've given me so much today already, " + n + ". Save some for tomorrow!";
        if (b.giftsToday.contains(key)) return "You already gave me one of those today.";
        int taste = pe.taste(held);
        int amount = switch (taste) {
            case 2 -> 10;
            case 1 -> 5;
            case -1 -> -5;
            default -> Personality.VALUABLE.contains(held.getItem()) ? 3 : 1;
        };
        if (amount > 0) amount = Math.max(1, amount * (perDay - b.gifts) / perDay);
        if (!p.getAbilities().instabuild) held.shrink(1);
        b.gifts++;
        b.giftsToday.add(key);
        change(p, amount);
        m.burst(amount > 0 ? ParticleTypes.HEART : ParticleTypes.ANGRY_VILLAGER, taste == 2 ? 6 : 2);
        return Dialogue.pick(switch (taste) {
            case 2 -> pe.giftLove;
            case 1 -> pe.giftLike;
            case -1 -> pe.giftDislike;
            default -> pe.giftNeutral;
        }, r, n);
    }

    /** The held item is the one she dreams of. Returns her answer, or null to treat it as an ordinary gift. */
    @Nullable
    private String propose(ServerPlayer p, ItemStack ring) {
        String n = p.getName().getString();
        if (isMarriedTo(p)) return "We're already married, silly! But thank you.";
        if (spouse != null) return Dialogue.pick(Dialogue.MARRIED_ELSEWHERE, m.getRandom(), n);
        Marriages marriages = Marriages.get(p.server);
        UUID theirs = marriages.spouse(p.getUUID());
        if (theirs != null && !theirs.equals(m.getUUID())) return "Aren't you already married, " + n + "?";
        if (affection(p) < Bond.MAX) return "Oh... it's beautiful, " + n + ". But I'm not ready for that. Not yet.";
        if (!p.getAbilities().instabuild) ring.shrink(1);
        spouse = p.getUUID();
        spouseName = n;
        marriages.marry(p.getUUID(), m.getUUID());
        m.onMarried(p);
        m.burst(ParticleTypes.HEART, 20);
        m.level().playSound(null, m.blockPosition(), SoundEvents.PLAYER_LEVELUP, m.getSoundSource(), 1.0F, 1.0F);
        return "Yes! Yes, " + n + ", I'll marry you! From now on, what's mine is yours.";
    }

    /** Trading with her: a little affection, up to a daily cap. */
    public void onTrade(Player p) {
        Bond b = bond(p.getUUID());
        if (b.tradeHearts < MaidensConfig.TRADE_HEARTS_PER_DAY.get()) {
            b.tradeHearts++;
            change(p, 1);
        }
    }

    public void onDeath() {
        if (spouse != null && m.level().getServer() != null) Marriages.get(m.level().getServer()).widow(m.getUUID());
    }

    // ------------------------------------------------------------------ saving

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        bonds.forEach((u, b) -> {
            CompoundTag t = b.save();
            t.putUUID("Player", u);
            list.add(t);
        });
        tag.put("Bonds", list);
        if (spouse != null) tag.putUUID("Spouse", spouse);
        tag.putString("SpouseName", spouseName);
    }

    public void load(CompoundTag tag) {
        bonds.clear();
        ListTag list = tag.getList("Bonds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.hasUUID("Player")) bonds.put(t.getUUID("Player"), Bond.load(t));
        }
        spouse = tag.hasUUID("Spouse") ? tag.getUUID("Spouse") : null;
        spouseName = tag.getString("SpouseName");
    }
}

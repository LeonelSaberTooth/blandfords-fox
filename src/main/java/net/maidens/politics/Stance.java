package net.maidens.politics;

/** How one village treats another. */
public enum Stance {
    /** Caravans, a road, better prices. */
    TRADE("trading", 0x3AD13A),
    NEUTRAL("neutral", 0xE8D040),
    /** Sends raids now and then. */
    AGGRESSIVE("aggressive", 0xE04040);

    public final String label;
    public final int color;

    Stance(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public static Stance byId(int id) {
        Stance[] all = values();
        return id >= 0 && id < all.length ? all[id] : NEUTRAL;
    }
}

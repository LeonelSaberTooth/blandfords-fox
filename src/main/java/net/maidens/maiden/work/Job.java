package net.maidens.maiden.work;

/** What a maiden has been hired to do. */
public enum Job {
    NONE("Free", "nothing", ""),
    FARM("Farm", "farming", "farmer"),
    GUARD("Guard", "guarding you", ""),
    WOOD("Woodcutter", "chopping wood", "fletcher"),
    MINE("Miner", "mining", "toolsmith"),
    FISH("Fisher", "fishing", "fisherman");

    public final String button, doing;
    /** The villager profession whose hat she wears for it ("" for none). */
    public final String hat;

    Job(String button, String doing, String hat) {
        this.button = button;
        this.doing = doing;
        this.hat = hat;
    }

    public static Job byId(int id) {
        Job[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }

    /** Jobs done at a work spot, as opposed to following someone. */
    public boolean atWorkSpot() {
        return this == FARM || this == WOOD || this == MINE || this == FISH;
    }
}

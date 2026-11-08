package net.maidens.maiden.work;

/** What a maiden has been hired to do. */
public enum Job {
    NONE("Free", "nothing"),
    FARM("Farm", "farming"),
    GUARD("Guard", "guarding you"),
    WOOD("Woodcutter", "chopping wood"),
    MINE("Miner", "mining"),
    FISH("Fisher", "fishing");

    public final String button, doing;

    Job(String button, String doing) {
        this.button = button;
        this.doing = doing;
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

package net.blandfordsfox.compat;

/**
 * Far East's tiger with the animations made for it (only when Far East is installed): the
 * numbers both sides share. Actions 1-4 are Far East's (lick, roar, stretch, pounce); ours
 * start at 10 and travel in Far East's own synced action byte.
 */
public final class FeTiger {
    private FeTiger() {}

    public static final String MOD_ID = "fareast";

    public static final int ROAR = 2, STRETCH = 3;
    public static final int MARK = 10, CHEEK_RUB = 11, SPRAY = 12, GROOM = 13;

    /** Playback speed of the new STRETCH (the speed chosen on the page). */
    public static final float STRETCH_SPEED = 0.74F;
    /** How long each action lasts, in ticks (the animations' lengths at their speeds). */
    public static final int STRETCH_TICKS = 152, ROAR_TICKS = 88, MARK_TICKS = 132, CHEEK_RUB_TICKS = 112, SPRAY_TICKS = 88,
            GROOM_TICKS = 160;

    /** Blocks travelled per loop of the new walk and gallop, so the paws keep pace with the ground. */
    public static final float WALK_BLOCKS_PER_CYCLE = 1.2695F, RUN_BLOCKS_PER_CYCLE = 3.75F;
    /**
     * Far East runs its tiger at 2.1x its speed, about 11.9 blocks a second (measured); this makes
     * it 8.1 blocks a second, the gallop at 1.3x (ground speed goes with the square of the speed
     * for a walking mob: 1.32 blocks a second at 0.7, 11.9 at 2.1).
     */
    public static final double RUN_SPEED = 1.738;

    public static boolean ours(int action) {
        return action >= MARK && action <= GROOM;
    }

    public static int ticks(int action) {
        return switch (action) {
            case MARK -> MARK_TICKS;
            case CHEEK_RUB -> CHEEK_RUB_TICKS;
            case SPRAY -> SPRAY_TICKS;
            case GROOM -> GROOM_TICKS;
            default -> 0;
        };
    }
}

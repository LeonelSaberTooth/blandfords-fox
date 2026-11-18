package net.blandfordsfox.compat;

/**
 * Far East compatibility: what we reach in Far East's tiger. Far East's tiger gets this interface
 * from FarEastTigerMixin (only when Far East is installed), so a tiger can be cast to it.
 */
public interface FeTigerAccess {
    void blandfordsfox$startAction(int action, int ticks);

    boolean blandfordsfox$isIdle();

    void blandfordsfox$setActivity(int activity, int ticks);

    int blandfordsfox$getAction();

    int blandfordsfox$getActivity();

    int blandfordsfox$getMood();

    int blandfordsfox$activityTicks();
}

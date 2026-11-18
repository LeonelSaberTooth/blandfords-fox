package net.blandfordsfox.compat;

import net.blandfordsfox.mixin.FarEastTigerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Far East compatibility check for the self-test (only when Far East is installed): on a grass
 * platform high in the air, measures how fast the tiger walks and runs, sends it to a birch for
 * each tree animation and checks where it stands and for how long it plays, and checks grooming,
 * stretching and roaring last as long as their animations.
 */
public final class FeTigerSelfTest {
    private FeTigerSelfTest() {}

    private static final int Y = 230, X0 = 0, Z0 = 2400, SIZE = 48;

    public static void run(ServerLevel level, StringBuilder report) {
        report.append("far east tiger:\n");
        try {
            arena(level);
            speeds(level, report);
            for (int kind = 0; kind < 3; kind++) tree(level, kind, report);
            groom(level, report);
            lengths(level, report);
        } catch (Throwable t) {
            report.append("  failed: ").append(t).append('\n');
            for (StackTraceElement e : t.getStackTrace()) report.append("    at ").append(e).append('\n');
        } finally {
            FeTigerGoals.forceNext = -1;
            FeTigerGoals.eager = false;
        }
    }

    private static void arena(ServerLevel level) {
        for (int cx = (X0 >> 4) - 1; cx <= ((X0 + SIZE) >> 4) + 1; cx++)
            for (int cz = (Z0 >> 4) - 1; cz <= ((Z0 + SIZE) >> 4) + 1; cz++) level.getChunk(cx, cz);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = X0; x < X0 + SIZE; x++)
            for (int z = Z0; z < Z0 + SIZE; z++) {
                level.setBlock(p.set(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = Y; y < Y + 8; y++) level.setBlock(p.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
    }

    @SuppressWarnings("unchecked")
    private static PathfinderMob tiger(ServerLevel level, double x, double z, float yaw) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(FeTiger.MOD_ID, "tiger"));
        PathfinderMob t = (PathfinderMob) type.create(level);
        t.moveTo(x, Y, z, yaw, 0);
        t.setYHeadRot(yaw);
        t.yBodyRot = yaw;
        level.addFreshEntity(t);
        return t;
    }

    private static void tick(Mob m, int n) {
        for (int i = 0; i < n; i++) {
            m.tickCount++;
            m.tick();
        }
    }

    private static FarEastTigerAccess fe(Mob m) {
        return (FarEastTigerAccess) m;
    }

    /** Ground speed with the move control driven at a speed, in blocks a second. */
    private static void speeds(ServerLevel level, StringBuilder report) {
        StringBuilder r = new StringBuilder("  ground speed (blocks/s) at move speed:");
        for (double speed : new double[]{0.7, 2.1, FeTiger.RUN_SPEED}) {
            PathfinderMob t = tiger(level, X0 + 2.5, Z0 + 24.5, -90);
            t.goalSelector.removeAllGoals(g -> true);
            tick(t, 5);
            double x = 0;
            for (int i = 0; i < 70; i++) {
                t.getMoveControl().setWantedPosition(X0 + 200, Y, Z0 + 24.5, speed);
                tick(t, 1);
                if (i == 40) x = t.getX();
            }
            r.append(String.format(Locale.ROOT, " %.3f -> %.2f", speed, (t.getX() - x) / 29 * 20));
            t.discard();
        }
        report.append(r).append('\n');
    }

    /** Sends a tiger to a birch for one tree animation and checks it lines up and stays put. */
    private static void tree(ServerLevel level, int kind, StringBuilder report) {
        String[] names = {"scratch (MARK)", "cheek rub", "spray"};
        BlockPos trunk = new BlockPos(X0 + 24, Y, Z0 + 24);
        for (int i = 0; i < 5; i++) level.setBlock(trunk.above(i), Blocks.BIRCH_LOG.defaultBlockState(), 2);
        // start it off to one side and turned away, so it has to walk round and line up
        PathfinderMob t = tiger(level, X0 + 17.5 + kind * 2, Z0 + 31.5 - kind * 4, 37 + kind * 100);
        FeTigerGoals.forceNext = kind;
        FeTigerGoals.eager = true;
        int start = -1, played = 0, ticks = 0;
        Vec3 at = null;
        float yaw = 0;
        double drift = 0, yawDrift = 0;
        for (; ticks < 900; ticks++) {
            tick(t, 1);
            int action = fe(t).blandfordsfox$getAction();
            if (action == kind + FeTiger.MARK) {
                if (start < 0) { start = ticks; at = t.position(); yaw = t.getYRot(); }
                played++;
                drift = Math.max(drift, t.position().distanceTo(at));
                yawDrift = Math.max(yawDrift, Math.abs(net.minecraft.util.Mth.wrapDegrees(t.getYRot() - yaw)));
            } else if (start >= 0) break;
        }
        report.append("  ").append(names[kind]).append(": ");
        if (start < 0) {
            report.append("never started; ").append(FeTigerGoals.TreeGoal.last).append('\n');
        } else {
            double rad = Math.toRadians(yaw);
            double fx = -Math.sin(rad), fz = Math.cos(rad), rx = -Math.cos(rad), rz = -Math.sin(rad);
            double dx = trunk.getX() + 0.5 - at.x, dz = trunk.getZ() + 0.5 - at.z;
            // the trunk in the tiger's own frame, in model pixels: right, and backwards
            double right = (dx * rx + dz * rz) * 16, back = -(dx * fx + dz * fz) * 16;
            report.append(String.format(Locale.ROOT, "started after %d ticks, played %d ticks (wanted %d), trunk at %.2f px right, %.2f px back, "
                            + "yaw %.2f, moved %.4f and turned %.2f while playing; %s%n",
                    start, played, FeTiger.ticks(kind + FeTiger.MARK), right, back, yaw, drift, yawDrift, FeTigerGoals.TreeGoal.last));
        }
        t.discard();
        for (int i = 0; i < 5; i++) level.setBlock(trunk.above(i), Blocks.AIR.defaultBlockState(), 2);
        FeTigerGoals.forceNext = -1;
        FeTigerGoals.eager = false;
    }

    private static void groom(ServerLevel level, StringBuilder report) {
        PathfinderMob t = tiger(level, X0 + 10.5, Z0 + 10.5, 0);
        tick(t, 5);
        fe(t).blandfordsfox$setActivity(1, 2000);
        FeTigerGoals.eager = true;
        int start = -1, played = 0, moved = 0;
        Vec3 at = t.position();
        for (int i = 0; i < 400; i++) {
            tick(t, 1);
            if (fe(t).blandfordsfox$getAction() == FeTiger.GROOM) {
                if (start < 0) start = i;
                played++;
                if (fe(t).blandfordsfox$getActivity() != 1) moved++;
            } else if (start >= 0) break;
        }
        FeTigerGoals.eager = false;
        report.append(String.format(Locale.ROOT, "  grooming while resting: started after %d ticks, played %d ticks (wanted %d), still lying %s, moved %.3f%n",
                start, played, FeTiger.GROOM_TICKS, moved == 0, t.position().distanceTo(at)));
        t.discard();
    }

    /** Far East's own stretch and roar, made as long as the new animations. */
    private static void lengths(ServerLevel level, StringBuilder report) {
        PathfinderMob t = tiger(level, X0 + 36.5, Z0 + 10.5, 0);
        tick(t, 5);
        StringBuilder r = new StringBuilder("  action lengths (ticks):");
        for (int action : new int[]{FeTiger.STRETCH, FeTiger.ROAR}) {
            fe(t).blandfordsfox$startAction(action, 40);
            int n = 0;
            while (n < 400 && fe(t).blandfordsfox$getAction() == action) { tick(t, 1); n++; }
            r.append(' ').append(action == FeTiger.STRETCH ? "stretch " : "roar ").append(n)
                    .append(" (wanted ").append(action == FeTiger.STRETCH ? FeTiger.STRETCH_TICKS : FeTiger.ROAR_TICKS).append(')');
            tick(t, 30);
        }
        report.append(r).append('\n');
        t.discard();
    }
}

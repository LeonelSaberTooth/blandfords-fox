package net.blandfordsfox.client.fareast;

import net.blandfordsfox.compat.FeTiger;
import net.blandfordsfox.compat.FeTigerGoals;
import net.blandfordsfox.compat.FeTigerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Client test with Far East installed: on a platform in the sky, a tiger plays each new animation
 * (lined up with a birch for the tree ones) and walks and gallops past the camera; screenshots at
 * set moments. Called from ClientSelfTest only when Far East is installed.
 */
public final class FeTigerClientTest {
    private FeTigerClientTest() {}

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final int Y = 230, X0 = 0, Z0 = 2400;
    private static final BlockPos TRUNK = new BlockPos(X0 + 24, Y, Z0 + 24);

    /** A scene: what the tiger does, where the camera is (relative to the tiger), and when to take pictures (ticks after it starts). */
    record Scene(String name, int action, double camX, double camY, double camZ, int... shots) {}

    private static final List<Scene> SCENES = List.of(
            new Scene("mark", FeTiger.MARK, 5.5, 1.6, 0.5, 20, 34, 42, 80, 125),
            new Scene("cheek", FeTiger.CHEEK_RUB, -2.5, 1.6, 4.2, 15, 30, 55, 100),
            new Scene("spray", FeTiger.SPRAY, 5.5, 1.4, -1.0, 15, 40, 80),
            new Scene("stretch", FeTiger.STRETCH, 5.5, 1.2, 0.5, 25, 45, 65, 85, 105, 140),
            new Scene("roar", FeTiger.ROAR, 3.0, 1.6, 4.0, 10, 30, 60),
            new Scene("groom", FeTiger.GROOM, 4.0, 1.4, 2.5, 20, 40, 75, 150),
            new Scene("walk", -1, 0.0, 1.6, -6.0, 40, 47, 54),
            new Scene("run", -2, 0.0, 1.6, -7.0, 30, 33, 36, 39));

    private static int tick = -1, scene = -1, sceneTick;
    private static volatile PathfinderMob tiger;
    private static volatile boolean ready;

    public static void tick(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        tick++;
        if (tick == 1) {
            mc.options.hideGui = true;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.setDayTime(6000);
                level.setWeatherParameters(100000, 0, false, false);
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.setGameMode(GameType.SPECTATOR);
                arena(level);
                p.teleportTo(level, X0 + 24.5, Y + 3, Z0 + 10, 0, 20);
            });
            return;
        }
        if (tick < 200) return;   // chunks load and draw
        if (scene < 0 || sceneTick > last(SCENES.get(scene)) + 40 + 10) {
            scene++;
            sceneTick = 0;
            if (scene >= SCENES.size()) {
                mc.stop();
                return;
            }
            setUp(server, SCENES.get(scene));
            return;
        }
        if (!ready) return;
        sceneTick++;
        Scene s = SCENES.get(scene);
        if (sceneTick == 40) start(server, s);
        if (sceneTick > 40) follow(server, s);
        for (int i = 0; i < s.shots().length; i++)
            if (sceneTick == 40 + s.shots()[i])
                Screenshot.grab(mc.gameDirectory, "tiger_" + s.name() + "_" + (i + 1) + ".png", mc.getMainRenderTarget(),
                        msg -> LOG.info("[clienttest] {}", msg.getString()));
    }

    private static int last(Scene s) {
        return s.shots()[s.shots().length - 1];
    }

    private static void arena(ServerLevel level) {
        for (int cx = (X0 >> 4) - 2; cx <= ((X0 + 160) >> 4) + 2; cx++)
            for (int cz = (Z0 >> 4) - 2; cz <= ((Z0 + 48) >> 4) + 2; cz++) level.getChunk(cx, cz);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = X0; x < X0 + 160; x++)
            for (int z = Z0; z < Z0 + 48; z++) {
                level.setBlock(p.set(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = Y; y < Y + 10; y++) level.setBlock(p.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
    }

    private static void setUp(MinecraftServer server, Scene s) {
        ready = false;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            if (tiger != null) tiger.discard();
            boolean tree = s.action() >= FeTiger.MARK && s.action() <= FeTiger.SPRAY;
            for (int i = 0; i < 5; i++)
                level.setBlock(TRUNK.above(i), tree ? Blocks.BIRCH_LOG.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            float yaw = 0;   // facing south (+z)
            Vec3 at = tree ? FeTigerGoals.spotFor(TRUNK, s.action() - FeTiger.MARK, yaw)
                    : s.action() < 0 ? new Vec3(X0 + 4.5, Y, Z0 + 24.5) : new Vec3(X0 + 24.5, Y, Z0 + 24.5);
            if (s.action() < 0) yaw = -90;   // walking and running east (+x)
            var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(FeTiger.MOD_ID, "tiger"));
            PathfinderMob t = (PathfinderMob) type.create(level);
            t.moveTo(at.x, at.y, at.z, yaw, 0);
            t.setYHeadRot(yaw);
            t.yBodyRot = yaw;
            t.goalSelector.removeAllGoals(g -> true);
            t.targetSelector.removeAllGoals(g -> true);
            level.addFreshEntity(t);
            tiger = t;
            if (s.action() == FeTiger.GROOM) ((FeTigerAccess) t).blandfordsfox$setActivity(1, 4000);
            camera(server, s);
            LOG.info("[clienttest] tiger scene {} at {}", s.name(), at);
            ready = true;
        });
    }

    private static void camera(MinecraftServer server, Scene s) {
        PathfinderMob t = tiger;
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        double x = t.getX() + s.camX(), y = t.getY() + s.camY(), z = t.getZ() + s.camZ();
        double dx = t.getX() - x, dy = t.getY() + 0.8 - y, dz = t.getZ() - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleportTo(server.overworld(), x, y, z, yaw, pitch);
    }

    private static void start(MinecraftServer server, Scene s) {
        server.execute(() -> {
            if (s.action() > 0) ((FeTigerAccess) tiger).blandfordsfox$startAction(s.action(), FeTiger.ticks(s.action()) > 0 ? FeTiger.ticks(s.action()) : 40);
        });
    }

    private static void follow(MinecraftServer server, Scene s) {
        if (s.action() >= 0) return;
        server.execute(() -> {
            PathfinderMob t = tiger;
            t.getMoveControl().setWantedPosition(X0 + 200, Y, Z0 + 24.5, s.action() == -1 ? 0.7 : FeTiger.RUN_SPEED);
            camera(server, s);
        });
    }
}

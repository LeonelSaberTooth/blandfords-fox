package net.maidens.client.village;

import net.maidens.network.VillageActionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The blocks of each building (asked from the server once, kept for the session), for the pictures on its card. */
public final class TemplateCache {
    public record Placed(BlockState state, int x, int y, int z) {}

    public record Building(int sx, int sy, int sz, List<Placed> visible) {}

    private static final Map<String, Building> CACHE = new HashMap<>();
    private static final Set<String> ASKED = new HashSet<>();

    private TemplateCache() {}

    /** The building, or null while it's still being fetched (the first call asks the server). */
    @Nullable
    public static Building get(long village, String name) {
        Building b = CACHE.get(name);
        if (b == null && ASKED.add(name)) {
            PacketDistributor.sendToServer(new VillageActionPayload(VillageActionPayload.TEMPLATE, village, name, 0, 0, 0, 0));
        }
        return b;
    }

    /** The server sent a building's blocks. Blocks hidden on all six sides are dropped (nobody sees them). */
    public static void receive(CompoundTag t) {
        int[] states = t.getIntArray("States"), positions = t.getIntArray("Positions"), size = t.getIntArray("Size");
        if (size.length != 3 || states.length != positions.length) return;
        Map<Long, BlockState> all = new HashMap<>();
        for (int i = 0; i < states.length; i++) {
            int p = positions[i];
            all.put(BlockPos.asLong(p & 0x3FF, p >> 10 & 0x3FF, p >> 20 & 0x3FF), Block.stateById(states[i]));
        }
        List<Placed> visible = new ArrayList<>();
        for (Map.Entry<Long, BlockState> e : all.entrySet()) {
            BlockPos p = BlockPos.of(e.getKey());
            if (hidden(all, p)) continue;
            visible.add(new Placed(e.getValue(), p.getX(), p.getY(), p.getZ()));
        }
        CACHE.put(t.getString("Name"), new Building(size[0], size[1], size[2], visible));
    }

    private static boolean hidden(Map<Long, BlockState> all, BlockPos p) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockState n = all.get(p.relative(d).asLong());
            if (n == null || !n.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return false;
        }
        return true;
    }
}

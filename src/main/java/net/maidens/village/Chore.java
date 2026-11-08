package net.maidens.village;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** One thing a village needs done: put a missing block back (REPAIR), or take an obstacle off a road (CLEAR). */
public final class Chore {
    public enum Kind { REPAIR, CLEAR }

    public final Kind kind;
    public final BlockPos pos;
    /** For a repair, the block to put back. */
    @Nullable public final BlockState block;
    /** For clearing: whether what's removed drops as items (a player's things on a road) or not (a building site). */
    public final boolean drop;
    final long villageId;
    /** When it was noticed; a maiden takes it on once it's been like this for a while. */
    long since;
    @Nullable UUID takenBy;
    long takenAt;

    Chore(Kind kind, BlockPos pos, @Nullable BlockState block, boolean drop, long villageId, long since) {
        this.kind = kind;
        this.pos = pos;
        this.block = block;
        this.drop = drop;
        this.villageId = villageId;
        this.since = since;
    }
}

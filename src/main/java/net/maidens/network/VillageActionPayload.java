package net.maidens.network;

import net.maidens.MaidensMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: something done on a village screen. village is the village's id; text, number, x, z and extra
 * depend on the action (see the constants).
 */
public record VillageActionPayload(int action, long village, String text, long number, int x, int z, int extra) implements CustomPacketPayload {
    /** Send the village screen again. */
    public static final int REFRESH = 0;
    /** Build template text at x, z with rotation extra (0-3). */
    public static final int BUILD = 1;
    /** Set the stance towards village number to Stance extra. */
    public static final int STANCE = 2;
    /** Send the blocks of template text (for its picture). */
    public static final int TEMPLATE = 3;
    /** Send the world map around x, z, number blocks across. */
    public static final int WORLD_MAP = 4;

    public static final Type<VillageActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "village_action"));
    public static final StreamCodec<FriendlyByteBuf, VillageActionPayload> STREAM_CODEC = CustomPacketPayload.codec(VillageActionPayload::write, VillageActionPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeLong(village);
        buf.writeUtf(text);
        buf.writeLong(number);
        buf.writeInt(x);
        buf.writeInt(z);
        buf.writeVarInt(extra);
    }

    private static VillageActionPayload read(FriendlyByteBuf buf) {
        return new VillageActionPayload(buf.readVarInt(), buf.readLong(), buf.readUtf(), buf.readLong(), buf.readInt(), buf.readInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

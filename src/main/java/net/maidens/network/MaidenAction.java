package net.maidens.network;

import net.maidens.MaidensMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: a button pressed in a maiden's menu. arg is the job for HIRE. */
public record MaidenAction(int entityId, int action, int arg) implements CustomPacketPayload {
    public static final int CHAT = 0, JOKE = 1, COMPLIMENT = 2, FLIRT = 3, GIFT = 4, TRADE = 5, BAG = 6, HIRE = 7, STOP = 8, REFRESH = 9;

    public static final Type<MaidenAction> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "action"));
    public static final StreamCodec<FriendlyByteBuf, MaidenAction> STREAM_CODEC = CustomPacketPayload.codec(MaidenAction::write, MaidenAction::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(action);
        buf.writeVarInt(arg);
    }

    private static MaidenAction read(FriendlyByteBuf buf) {
        return new MaidenAction(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

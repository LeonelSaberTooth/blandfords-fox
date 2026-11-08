package net.maidens.network;

import net.maidens.MaidensMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open (or refresh) a maiden's menu, with everything it shows.
 * married: 0 single, 1 married to you, 2 married to someone else. employer: 0 nobody, 1 you, 2 someone else.
 * shiftLeft is in ticks, -1 for no end.
 */
public record MenuPayload(int entityId, String name, String personality, int affection, int married, int job, int shiftLeft,
                         int employer, String employerName, boolean canOpenBag, boolean freeWork, int rent, String message)
        implements CustomPacketPayload {
    public static final Type<MenuPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "menu"));
    public static final StreamCodec<FriendlyByteBuf, MenuPayload> STREAM_CODEC = CustomPacketPayload.codec(MenuPayload::write, MenuPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(name);
        buf.writeUtf(personality);
        buf.writeVarInt(affection);
        buf.writeVarInt(married);
        buf.writeVarInt(job);
        buf.writeInt(shiftLeft);
        buf.writeVarInt(employer);
        buf.writeUtf(employerName);
        buf.writeBoolean(canOpenBag);
        buf.writeBoolean(freeWork);
        buf.writeVarInt(rent);
        buf.writeUtf(message);
    }

    private static MenuPayload read(FriendlyByteBuf buf) {
        return new MenuPayload(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readInt(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

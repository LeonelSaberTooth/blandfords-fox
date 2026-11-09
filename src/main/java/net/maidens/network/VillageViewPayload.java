package net.maidens.network;

import net.maidens.MaidensMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: something for one of the village screens, as tagged data (see the builder on the server and
 * the screen on the client for the fields). kind: 0 the village screen, 1 a building's blocks (for its picture),
 * 2 the world map.
 */
public record VillageViewPayload(int kind, CompoundTag data) implements CustomPacketPayload {
    public static final int VILLAGE = 0, TEMPLATE = 1, WORLD = 2;
    public static final Type<VillageViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidensMod.MOD_ID, "village_view"));
    public static final StreamCodec<FriendlyByteBuf, VillageViewPayload> STREAM_CODEC = CustomPacketPayload.codec(VillageViewPayload::write, VillageViewPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(kind);
        buf.writeNbt(data);
    }

    private static VillageViewPayload read(FriendlyByteBuf buf) {
        int kind = buf.readVarInt();
        CompoundTag tag = buf.readNbt();
        return new VillageViewPayload(kind, tag == null ? new CompoundTag() : tag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.bensonskiy.scountry.network;

import com.bensonskiy.scountry.SCountry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер → клиент: тайл цветов карты.
 * handle() регистрируется на клиенте в ClientNetworkHandler.
 */
public record ChunkColorResponsePacket(int minCX, int minCZ, int chunksX, int chunksZ,
                                       int[] argb, int pxPerChunk)
        implements CustomPacketPayload {

    public static final Type<ChunkColorResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SCountry.MODID, "chunk_color_resp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkColorResponsePacket> STREAM_CODEC =
            StreamCodec.of(ChunkColorResponsePacket::write, ChunkColorResponsePacket::read);

    private static void write(RegistryFriendlyByteBuf b, ChunkColorResponsePacket p) {
        b.writeVarInt(p.minCX); b.writeVarInt(p.minCZ);
        b.writeVarInt(p.chunksX); b.writeVarInt(p.chunksZ);
        b.writeVarInt(p.pxPerChunk);
        for (int c : p.argb) b.writeInt(c);
    }

    private static ChunkColorResponsePacket read(RegistryFriendlyByteBuf b) {
        int x = b.readVarInt(), z = b.readVarInt();
        int sx = b.readVarInt(), sz = b.readVarInt();
        int px = b.readVarInt();
        int w = sx * px, h = sz * px;
        int[] arr = new int[w * h];
        for (int i = 0; i < arr.length; i++) arr[i] = b.readInt();
        return new ChunkColorResponsePacket(x, z, sx, sz, arr, px);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
package com.bensonskiy.scountry.network;

import com.bensonskiy.scountry.SCountry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Клиент → сервер: запрос тайла цветов карты.
 * handle() регистрируется в ServerNetworkHandler (server-модуль).
 */
public record ChunkColorRequestPacket(int minCX, int minCZ, int chunksX, int chunksZ)
        implements CustomPacketPayload {

    public static final Type<ChunkColorRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SCountry.MODID, "chunk_color_req"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkColorRequestPacket> STREAM_CODEC =
            StreamCodec.of((b, p) -> {
                        b.writeVarInt(p.minCX);
                        b.writeVarInt(p.minCZ);
                        b.writeVarInt(p.chunksX);
                        b.writeVarInt(p.chunksZ);
                    },
                    b -> new ChunkColorRequestPacket(
                            b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
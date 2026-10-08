package com.bensonskiy.scountry.network;

import com.bensonskiy.scountry.SCountry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Пустой пакет-триггер: сервер → клиент «открой карту».
 * handle() регистрируется на клиенте в ClientNetworkHandler.
 */
public record OpenMapPacket() implements CustomPacketPayload {

    public static final Type<OpenMapPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SCountry.MODID, "open_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMapPacket> STREAM_CODEC =
            StreamCodec.unit(new OpenMapPacket());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
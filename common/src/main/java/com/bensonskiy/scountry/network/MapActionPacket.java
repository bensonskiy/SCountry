package com.bensonskiy.scountry.network;

import com.bensonskiy.scountry.SCountry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Клиент → сервер: операция рисования чанков на карте.
 * handle() регистрируется в ServerNetworkHandler (server-модуль).
 */
public record MapActionPacket(String country, boolean add, List<int[]> chunks)
        implements CustomPacketPayload {

    public static final Type<MapActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SCountry.MODID, "map_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapActionPacket> STREAM_CODEC =
            StreamCodec.of(MapActionPacket::write, MapActionPacket::read);

    private static void write(RegistryFriendlyByteBuf b, MapActionPacket p) {
        b.writeUtf(p.country);
        b.writeBoolean(p.add);
        if (p.chunks == null || p.chunks.size() > 1024) {
            throw new IllegalArgumentException("Too many chunks in MapActionPacket");
        }
        b.writeVarInt(p.chunks.size());
        for (int[] c : p.chunks) {
            if (c == null || c.length < 2) throw new IllegalArgumentException("Invalid chunk coordinate");
            b.writeVarInt(c[0]); b.writeVarInt(c[1]);
        }
    }

    private static MapActionPacket read(RegistryFriendlyByteBuf b) {
        String c = b.readUtf();
        boolean add = b.readBoolean();
        int n = b.readVarInt();
        if (n < 0 || n > 1024) throw new IllegalArgumentException("Invalid chunk count: " + n);
        List<int[]> chunks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) chunks.add(new int[]{b.readVarInt(), b.readVarInt()});
        return new MapActionPacket(c, add, chunks);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
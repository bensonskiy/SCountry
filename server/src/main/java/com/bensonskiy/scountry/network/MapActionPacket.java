package com.bensonskiy.scountry.network;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.SCountryServer;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record MapActionPacket(String country, boolean add, List<int[]> chunks)
        implements CustomPacketPayload {

    public static final Type<MapActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SCountry.MODID, "map_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapActionPacket> STREAM_CODEC =
            StreamCodec.of(MapActionPacket::write, MapActionPacket::read);

    private static void write(RegistryFriendlyByteBuf b, MapActionPacket p) {
        b.writeUtf(p.country); b.writeBoolean(p.add);
        b.writeVarInt(p.chunks.size());
        for (int[] c : p.chunks) { b.writeVarInt(c[0]); b.writeVarInt(c[1]); }
    }

    private static MapActionPacket read(RegistryFriendlyByteBuf b) {
        String c = b.readUtf(); boolean add = b.readBoolean();
        int n = b.readVarInt();
        List<int[]> chunks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) chunks.add(new int[]{b.readVarInt(), b.readVarInt()});
        return new MapActionPacket(c, add, chunks);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MapActionPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            CountryManager mgr = SCountryServer.countryManager;
            if (mgr == null) return;
            Country c = mgr.getCountryByName(p.country);
            if (c == null) return;
            if (p.add) mgr.addChunks(c, "overworld", p.chunks);
            else       mgr.removeChunks(c, "overworld", p.chunks);
            PacketDistributor.sendToAllPlayers(CountrySyncPacket.collect());
        });
    }
}
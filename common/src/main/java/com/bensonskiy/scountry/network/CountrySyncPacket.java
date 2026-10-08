package com.bensonskiy.scountry.network;

import com.bensonskiy.scountry.SCountry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Синхронизация списка стран. Общий (client + server).
 * handle() регистрируется на клиенте в ClientNetworkHandler — там ссылка
 * на ClientCountryCache (client-модуль).
 * collect() живёт в CountrySyncBuilder (server-модуль).
 */
public record CountrySyncPacket(List<CountryDTO> countries)
        implements CustomPacketPayload {

    public static final Type<CountrySyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SCountry.MODID, "country_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CountrySyncPacket> STREAM_CODEC =
            StreamCodec.of(CountrySyncPacket::write, CountrySyncPacket::read);

    private static void write(RegistryFriendlyByteBuf b, CountrySyncPacket p) {
        b.writeVarInt(p.countries.size());
        for (CountryDTO c : p.countries) c.write(b);
    }

    private static CountrySyncPacket read(RegistryFriendlyByteBuf b) {
        int n = b.readVarInt();
        List<CountryDTO> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(CountryDTO.read(b));
        return new CountrySyncPacket(l);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
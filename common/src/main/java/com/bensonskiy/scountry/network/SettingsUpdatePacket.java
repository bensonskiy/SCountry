package com.bensonskiy.scountry.network;

import com.bensonskiy.scountry.SCountry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Одно действие в GUI настроек страны. Все действия проверяются на сервере.
 *
 * action:
 *   0 = SET_MODE         a=key, b=value (ALL/ONLY_MEMBERS/NONE)
 *   1 = CREATE_ROLE      a=roleName
 *   2 = DELETE_ROLE      a=roleName
 *   3 = REMOVE_MEMBER    a=playerName
 *   4 = SET_LEADER       a=playerName
 *   5 = SET_MEMBER_ROLE  a=playerName, b=roleName
 *   6 = SET_ROLE_PERM    a=roleName, b="permKey:0|1"
 *
 * handle() регистрируется в ServerNetworkHandler (server-модуль).
 */
public record SettingsUpdatePacket(String country, int action, String a, String b)
        implements CustomPacketPayload {

    public static final Type<SettingsUpdatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SCountry.MODID, "settings_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsUpdatePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.country == null ? "" : p.country);
                        buf.writeVarInt(p.action);
                        buf.writeUtf(p.a == null ? "" : p.a);
                        buf.writeUtf(p.b == null ? "" : p.b);
                    },
                    buf -> new SettingsUpdatePacket(
                            buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readUtf()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
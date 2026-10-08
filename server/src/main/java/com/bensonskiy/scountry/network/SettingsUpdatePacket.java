package com.bensonskiy.scountry.network;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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

    public static void handle(SettingsUpdatePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            CountryManager mgr = SCountry.countryManager;
            if (mgr == null) return;
            Country c = mgr.getCountryByName(p.country());
            if (c == null) return;

            boolean admin = player.hasPermissions(2);
            String pname = player.getGameProfile().getName();
            String role  = c.getRole(pname);
            boolean leaderOrDeputy = "Лидер".equals(role) || "Заместитель".equals(role);

            switch (p.action()) {
                case 0 -> {
                    if (!admin && !leaderOrDeputy) return;
                    if (!p.b().equals("ALL") && !p.b().equals("ONLY_MEMBERS") && !p.b().equals("NONE")) return;
                    switch (p.a()) {
                        case "pvp"       -> c.pvpMode = p.b();
                        case "build"     -> { c.buildMode = p.b(); c.breakMode = p.b(); c.placeMode = p.b(); }
                        case "break"     -> c.breakMode = p.b();
                        case "place"     -> c.placeMode = p.b();
                        case "pickup"    -> c.pickupMode = p.b();
                        case "interact"  -> c.interactMode = p.b();
                        case "explosion" -> c.explosionMode = p.b();
                        default -> { return; }
                    }
                    mgr.saveAll();
                }
                case 1 -> {
                    if (!admin && !leaderOrDeputy) return;
                    if (c.hasCustomRole(p.a())) return;
                    c.createCustomRole(p.a());
                    mgr.saveAll();
                }
                case 2 -> {
                    if (!admin && !leaderOrDeputy) return;
                    if (!c.hasCustomRole(p.a())) return;
                    c.deleteCustomRole(p.a());
                    mgr.saveAll();
                }
                case 3 -> {
                    if (!admin && !leaderOrDeputy) return;
                    if (p.a().equalsIgnoreCase(c.leader) && !admin) return;
                    ServerPlayer tp = player.server.getPlayerList().getPlayerByName(p.a());
                    mgr.removeMember(c, p.a(), tp != null ? tp.getUUID().toString() : null);
                }
                case 4 -> {
                    if (!admin && !pname.equalsIgnoreCase(c.leader)) return;
                    if (!c.isMember(p.a())) return;
                    Country led = mgr.getLedCountry(p.a());
                    if (led != null && led != c) return;
                    c.setLeader(p.a());
                    mgr.saveAll();
                }
                case 5 -> {
                    if (!admin && !leaderOrDeputy) return;
                    if (!c.isMember(p.a())) return;
                    if ("Лидер".equals(p.b()) && !admin && !pname.equalsIgnoreCase(c.leader)) return;
                    if ("Лидер".equals(p.b())) c.setLeader(p.a());
                    else c.setRole(p.a(), p.b());
                    mgr.saveAll();
                }
                case 6 -> {
                    // SET_ROLE_PERM: a=roleName, b="permKey:0|1"
                    if (!admin && !leaderOrDeputy) return;
                    if (!c.hasCustomRole(p.a())) return;
                    int sep = p.b().indexOf(':');
                    if (sep <= 0) return;
                    String perm = p.b().substring(0, sep);
                    if (!Country.ROLE_PERM_KEYS.contains(perm)) return;
                    boolean val = "1".equals(p.b().substring(sep + 1));
                    c.setCustomRolePermission(p.a(), perm, val);
                    mgr.saveAll();
                }
            }
            PacketDistributor.sendToAllPlayers(CountrySyncPacket.collect());
        });
    }
}
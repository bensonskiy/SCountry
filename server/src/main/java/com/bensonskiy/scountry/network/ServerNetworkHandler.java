package com.bensonskiy.scountry.network;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.SCountryServer;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import com.bensonskiy.scountry.network.CountrySyncBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = SCountry.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ServerNetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        reg.playToServer(ChunkColorRequestPacket.TYPE,
                ChunkColorRequestPacket.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> handleChunkColorRequest(p, ctx)));

        reg.playToServer(MapActionPacket.TYPE,
                MapActionPacket.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> handleMapAction(p, ctx)));

        reg.playToServer(SettingsUpdatePacket.TYPE,
                SettingsUpdatePacket.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> handleSettingsUpdate(p, ctx)));
    }

    // ==================== ChunkColorRequest ====================

    private static void handleChunkColorRequest(ChunkColorRequestPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        ChunkColorCache.respond(sp, p);
    }

    // ==================== MapAction ====================

    private static void handleMapAction(MapActionPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) return;
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;
        Country c = mgr.getCountryByName(p.country());
        if (c == null) return;
        if (p.add()) mgr.addChunks(c, "overworld", p.chunks());
        else          mgr.removeChunks(c, "overworld", p.chunks());
        PacketDistributor.sendToAllPlayers(CountrySyncBuilder.collect());
    }

    // ==================== SettingsUpdate ====================

    private static void handleSettingsUpdate(SettingsUpdatePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        CountryManager mgr = SCountryServer.countryManager;
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
                else                       c.setRole(p.a(), p.b());
                mgr.saveAll();
            }
            case 6 -> {
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

        PacketDistributor.sendToAllPlayers(CountrySyncBuilder.collect());
    }
}
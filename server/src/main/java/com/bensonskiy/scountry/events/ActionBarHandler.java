package com.bensonskiy.scountry.events;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.SCountryServer;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class ActionBarHandler {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;

        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;

        String dim = CountryManager.dimPath(player.serverLevel());
        int cx = player.blockPosition().getX() >> 4;
        int cz = player.blockPosition().getZ() >> 4;
        Country country = mgr.getCountryByChunk(dim, cx, cz);

        if (country != null) {
            String playerName = player.getGameProfile().getName();
            String role = country.getRole(playerName);
            String roleStr = (role != null) ? " §7| Роль: " + role : "";
            Component msg = Component.literal("§6[§e" + country.name + "§6]" + roleStr);
            player.connection.send(new ClientboundSetActionBarTextPacket(msg));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CountryManager mgr = SCountryServer.countryManager;
        if (mgr == null) return;

        String name = player.getGameProfile().getName();
        for (Country c : mgr.getCountries().values()) {
            if (c.hasInvite(name)) {
                player.sendSystemMessage(Component.literal(
                        "§6Вас пригласили в страну §e" + c.name + "§6. Введите §f/c accept§6."));
            }
        }
    }
}
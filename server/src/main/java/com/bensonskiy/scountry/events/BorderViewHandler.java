package com.bensonskiy.scountry.events;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Просмотр границ чанков через частицы + BossBar. */
public class BorderViewHandler {

    private static final Map<UUID, ServerBossEvent> activeBossBars = new ConcurrentHashMap<>();
    private static final Set<UUID> activeViewers = ConcurrentHashMap.newKeySet();

    private static final DustParticleOptions DUST_GREEN = new DustParticleOptions(new Vector3f(0.3f, 0.9f, 0.3f), 1.5f);
    private static final DustParticleOptions DUST_RED   = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.2f), 1.5f);

    public static void toggleView(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (activeViewers.contains(uuid)) {
            activeViewers.remove(uuid);
            ServerBossEvent bar = activeBossBars.remove(uuid);
            if (bar != null) bar.removePlayer(player);
            player.sendSystemMessage(Component.literal("§cРежим просмотра границ выключен."));
        } else {
            activeViewers.add(uuid);
            player.sendSystemMessage(Component.literal("§aРежим просмотра границ включён. §7Повторите /c view для отключения."));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        if (!activeViewers.contains(uuid)) return;
        if (player.tickCount % 20 != 0) return;

        CountryManager mgr = SCountry.countryManager;
        if (mgr == null) return;

        String dim = CountryManager.dimPath(player.serverLevel());
        int px = player.blockPosition().getX() >> 4;
        int pz = player.blockPosition().getZ() >> 4;
        int y  = player.blockPosition().getY();

        Set<String> myCountries = new HashSet<>();
        for (Country mine : mgr.getCountriesOfPlayer(player.getGameProfile().getName()))
            myCountries.add(mine.name);

        Country here = mgr.getCountryByChunk(dim, px, pz);
        updateBossBar(player, here);

        for (int cx = px - 8; cx <= px + 8; cx++) {
            for (int cz = pz - 8; cz <= pz + 8; cz++) {
                Country c = mgr.getCountryByChunk(dim, cx, cz);
                if (c == null) continue;
                DustParticleOptions dust = myCountries.contains(c.name) ? DUST_GREEN : DUST_RED;
                drawChunkBorder(player, dust, cx, cz, y);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        activeViewers.remove(uuid);
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerBossEvent bar = activeBossBars.remove(uuid);
            if (bar != null) bar.removePlayer(player);
        }
    }

    private static void drawChunkBorder(ServerPlayer player, DustParticleOptions dust, int cx, int cz, int y) {
        int minX = cx << 4, minZ = cz << 4;
        int maxX = minX + 15, maxZ = minZ + 15;
        for (int x = minX; x <= maxX; x += 2) {
            spawnDust(player, dust, x, y, minZ);
            spawnDust(player, dust, x, y, maxZ);
        }
        for (int z = minZ + 2; z < maxZ; z += 2) {
            spawnDust(player, dust, minX, y, z);
            spawnDust(player, dust, maxX, y, z);
        }
        for (int dy = -2; dy <= 2; dy++) {
            spawnDust(player, dust, minX, y + dy, minZ);
            spawnDust(player, dust, maxX, y + dy, minZ);
            spawnDust(player, dust, minX, y + dy, maxZ);
            spawnDust(player, dust, maxX, y + dy, maxZ);
        }
    }

    private static void spawnDust(ServerPlayer player, DustParticleOptions dust, int x, int y, int z) {
        player.serverLevel().sendParticles(player, dust, true, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
    }

    private static void updateBossBar(ServerPlayer player, Country country) {
        UUID uuid = player.getUUID();
        ServerBossEvent bar = activeBossBars.get(uuid);
        if (country == null) {
            if (bar != null) { bar.removePlayer(player); activeBossBars.remove(uuid); }
            return;
        }
        Component title = Component.literal("§6Страна: §e" + country.name + " §7| §f"
                + country.members.size() + " жителей");
        if (bar == null) {
            bar = new ServerBossEvent(title, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
            bar.setProgress(1.0f);
            bar.addPlayer(player);
            activeBossBars.put(uuid, bar);
        } else bar.setName(title);
    }
}
package com.bensonskiy.scountry;

import com.bensonskiy.scountry.commands.CountryCommand;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import com.bensonskiy.scountry.events.*;
import com.bensonskiy.scountry.network.CountryDTO;
import com.bensonskiy.scountry.network.CountrySyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.File;

@Mod("scountryserver")
public class SCountryServer {

    /** Единственный менеджер стран. */
    public static CountryManager countryManager;

    public SCountryServer(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.register(new ProtectionHandler());
        NeoForge.EVENT_BUS.register(new ClaimBlockHandler());
        NeoForge.EVENT_BUS.register(new ActionBarHandler());
        NeoForge.EVENT_BUS.register(new BorderViewHandler());
        NeoForge.EVENT_BUS.register(new MapUpdateHandler());

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (CountryManager.consumeDirty()) {
            PacketDistributor.sendToAllPlayers(CountrySyncPacket.collect());
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            PacketDistributor.sendToPlayer(p, CountrySyncPacket.collect());
        }
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        File dataFile = new File(dataDir(event.getServer()), "countries.json");
        SCountry.LOGGER.info("[SCountry] Данные стран: {}", dataFile.getAbsolutePath());
        countryManager = new CountryManager(dataFile);
    }

    private void onServerStarted(ServerStartedEvent event) {
        CreateGuardSelfCheck.run();
    }

    private static File dataDir(MinecraftServer server) {
        return server.getServerDirectory().toAbsolutePath()
                .resolve("config").resolve("scountry").toFile();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CountryCommand.register(event.getDispatcher());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (countryManager != null) countryManager.saveAll();
    }

    /** Утилита для CountrySyncPacket: создать снимок страны. */
    public static CountryDTO snapshotOf(Country c) {
        CountryDTO d = new CountryDTO();
        d.name = c.name; d.leader = c.leader; d.color = c.color;
        d.pvpMode = c.pvpMode; d.buildMode = c.buildMode;
        d.breakMode = c.getBreakMode(); d.placeMode = c.getPlaceMode();
        d.pickupMode = c.pickupMode; d.interactMode = c.interactMode;
        d.explosionMode = c.explosionMode;
        d.members = new java.util.HashMap<>(c.members);
        d.modSettings = new java.util.HashMap<>(c.modSettings);
        c.customRoles.forEach((k, v) -> d.customRoles.put(k, new java.util.HashMap<>(v)));
        for (String s : c.chunks) {
            String[] parts = s.split(";");
            if (parts.length == 3 && "overworld".equals(parts[0])) {
                long x = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
                d.chunkKeys.add((x << 32) | (z & 0xFFFFFFFFL));
            }
        }
        return d;
    }
}
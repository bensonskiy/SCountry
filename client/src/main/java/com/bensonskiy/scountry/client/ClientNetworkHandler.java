package com.bensonskiy.scountry.client;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.network.ChunkColorResponsePacket;
import com.bensonskiy.scountry.network.CountrySyncPacket;
import com.bensonskiy.scountry.network.OpenMapPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Регистрация всех пакетов, адресованных клиенту.
 * Здесь можно ссылаться на ClientCountryCache / ClientChunkColorCache / MapScreen —
 * это клиентский модуль.
 */
@EventBusSubscriber(modid = SCountry.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ClientNetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        // CountrySyncPacket: сервер → клиент, обновляем кэш стран.
        reg.playToClient(CountrySyncPacket.TYPE,
                CountrySyncPacket.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientCountryCache.replaceAll(p.countries())));

        // OpenMapPacket: сервер → клиент, открываем экран карты.
        reg.playToClient(OpenMapPacket.TYPE,
                OpenMapPacket.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() ->
                        Minecraft.getInstance().setScreen(new MapScreen())));

        // ChunkColorResponsePacket: сервер → клиент, кладём тайл в кэш текстур.
        reg.playToClient(ChunkColorResponsePacket.TYPE,
                ChunkColorResponsePacket.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientChunkColorCache.put(p)));
    }
}
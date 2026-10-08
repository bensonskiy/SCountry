package com.bensonskiy.scountry.client;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.network.CountryDTO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = SCountry.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (ClientKeybinds.OPEN_MAP.consumeClick()) {
            if (mc.screen != null) return;
            openMapOrSettings(mc);
        }
    }

    /**
     * Нажатие клавиши карты:
     *  - OP (permission >= 2) → полноценная карта с рисованием.
     *  - участник/лидер страны → настройки своей страны.
     *  - ни в одной стране → окно с сообщением.
     */
    private static void openMapOrSettings(Minecraft mc) {
        if (mc.player == null) return;

        if (mc.player.hasPermissions(2)) {
            mc.setScreen(new MapScreen());
            return;
        }

        String name = mc.player.getGameProfile().getName();
        CountryDTO country = ClientCountryCache.countryOfPlayer(name);

        if (country != null) {
            mc.setScreen(new CountrySettingsScreen(country.name, null));
        } else {
            mc.setScreen(new InfoScreen(
                    Component.literal("§cВы не состоите ни в каком государстве"),
                    null));
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientCountryCache.replaceAll(java.util.List.of());
        ClientChunkColorCache.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCountryCache.replaceAll(java.util.List.of());
        ClientChunkColorCache.clear();
    }
}
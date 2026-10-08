package com.bensonskiy.scountry.client;

import com.bensonskiy.scountry.SCountry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(SCountry.MODID)
public class SCountryClient {
    public SCountryClient(IEventBus modBus, ModContainer modContainer) {
        SCountry.LOGGER.info("[SCountry] клиентская часть инициализирована");
    }
}
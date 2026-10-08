package com.bensonskiy.scountry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Общий namespace и логгер. Сам @Mod-класс клиента — SCountryClient в модуле client,
 * сервера — SCountryServer в модуле server.
 */
public final class SCountry {
    public static final String MODID = "scountry";
    public static final Logger LOGGER = LogManager.getLogger("SCountry");

    private SCountry() {}
}
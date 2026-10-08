package com.bensonskiy.scountry.client;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.client.compat.journeymap.SCountryJourneyMapPlugin;
import com.bensonskiy.scountry.network.CountryDTO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClientCountryCache {

    private static final Map<String, CountryDTO> BY_NAME = new ConcurrentHashMap<>();
    private static final Map<Long, CountryDTO> BY_CHUNK = new ConcurrentHashMap<>();

    /** Растёт при каждом replaceAll — GUI сравнивает и перестраивается. */
    private static final AtomicInteger VERSION = new AtomicInteger(0);

    private ClientCountryCache() {}

    public static void replaceAll(List<CountryDTO> list) {
        BY_NAME.clear();
        BY_CHUNK.clear();
        for (CountryDTO c : list) {
            BY_NAME.put(c.name, c);
            for (long k : c.chunkKeys) BY_CHUNK.put(k, c);
        }
        VERSION.incrementAndGet();
        forceRefreshJourneyMapOverlays();
    }

    public static Collection<CountryDTO> all() { return BY_NAME.values(); }
    public static CountryDTO byName(String n) { return BY_NAME.get(n); }

    public static CountryDTO byChunk(int cx, int cz) {
        return BY_CHUNK.get(((long) cx << 32) | (cz & 0xFFFFFFFFL));
    }

    /** Возвращает страну, в которой состоит игрок (регистронезависимо), либо null. */
    public static CountryDTO countryOfPlayer(String playerName) {
        if (playerName == null) return null;
        for (CountryDTO c : BY_NAME.values()) {
            if (c.members == null) continue;
            for (String m : c.members.keySet()) {
                if (m.equalsIgnoreCase(playerName)) return c;
            }
        }
        return null;
    }

    /** Возвращает роль игрока в его стране, либо null. */
    public static String roleOfPlayer(String playerName) {
        CountryDTO c = countryOfPlayer(playerName);
        if (c == null || c.members == null) return null;
        for (Map.Entry<String, String> e : c.members.entrySet()) {
            if (e.getKey().equalsIgnoreCase(playerName)) return e.getValue();
        }
        return null;
    }

    public static int version() { return VERSION.get(); }

    /**
     * Просит JM перестроить оверлеи. Безопасно:
     *  • если JM-плагин ещё не инициализирован (instance == null) — просто выходим;
     *  • если JM ещё не готов (getInstance() == null) — refresh откладывается
     *    планировщиком JourneyMapRefreshScheduler до момента готовности JM;
     *  • если JM не установлен — SCountryJourneyMapPlugin не загрузится, JVM
     *    бросит NoClassDefFoundError, который мы спокойно ловим.
     */
    public static void forceRefreshJourneyMapOverlays() {
        try {
            if (SCountryJourneyMapPlugin.getInstance() == null) return;
            SCountryJourneyMapPlugin.onCountriesUpdated();
        } catch (NoClassDefFoundError ignored) {
            // JourneyMap не установлен — класс SCountryJourneyMapPlugin
            // не может быть загружен. Это нормальная ситуация, тихо выходим.
        } catch (Throwable t) {
            SCountry.LOGGER.debug("[SCountry] JourneyMap refresh skipped: " + t.getMessage());
        }
    }
}
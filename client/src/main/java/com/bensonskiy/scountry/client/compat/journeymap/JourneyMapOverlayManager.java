package com.bensonskiy.scountry.client.compat.journeymap;

import com.bensonskiy.scountry.client.ClientCountryCache;
import com.bensonskiy.scountry.network.CountryDTO;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

public final class JourneyMapOverlayManager {

    private static final Logger LOGGER = LogManager.getLogger("SCountry/JourneyMap");

    /** Устанавливается из SCountryJourneyMapPlugin после initialize(). */
    private static volatile IClientAPI api;

    /** true только после того, как JM сообщил MAPPING_STARTED. */
    private static volatile boolean ready = false;

    /** Если refreshAll() дёрнули до готовности — запомним и выполним позже. */
    private static volatile boolean pendingRefresh = false;

    private static final Map<String, List<PolygonOverlay>> activePolygons = new HashMap<>();
    private static boolean labelSupportWarned = false;

    private JourneyMapOverlayManager() {}

    // ==================== API ДЛЯ ПЛАГИНА ====================

    /** Только сохраняет api. refreshAll() здесь вызывать нельзя. */
    public static void registerApi(IClientAPI clientApi) {
        api = clientApi;
        LOGGER.info("[SCountry] IClientAPI получен: " + (api != null));
    }

    /**
     * Вызывается из SCountryJourneyMapPlugin, когда JM сгенерировал
     * MAPPING_STARTED.
     *
     * ВАЖНО: даже после MAPPING_STARTED JourneymapClient.getInstance() может
     * быть ещё null (JM стреляет событием раньше, чем создаёт клиент).
     * Поэтому НЕ зовём refreshAllInternal() отсюда напрямую — только ставим
     * задачу в JourneyMapRefreshScheduler, который дождётся реальной готовности.
     */
    public static synchronized void markReady() {
        ready = true;
        LOGGER.info("[SCountry] JourneyMap сообщил MAPPING_STARTED — ставим refresh в очередь");
        if (pendingRefresh) {
            pendingRefresh = false;
            JourneyMapRefreshScheduler.schedule(JourneyMapOverlayManager::refreshAllInternal);
        }
    }

    /**
     * Полный пересбор оверлеев. Безопасно вызывать в любой момент.
     * Всегда идёт через планировщик — он проверит, что JM реально инициализирован.
     */
    public static synchronized void refreshAll() {
        if (!ready) {
            pendingRefresh = true;
            LOGGER.debug("[SCountry] refreshAll: JM не готов, откладываем");
            return;
        }
        JourneyMapRefreshScheduler.schedule(JourneyMapOverlayManager::refreshAllInternal);
    }

    // ==================== ВНУТРЕННЯЯ ЛОГИКА ====================

    private static void refreshAllInternal() {
        IClientAPI local = api;
        if (local == null) {
            LOGGER.debug("[SCountry] refreshAllInternal: api == null, пропуск");
            return;
        }

        // Снимаем старые оверлеи.
        for (List<PolygonOverlay> list : activePolygons.values()) {
            for (PolygonOverlay o : list) {
                try { local.remove(o); } catch (Throwable ignored) {}
            }
        }
        activePolygons.clear();

        int total = 0;
        int failures = 0;
        for (CountryDTO country : ClientCountryCache.all()) {
            List<PolygonOverlay> list;
            try {
                list = buildOverlays(country);
            } catch (Throwable t) {
                failures++;
                LOGGER.error("[SCountry] buildOverlays упал для " + country.name + ": " + t, t);
                continue;
            }
            for (PolygonOverlay o : list) {
                try {
                    local.show(o);
                    total++;
                } catch (Throwable t) {
                    failures++;
                    LOGGER.error("[SCountry] api.show() упал: " + t, t);
                }
            }
            activePolygons.put(country.name, list);
        }
        LOGGER.info("[SCountry] полигонов: " + total + " (сбоев: " + failures + ")");
    }

    /** Возвращает центр страны в блоках + её габариты. null если пусто. */
    public static double[] countryCenterBlocks(CountryDTO country) {
        if (country.chunkKeys == null || country.chunkKeys.isEmpty()) return null;
        int minCX = Integer.MAX_VALUE, maxCX = Integer.MIN_VALUE;
        int minCZ = Integer.MAX_VALUE, maxCZ = Integer.MIN_VALUE;
        for (long k : country.chunkKeys) {
            int cx = (int) (k >> 32);
            int cz = (int) (k & 0xFFFFFFFFL);
            if (cx < minCX) minCX = cx;
            if (cx > maxCX) maxCX = cx;
            if (cz < minCZ) minCZ = cz;
            if (cz > maxCZ) maxCZ = cz;
        }
        return new double[]{
                (minCX + maxCX + 1) * 8.0,
                (minCZ + maxCZ + 1) * 8.0,
                (maxCX - minCX + 1) * 16.0,
                (maxCZ - minCZ + 1) * 16.0
        };
    }

    private static List<PolygonOverlay> buildOverlays(CountryDTO country) {
        if (country.chunkKeys == null || country.chunkKeys.isEmpty()) return List.of();
        List<int[]> rects = greedyRectangles(new HashSet<>(country.chunkKeys));

        Color fill    = new Color(country.color, false);
        Color outline = fill.darker();

        List<PolygonOverlay> result = new ArrayList<>(rects.size() + 1);

        // 1) Заливка территории.
        for (int[] r : rects) {
            int x0 = r[0] * 16, x1 = (r[2] + 1) * 16;
            int z0 = r[1] * 16, z1 = (r[3] + 1) * 16;
            MapPolygon polygon = new MapPolygon(
                    new BlockPos(x0, 0, z0),
                    new BlockPos(x1, 0, z0),
                    new BlockPos(x1, 0, z1),
                    new BlockPos(x0, 0, z1));

            ShapeProperties props = new ShapeProperties();
            props.setFillColor(fill.getRGB());
            props.setFillOpacity(0.30f);
            props.setStrokeColor(outline.getRGB());
            props.setStrokeWidth(2f);
            props.setStrokeOpacity(0.9f);

            result.add(new PolygonOverlay("scountry", Level.OVERWORLD, props, polygon));
        }

        // 2) Невидимый полигон в центре — только ради подписи.
        double[] bb = countryCenterBlocks(country);
        if (bb != null) {
            int cx = (int) bb[0], cz = (int) bb[1];
            MapPolygon labelPoly = new MapPolygon(
                    new BlockPos(cx,     0, cz),
                    new BlockPos(cx + 1, 0, cz),
                    new BlockPos(cx + 1, 0, cz + 1),
                    new BlockPos(cx,     0, cz + 1));

            ShapeProperties labelProps = new ShapeProperties();
            labelProps.setFillOpacity(0f);
            labelProps.setStrokeOpacity(0f);

            PolygonOverlay labelOverlay =
                    new PolygonOverlay("scountry_label", Level.OVERWORLD, labelProps, labelPoly);

            if (trySetLabel(labelOverlay, country.name, country.color)) {
                result.add(labelOverlay);
            } else if (!labelSupportWarned) {
                labelSupportWarned = true;
                LOGGER.warn("[SCountry] JM PolygonOverlay не поддерживает setLabel — " +
                        "подписи на карте отключены.");
            }
        }

        return result;
    }

    /** Пытается выставить label на PolygonOverlay через рефлексию. */
    private static boolean trySetLabel(PolygonOverlay overlay, String label, int colorRgb) {
        boolean ok = false;

        try {
            Method m = findMethod(overlay.getClass(), "setLabel", String.class);
            if (m != null) { m.invoke(overlay, label); ok = true; }
        } catch (Throwable ignored) {}

        if (!ok) {
            try {
                Method getProps = findMethod(overlay.getClass(), "getShapeProperties");
                Object props = getProps != null ? getProps.invoke(overlay) : null;
                if (props != null) {
                    Method m = findMethod(props.getClass(), "setLabel", String.class);
                    if (m != null) { m.invoke(props, label); ok = true; }
                }
            } catch (Throwable ignored) {}
        }

        if (ok) {
            try {
                Method m = findMethod(overlay.getClass(), "setLabelColor", int.class);
                if (m != null) m.invoke(overlay, colorRgb);
            } catch (Throwable ignored) {}
        }

        return ok;
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... args) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try { return c.getDeclaredMethod(name, args); }
            catch (NoSuchMethodException e) { c = c.getSuperclass(); }
        }
        try { return cls.getMethod(name, args); }
        catch (NoSuchMethodException e) { return null; }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static List<int[]> greedyRectangles(Set<Long> remaining) {
        List<int[]> rects = new ArrayList<>();
        while (!remaining.isEmpty()) {
            long first = remaining.iterator().next();
            int fx = (int) (first >> 32);
            int fz = (int) (first & 0xFFFFFFFFL);
            int x1 = fx;
            while (remaining.contains(key(x1 + 1, fz))) x1++;
            int z1 = fz;
            outer:
            while (true) {
                int nz = z1 + 1;
                for (int x = fx; x <= x1; x++)
                    if (!remaining.contains(key(x, nz))) break outer;
                z1 = nz;
            }
            for (int x = fx; x <= x1; x++)
                for (int z = fz; z <= z1; z++)
                    remaining.remove(key(x, z));
            rects.add(new int[]{fx, fz, x1, z1});
        }
        return rects;
    }
}
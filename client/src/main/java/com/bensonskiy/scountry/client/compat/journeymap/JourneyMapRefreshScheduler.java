package com.bensonskiy.scountry.client.compat.journeymap;

import com.bensonskiy.scountry.SCountry;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.lang.reflect.Method;

/**
 * Ждёт реальной готовности JourneyMap и только потом выполняет отложенный
 * callback. Проблема: JM вызывает IClientPlugin.initialize() (и стреляет
 * MAPPING_STARTED) раньше, чем его собственный JourneymapClient будет создан.
 * Любой вызов JM API в этот момент падает с NPE:
 *     Cannot invoke "journeymap.client.JourneymapClient.isInitialized()"
 *     because the return value of "journeymap.client.JourneymapClient.getInstance()" is null
 *
 * Решение: ждём RenderGuiEvent.Post, проверяем getInstance() != null И
 * isInitialized() == true. Если JM ещё не готов — просто возвращаемся,
 * не выполняя callback. Он останется в очереди и выполнится на следующем
 * кадре, когда JM наконец загрузится.
 */
@EventBusSubscriber(modid = SCountry.MODID, value = Dist.CLIENT)
public final class JourneyMapRefreshScheduler {

    private static Runnable pendingCallback;
    private static int waitFrames = 0;
    private static final int MAX_WAIT_FRAMES = 1200; // ~20 секунд при 60 FPS

    private JourneyMapRefreshScheduler() {}

    public static synchronized void schedule(Runnable callback) {
        pendingCallback = callback;
        waitFrames = 0;
    }

    public static synchronized void cancel() {
        pendingCallback = null;
        waitFrames = 0;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Runnable cb;
        synchronized (JourneyMapRefreshScheduler.class) {
            if (pendingCallback == null) return;

            // Не пытаемся ничего делать, пока игрок не в мире.
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;

            if (!isJourneyMapReady()) {
                waitFrames++;
                if (waitFrames > MAX_WAIT_FRAMES) {
                    SCountry.LOGGER.warn("[SCountry] JourneyMap не готов за {} кадров — отмена.",
                            MAX_WAIT_FRAMES);
                    pendingCallback = null;
                    waitFrames = 0;
                }
                // JM ещё не готов — просто ждём следующий кадр.
                return;
            }

            cb = pendingCallback;
            pendingCallback = null;
            waitFrames = 0;
        }

        try {
            cb.run();
        } catch (Throwable t) {
            SCountry.LOGGER.error("[SCountry] Ошибка в отложенном вызове JourneyMap: " + t, t);
        }
    }

    /**
     * Проверяет готовность JM через рефлексию.
     * Возвращает true только если:
     *   1. Класс JourneymapClient существует (JM установлен).
     *   2. getInstance() != null (клиент создан).
     *   3. isInitialized() == true (клиент полностью готов).
     */
    private static boolean isJourneyMapReady() {
        try {
            Class<?> cls = Class.forName("journeymap.client.JourneymapClient");
            Method getInstance = cls.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            if (instance == null) return false;

            try {
                Method isInitialized = cls.getMethod("isInitialized");
                Object result = isInitialized.invoke(instance);
                return Boolean.TRUE.equals(result);
            } catch (NoSuchMethodException ignored) {
                // Если метода нет — считаем, что instance != null достаточно.
                return true;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // JM не установлен — не ждём.
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
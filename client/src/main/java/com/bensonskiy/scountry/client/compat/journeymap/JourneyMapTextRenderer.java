package com.bensonskiy.scountry.client.compat.journeymap;

/**
 * Ручной рендер подписей через GuiGraphics удалён. Подписи теперь
 * рисует сам JourneyMap через PolygonOverlay.setLabel(), см.
 * JourneyMapOverlayManager.
 */
public final class JourneyMapTextRenderer {
    private JourneyMapTextRenderer() {}
}
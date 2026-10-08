package com.bensonskiy.scountry.client;

import com.bensonskiy.scountry.network.CountryDTO;
import net.minecraft.client.gui.GuiGraphics;

public final class MapRenderer {

    private MapRenderer() {}

    public static void draw(GuiGraphics g, MapScreen s, int x0, int y0, int x1, int y1) {
        int mapLeft = x0, mapTop = y0, mapRight = x1, mapBottom = y1;

        // Фон под картой
        g.fill(mapLeft, mapTop, mapRight, mapBottom, 0xFF08090B);

        // 1) Тайлы — один blit на тайл, фильтр NEAREST внутри текстуры
        for (ClientChunkColorCache.Tile t : ClientChunkColorCache.tiles()) {
            int tMaxCX = t.minCX + t.chunksX;
            int tMaxCZ = t.minCZ + t.chunksZ;

            int sx0 = (int) Math.floor(s.worldToScreenX(t.minCX << 4));
            int sz0 = (int) Math.floor(s.worldToScreenZ(t.minCZ << 4));
            int sx1 = (int) Math.ceil (s.worldToScreenX(tMaxCX << 4));
            int sz1 = (int) Math.ceil (s.worldToScreenZ(tMaxCZ << 4));

            if (sx1 < mapLeft || sx0 > mapRight) continue;
            if (sz1 < mapTop  || sz0 > mapBottom) continue;

            int drawW = sx1 - sx0;
            int drawH = sz1 - sz0;
            if (drawW <= 0 || drawH <= 0) continue;

            // x, y, screenW, screenH, uOff, vOff, uWidth, vHeight, texW, texH
            g.blit(t.textureId, sx0, sz0, drawW, drawH,
                    0.0f, 0.0f, t.width, t.height, t.width, t.height);
        }

        // 2) Заливка стран
        int cx0 = s.screenToChunkX(mapLeft), cz0 = s.screenToChunkZ(mapTop);
        int cx1 = s.screenToChunkX(mapRight), cz1 = s.screenToChunkZ(mapBottom);
        int tile = s.chunkPixelSize();

        for (int cx = cx0 - 1; cx <= cx1 + 1; cx++) {
            for (int cz = cz0 - 1; cz <= cz1 + 1; cz++) {
                CountryDTO c = ClientCountryCache.byChunk(cx, cz);
                if (c == null) continue;
                int sx = s.chunkToScreenX(cx), sz = s.chunkToScreenZ(cz);
                boolean sel = s.isSelected(c.name);
                int fill = (sel ? 0x55 : 0x2A) << 24 | (c.color & 0xFFFFFF);
                g.fill(sx, sz, sx + tile, sz + tile, fill);
            }
        }

        // 3) Периметр стран (утолщён для выбранной)
        for (int cx = cx0 - 1; cx <= cx1 + 1; cx++) {
            for (int cz = cz0 - 1; cz <= cz1 + 1; cz++) {
                CountryDTO c = ClientCountryCache.byChunk(cx, cz);
                if (c == null) continue;
                int sx = s.chunkToScreenX(cx), sz = s.chunkToScreenZ(cz);
                boolean sel = s.isSelected(c.name);
                int col = 0xFF000000 | (c.color & 0xFFFFFF);
                int thick = sel ? 2 : 1;

                if (s.getCountryAt(cx, cz - 1) != c) g.fill(sx, sz, sx + tile, sz + thick, col);
                if (s.getCountryAt(cx, cz + 1) != c) g.fill(sx, sz + tile - thick, sx + tile, sz + tile, col);
                if (s.getCountryAt(cx - 1, cz) != c) g.fill(sx, sz, sx + thick, sz + tile, col);
                if (s.getCountryAt(cx + 1, cz) != c) g.fill(sx + tile - thick, sz, sx + tile, sz + tile, col);
            }
        }

        // 4) Ховер-подсветка чанка под курсором
        int hx = s.screenToChunkX((int) s.rawMouseX());
        int hz = s.screenToChunkZ((int) s.rawMouseY());
        if (s.rawMouseX() < mapRight && s.rawMouseY() < mapBottom) {
            int sx = s.chunkToScreenX(hx), sz = s.chunkToScreenZ(hz);
            g.fill(sx, sz, sx + tile, sz + 1, 0xAAFFFFFF);
            g.fill(sx, sz + tile - 1, sx + tile, sz + tile, 0xAAFFFFFF);
            g.fill(sx, sz, sx + 1, sz + tile, 0xAAFFFFFF);
            g.fill(sx + tile - 1, sz, sx + tile, sz + tile, 0xAAFFFFFF);
        }
    }
}
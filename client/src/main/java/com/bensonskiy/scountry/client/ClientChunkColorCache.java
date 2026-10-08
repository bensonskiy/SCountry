package com.bensonskiy.scountry.client;

import com.bensonskiy.scountry.network.ChunkColorResponsePacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Хранит пиксельные тайлы карты как готовые текстуры с nearest-фильтром.
 * Заменять на fill-сетку нельзя: при уменьшении масштаба пиксели начинают
 * перекрывать друг друга — карта выглядит размытой и «дышит».
 */
public final class ClientChunkColorCache {

    public static final class Tile {
        public final int minCX, minCZ, chunksX, chunksZ, px, width, height;
        public final int[] argb;
        public final ResourceLocation textureId;
        public final DynamicTexture texture;

        Tile(int minCX, int minCZ, int chunksX, int chunksZ, int px, int[] argb) {
            this.minCX = minCX; this.minCZ = minCZ;
            this.chunksX = chunksX; this.chunksZ = chunksZ;
            this.px = px;
            this.width = chunksX * px;
            this.height = chunksZ * px;
            this.argb = argb;

            NativeImage img = new NativeImage(NativeImage.Format.RGBA, width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int c = argb[y * width + x];
                    int a = (c >>> 24) & 0xFF;
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;
                    // setPixelRGBA принимает ABGR (little-endian).
                    img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }

            this.texture = new DynamicTexture(img);
            // Ключевая строчка: без этого текстура получает GL_LINEAR и карта «мылится».
            this.texture.setFilter(false, false);
            this.textureId = ResourceLocation.fromNamespaceAndPath("scountry",
                    "tile_" + minCX + "_" + minCZ + "_" + System.nanoTime());
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
        }
    }

    private static final List<Tile> TILES = new ArrayList<>();

    private ClientChunkColorCache() {}

    public static synchronized void put(ChunkColorResponsePacket p) {
        TILES.removeIf(t -> {
            boolean same = t.minCX == p.minCX() && t.minCZ == p.minCZ()
                    && t.chunksX == p.chunksX() && t.chunksZ == p.chunksZ();
            if (same) release(t);
            return same;
        });
        TILES.add(new Tile(p.minCX(), p.minCZ(), p.chunksX(), p.chunksZ(), p.pxPerChunk(), p.argb()));
    }

    public static synchronized List<Tile> tiles() { return new ArrayList<>(TILES); }

    public static synchronized boolean hasTile(int minCX, int minCZ, int chunksX, int chunksZ) {
        for (Tile t : TILES)
            if (t.minCX == minCX && t.minCZ == minCZ && t.chunksX == chunksX && t.chunksZ == chunksZ)
                return true;
        return false;
    }

    public static synchronized void clear() {
        for (Tile t : TILES) release(t);
        TILES.clear();
    }

    private static void release(Tile t) {
        try {
            Minecraft.getInstance().getTextureManager().release(t.textureId);
        } catch (Throwable ignored) {}
    }
}
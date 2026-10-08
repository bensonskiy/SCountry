package com.bensonskiy.scountry.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Генерирует пиксельные тайлы карты: {@link #PX}×{@link #PX} пикселей на чанк.
 * Один пиксель — 2×2 блока. Цвет = MapColor верхнего блока, яркость — hillshade
 * по разнице высот с соседями (свет с северо-запада).
 */
public final class ChunkColorCache {

    public static final int PX = 8;             // пикселей на чанк
    private static final int BLOCKS_PER_PX = 16 / PX; // 2 блока на пиксель

    private static final Map<Long, int[]> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CHUNKS_SIDE = 16;
    private static final int VOID_COLOR = 0xFF16191F;

    private ChunkColorCache() {}

    public static void invalidate(int cx, int cz) {
        // Просто инвалидируем кеш региона: проще, чем отслеживать тайлы.
        // Тайлы маленькие (16×16 чанков = 128×128 px), пересчёт дешёвый.
        CACHE.keySet().removeIf(k -> true); // грубо, но работает
    }

    public static void respond(ServerPlayer player, ChunkColorRequestPacket req) {
        if (req == null || player == null) return;
        if (req.chunksX() <= 0 || req.chunksZ() <= 0
                || req.chunksX() > MAX_CHUNKS_SIDE || req.chunksZ() > MAX_CHUNKS_SIDE) return;
        int sx = req.chunksX();
        int sz = req.chunksZ();
        ServerLevel level = player.server.overworld();

        int w = sx * PX, h = sz * PX;
        int[] out = new int[w * h];

        // Высоты: получаем один раз и используем и для цвета, и для hillshade
        int[] heights = new int[w * h];

        for (int pz = 0; pz < h; pz++) {
            for (int px = 0; px < w; px++) {
                int chunkOffX = px / PX;
                int chunkOffZ = pz / PX;
                int inChunkX = (px % PX) * BLOCKS_PER_PX;
                int inChunkZ = (pz % PX) * BLOCKS_PER_PX;
                int cx = req.minCX() + chunkOffX;
                int cz = req.minCZ() + chunkOffZ;
                int wx = (cx << 4) + inChunkX;
                int wz = (cz << 4) + inChunkZ;
                heights[pz * w + px] = topY(level, cx, cz, inChunkX, inChunkZ);
                out[pz * w + px] = rawColor(level, cx, cz, wx, wz, inChunkX, inChunkZ);
            }
        }

        // Hillshade: для каждого пикселя смотрим на высоту северного и западного соседа
        int[] shaded = new int[w * h];
        for (int pz = 0; pz < h; pz++) {
            for (int px = 0; px < w; px++) {
                int i = pz * w + px;
                int here  = heights[i];
                int north = pz > 0     ? heights[(pz - 1) * w + px] : here;
                int west  = px > 0     ? heights[pz * w + (px - 1)] : here;
                int d = 2 * here - north - west;
                int bright = clamp(220 + d * 14, 80, 255);
                shaded[i] = mul(out[i], bright / 255.0);
            }
        }

        PacketDistributor.sendToPlayer(player,
                new ChunkColorResponsePacket(req.minCX(), req.minCZ(), sx, sz, shaded, PX));
    }

    private static int topY(ServerLevel level, int cx, int cz, int inX, int inZ) {
        try {
            if (!level.hasChunk(cx, cz)) return Integer.MIN_VALUE;
            LevelChunk ch = level.getChunk(cx, cz);
            return ch.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inX, inZ);
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private static int rawColor(ServerLevel level, int cx, int cz, int wx, int wz, int inX, int inZ) {
        try {
            if (!level.hasChunk(cx, cz)) return VOID_COLOR;
            LevelChunk ch = level.getChunk(cx, cz);
            int top = ch.getHeight(Heightmap.Types.WORLD_SURFACE, inX, inZ) - 1;
            // Спускаемся вниз, если сверху трава/растение с MapColor.NONE
            for (int y = top; y > top - 8 && y > level.getMinBuildHeight(); y--) {
                BlockPos pos = new BlockPos(wx, y, wz);
                MapColor mc = ch.getBlockState(pos).getMapColor(level, pos);
                if (mc != MapColor.NONE) {
                    int c = mc.col;
                    // Вода темнее по глубине
                    if (mc == MapColor.WATER) {
                        int floor = ch.getHeight(Heightmap.Types.OCEAN_FLOOR, inX, inZ);
                        int depth = Math.max(0, top - floor);
                        double f = 0.6 + 0.4 * Math.exp(-depth * 0.06);
                        return mul(0xFF000000 | c, f);
                    }
                    return 0xFF000000 | c;
                }
            }
            return VOID_COLOR;
        } catch (Exception e) {
            return VOID_COLOR;
        }
    }

    private static int mul(int argb, double f) {
        int a = (argb >>> 24) & 0xFF;
        int r = clamp((int) (((argb >> 16) & 0xFF) * f), 0, 255);
        int g = clamp((int) (((argb >> 8)  & 0xFF) * f), 0, 255);
        int b = clamp((int) (( argb        & 0xFF) * f), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    private static int clamp(int v, int a, int b) { return v < a ? a : (v > b ? b : v); }
}
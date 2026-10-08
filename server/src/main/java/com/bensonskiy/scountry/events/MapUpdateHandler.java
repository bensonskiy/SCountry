package com.bensonskiy.scountry.events;

import com.bensonskiy.scountry.network.ChunkColorCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Инвалидирует кеш цветов чанков при изменениях блоков. */
public class MapUpdateHandler {

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) { mark(e.getLevel(), e.getPos()); }
    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent e) { mark(e.getLevel(), e.getPos()); }

    @SubscribeEvent
    public void onMultiPlace(BlockEvent.EntityMultiPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!"overworld".equals(level.dimension().location().getPath())) return;
        for (BlockSnapshot s : e.getReplacedBlockSnapshots())
            ChunkColorCache.invalidate(s.getPos().getX() >> 4, s.getPos().getZ() >> 4);
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!"overworld".equals(level.dimension().location().getPath())) return;
        for (BlockPos pos : e.getAffectedBlocks())
            ChunkColorCache.invalidate(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private void mark(Object levelAccessor, BlockPos pos) {
        if (!(levelAccessor instanceof ServerLevel level)) return;
        if (pos == null) return;
        if (!"overworld".equals(level.dimension().location().getPath())) return;
        ChunkColorCache.invalidate(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
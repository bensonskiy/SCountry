package com.bensonskiy.scountry.events;

import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** «Блок привата» — minecraft:structure_void, ставит 5×5 чанков вокруг себя. */
public class ClaimBlockHandler {

    private static final int HALF = 2;
    private static final int MIN_GAP = 5;

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getPlacedBlock().is(Blocks.STRUCTURE_VOID)) return;

        CountryManager mgr = SCountry.countryManager;
        if (mgr == null) return;

        String name = player.getGameProfile().getName();
        if (mgr.isPlayerInAnyCountry(name)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§cУ вас уже есть страна — нельзя создать приват."));
            return;
        }

        BlockPos pos = event.getPos();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        String dim = CountryManager.dimPath(level);

        if (!mgr.isChunkFarFromOthers(dim, cx, cz, MIN_GAP, null)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(
                    "§cСлишком близко к чужому привату! Минимум " + MIN_GAP + " чанков."));
            return;
        }

        Country c = mgr.createClaimBlockCountry(name, player.getUUID().toString(), dim, cx, cz, HALF, MIN_GAP);
        if (c == null) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§cНе удалось создать приват."));
            return;
        }
        level.removeBlock(pos, false);

        int side = HALF * 2 + 1;
        player.sendSystemMessage(Component.literal(
                "§bПриват создан! §f" + side + "×" + side + " чанков. Страна: §e" + c.name));
    }
}
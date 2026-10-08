package com.bensonskiy.scountry.events;

import com.bensonskiy.scountry.Config;
import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.SCountryServer;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Единая точка проверки прав для C2S-пакетов Create, обходящих стандартные события. */
public final class ModInteractionGuard {

    private static final Logger LOGGER = LogManager.getLogger("SCountry/Guard");

    private ModInteractionGuard() {}

    public static boolean allow(ServerPlayer player, BlockPos pos, String forcedKey) {
        try {
            if (player == null || pos == null) return true;
            if (player.hasPermissions(2)) return true;
            CountryManager mgr = SCountryServer.countryManager;
            if (mgr == null) return true;

            String key = forcedKey;
            if (key == null) {
                Level level = player.level();
                if (!level.isLoaded(pos)) return true;
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                if (blockId == null || blockId.getNamespace().equals("minecraft")) return true;
                key = blockId.toString();
            }
            key = CountryManager.groupKey(key);

            String dim = player.level().dimension().location().getPath();
            Country country = mgr.getCountryByChunk(dim, pos.getX() >> 4, pos.getZ() >> 4);
            if (country == null) return true;

            boolean allowed = country.playerAllowed(
                    player.getGameProfile().getName(), country.getModInteractMode(key), key);

            if (!allowed) {
                player.sendSystemMessage(Component.literal(Config.messageDenied.get()));
                LOGGER.debug("Mod interaction BLOCKED: {} at {} for {}",
                        key, pos, player.getGameProfile().getName());
            }
            return allowed;
        } catch (Exception e) {
            LOGGER.error("ModInteractionGuard error: " + e.getMessage());
            return true;
        }
    }

    public static boolean allowEntity(ServerPlayer player, Entity target, boolean attack) {
        try {
            if (player == null || target == null) return true;
            if (player.hasPermissions(2)) return true;
            CountryManager mgr = SCountryServer.countryManager;
            if (mgr == null) return true;

            BlockPos pos = target.blockPosition();
            String dim = player.level().dimension().location().getPath();
            Country country = mgr.getCountryByChunk(dim, pos.getX() >> 4, pos.getZ() >> 4);
            if (country == null) return true;

            String mode = attack ? country.pvpMode : country.interactMode;
            boolean allowed = country.playerAllowed(
                    player.getGameProfile().getName(), mode, attack ? "pvp" : "interact");
            if (!allowed) player.sendSystemMessage(Component.literal(Config.messageDenied.get()));
            return allowed;
        } catch (Exception e) {
            LOGGER.error("ModInteractionGuard entity error: " + e.getMessage());
            return true;
        }
    }
}
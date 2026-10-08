package com.bensonskiy.scountry.events;

import com.bensonskiy.scountry.Config;
import com.bensonskiy.scountry.SCountry;
import com.bensonskiy.scountry.data.Country;
import com.bensonskiy.scountry.data.CountryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class ProtectionHandler {

    private static boolean isOp(ServerPlayer p) { return p.hasPermissions(2); }

    private static Country getCountry(ServerPlayer p, BlockPos pos) {
        CountryManager mgr = SCountry.countryManager;
        if (mgr == null) return null;
        return mgr.getCountryByChunk(CountryManager.dimPath(p.serverLevel()), pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static boolean canAccess(ServerPlayer p, Country c, String mode, String permKey) {
        return c.playerAllowed(p.getGameProfile().getName(), mode, permKey);
    }

    private static boolean buildingForbidden(Country c) {
        return "NONE".equals(c.getBreakMode()) || "NONE".equals(c.getPlaceMode());
    }

    private static void deny(ServerPlayer p) { p.sendSystemMessage(Component.literal(Config.messageDenied)); }

    private static String itemKey(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof BlockItem) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return null;
        String itemId = id.toString();
        if (stack.has(DataComponents.FOOD))   return "eat";
        if (itemId.contains("_bucket"))       return "bucket";
        switch (itemId) {
            case "minecraft:flint_and_steel":  return "fire";
            case "minecraft:bone_meal":        return "bonemeal";
            case "minecraft:fishing_rod":      return "fishing";
            case "minecraft:shears":           return "shears";
            case "minecraft:ender_pearl":      return "enderpearl";
            case "minecraft:ender_eye":        return "endereye";
            case "minecraft:snowball":
            case "minecraft:egg":              return "throw";
            case "minecraft:splash_potion":
            case "minecraft:lingering_potion": return "potion";
            case "minecraft:potion":           return "drink";
            case "minecraft:experience_bottle":return "xp_bottle";
            case "minecraft:firework_rocket":  return "firework";
            case "minecraft:bow":
            case "minecraft:crossbow":         return "ranged";
            case "minecraft:trident":          return "trident";
            case "minecraft:lead":             return "lead";
            case "minecraft:name_tag":         return "nametag";
        }
        if (!itemId.startsWith("minecraft:")) return CountryManager.groupKey(itemId);
        return null;
    }

    private static String interactionKey(PlayerInteractEvent.RightClickBlock event) {
        String key = itemKey(event.getItemStack());
        if (key != null) return key;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                event.getLevel().getBlockState(event.getPos()).getBlock());
        if (blockId == null) return null;
        if (!blockId.getNamespace().equals("minecraft"))
            return CountryManager.groupKey(blockId.toString());
        return CountryManager.vanillaBlockKey(blockId.toString());
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isOp(player)) return;
        if (!Config.protectRightClickBlock) return;
        if (event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND && event.getItemStack().isEmpty()) return;

        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();
        String key = interactionKey(event);

        boolean clickableTarget = key != null && key.startsWith("vanilla:") && !player.isShiftKeyDown();
        boolean placing = !stack.isEmpty() && stack.getItem() instanceof BlockItem && !clickableTarget;

        if (placing) {
            Country c = getCountry(player, pos.relative(event.getFace()));
            if (c != null && !canAccess(player, c, c.getPlaceMode(), "place")) {
                event.setCanceled(true); deny(player);
            }
            return;
        }

        Country c = getCountry(player, pos);
        if (c == null) return;
        String mode = (key != null) ? c.getModInteractMode(key) : c.interactMode;
        if (!canAccess(player, c, mode, key != null ? key : "interact")) {
            event.setCanceled(true); deny(player);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isOp(player)) return;
        if (!Config.protectUseItem) return;
        String key = itemKey(event.getItemStack());
        if (key == null) return;
        Country c = getCountry(player, player.blockPosition());
        if (c == null) return;
        String mode = c.getModInteractMode(key);
        if (!canAccess(player, c, mode, key)) { event.setCanceled(true); deny(player); }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isOp(player)) return;
        if (!Config.protectLeftClickBlock) return;
        Country c = getCountry(player, event.getPos());
        if (c != null && !canAccess(player, c, c.getBreakMode(), "break")) {
            event.setCanceled(true); deny(player);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (isOp(player)) return;
        if (!Config.protectBlockBreak) return;
        CountryManager mgr = SCountry.countryManager;
        if (mgr == null) return;
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        Country c = mgr.getCountryByChunk(CountryManager.dimPath(level),
                event.getPos().getX() >> 4, event.getPos().getZ() >> 4);
        if (c != null && !canAccess(player, c, c.getBreakMode(), "break")) {
            event.setCanceled(true); deny(player);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isOp(player)) return;
        if (!Config.protectAttackEntity) return;
        Entity target = event.getTarget();
        Country c = getCountry(player, target.blockPosition());
        if (c != null && !canAccess(player, c, c.pvpMode, "pvp")) {
            event.setCanceled(true); deny(player);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isOp(player)) return;
        if (!Config.protectEntityInteract) return;
        Country c = getCountry(player, event.getTarget().blockPosition());
        if (c != null && !canAccess(player, c, c.interactMode, "interact")) {
            event.setCanceled(true); deny(player);
        }
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (isOp(player)) return;
        if (!Config.protectItemPickup) return;
        Country c = getCountry(player, event.getItemEntity().blockPosition());
        if (c != null && !canAccess(player, c, c.pickupMode, "pickup"))
            event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!Config.protectExplosion) return;
        CountryManager mgr = SCountry.countryManager;
        if (mgr == null) return;
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        String dim = CountryManager.dimPath(level);
        event.getAffectedBlocks().removeIf(pos -> {
            Country c = mgr.getCountryByChunk(dim, pos.getX() >> 4, pos.getZ() >> 4);
            return c != null && "NONE".equals(c.explosionMode);
        });
    }

    @SubscribeEvent
    public void onPiston(PistonEvent.Pre event) {
        CountryManager mgr = SCountry.countryManager;
        if (mgr == null || !Config.protectBlockBreak) return;
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        String dim = CountryManager.dimPath(level);
        try {
            net.minecraft.world.level.block.piston.PistonStructureResolver resolver = event.getStructureHelper();
            if (resolver != null && resolver.resolve()) {
                net.minecraft.core.Direction dir = event.getDirection();
                for (BlockPos pos : resolver.getToPush()) {
                    BlockPos dest = pos.relative(dir);
                    Country c = mgr.getCountryByChunk(dim, dest.getX() >> 4, dest.getZ() >> 4);
                    if (c != null && buildingForbidden(c)) { event.setCanceled(true); return; }
                    c = mgr.getCountryByChunk(dim, pos.getX() >> 4, pos.getZ() >> 4);
                    if (c != null && buildingForbidden(c)) { event.setCanceled(true); return; }
                }
                for (BlockPos pos : resolver.getToDestroy()) {
                    Country c = mgr.getCountryByChunk(dim, pos.getX() >> 4, pos.getZ() >> 4);
                    if (c != null && buildingForbidden(c)) { event.setCanceled(true); return; }
                }
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (!Config.protectBlockBreak) return;
        CountryManager mgr = SCountry.countryManager;
        if (mgr == null) return;
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        String dim = CountryManager.dimPath(level);
        BlockPos pos = event.getPos();
        Country c = mgr.getCountryByChunk(dim, pos.getX() >> 4, pos.getZ() >> 4);
        if (c != null && buildingForbidden(c)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLivingHurt(LivingIncomingDamageEvent event) {
        CountryManager mgr = SCountry.countryManager;
        if (mgr == null) return;
        if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        String dim = CountryManager.dimPath(level);
        Entity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();

        Country c = mgr.getCountryByChunk(dim, victim.blockPosition().getX() >> 4, victim.blockPosition().getZ() >> 4);
        if (c == null) return;

        if (attacker instanceof ServerPlayer player) {
            if (isOp(player) || !Config.protectAttackEntity) return;
            boolean isProjectile = event.getSource().getDirectEntity() != null
                    && event.getSource().getDirectEntity() != player;
            if (!isProjectile) return;
            if (!canAccess(player, c, c.pvpMode, "pvp")) {
                event.setCanceled(true);
                deny(player);
                if (event.getSource().getDirectEntity() != null)
                    event.getSource().getDirectEntity().discard();
            }
        } else if (victim instanceof ServerPlayer targetPlayer && !isOp(targetPlayer)) {
            if (!Config.protectAttackEntity) return;
            if ("NONE".equals(c.pvpMode)) event.setCanceled(true);
        }
    }
}
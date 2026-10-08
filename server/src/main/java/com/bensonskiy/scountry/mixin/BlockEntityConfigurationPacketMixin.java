package com.bensonskiy.scountry.mixin;

import com.bensonskiy.scountry.events.ModInteractionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перехватывает общий обработчик BlockEntityConfigurationPacket в Create.
 * Это базовый класс целого семейства "конфиг"-пакетов, которые применяют изменение
 * на сервере НАПРЯМУЮ, минуя стандартное событие взаимодействия:
 * Speed Controller, schematicannon, threshold switch, sequencer,
 * symmetry wand, station, schedule, display link, factory panel и т.д.
 */
@Mixin(targets = "com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket", remap = false)
public abstract class BlockEntityConfigurationPacketMixin {

    @Shadow @Final protected BlockPos pos;

    @Inject(method = "handle(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void countryProtect$guard(ServerPlayer player, CallbackInfo ci) {
        if (!ModInteractionGuard.allow(player, this.pos, null)) ci.cancel();
    }
}
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
 * Перехватывает RadialWrenchMenuSubmitPacket в Create — поворот блока через радиальное
 * меню ключа. Этот пакет крутит блок на сервере НАПРЯМУЮ, без проверки прав и без
 * стандартного события взаимодействия (главный кейс обхода приватов ключом).
 */
@Mixin(targets = "com.simibubi.create.content.contraptions.wrench.RadialWrenchMenuSubmitPacket", remap = false)
public abstract class RadialWrenchMenuSubmitPacketMixin {

    @Shadow @Final private BlockPos blockPos;

    @Inject(method = "handle(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void countryProtect$guard(ServerPlayer player, CallbackInfo ci) {
        if (!ModInteractionGuard.allow(player, this.blockPos, "create:wrench")) ci.cancel();
    }
}
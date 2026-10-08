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

/** Блокирует установку супер-клея (создание SuperGlueEntity) в чужом привате. */
@Mixin(targets = "com.simibubi.create.content.contraptions.glue.SuperGlueSelectionPacket", remap = false)
public abstract class SuperGlueSelectionPacketMixin {

    @Shadow @Final private BlockPos to;

    @Inject(method = "handle(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void countryProtect$guard(ServerPlayer player, CallbackInfo ci) {
        if (!ModInteractionGuard.allow(player, this.to, "create:super_glue")) ci.cancel();
    }
}
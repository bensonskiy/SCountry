package com.bensonskiy.scountry.mixin;

import com.bensonskiy.scountry.events.ModInteractionGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extendo Grip бьёт/взаимодействует с сущностью на расстоянии через собственный пакет,
 * минуя AttackEntityEvent/EntityInteract — то есть обходит PvP/interact-защиту.
 * Проверяем по позиции цели: удар → pvpMode, взаимодействие → interactMode.
 */
@Mixin(targets = "com.simibubi.create.content.equipment.extendoGrip.ExtendoGripInteractionPacket", remap = false)
public abstract class ExtendoGripInteractionPacketMixin {

    @Shadow @Final private int target;
    @Shadow @Final private InteractionHand hand;

    @Inject(method = "handle(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void countryProtect$guard(ServerPlayer player, CallbackInfo ci) {
        if (player == null) return;
        Entity targetEntity = player.level().getEntity(this.target);
        if (targetEntity == null) return;
        boolean attack = this.hand == null;
        if (!ModInteractionGuard.allowEntity(player, targetEntity, attack)) ci.cancel();
    }
}
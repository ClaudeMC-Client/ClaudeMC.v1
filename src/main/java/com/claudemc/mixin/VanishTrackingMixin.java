package com.claudemc.mixin;

import com.claudemc.module.impl.misc.VanishDetect;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class VanishTrackingMixin {

    @Inject(method = "onEntitySpawn", at = @At("HEAD"), require = 0)
    private void claudemc$onEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        if (packet.getEntityUuid() == null) return;
        VanishDetect.INSTANCE.onPlayerSpawned(
            packet.getId(),
            packet.getEntityUuid(),
            packet.getX(), packet.getY(), packet.getZ()
        );
    }

    @Inject(method = "onEntitiesDestroy", at = @At("HEAD"), require = 0)
    private void claudemc$onEntitiesDestroy(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        VanishDetect.INSTANCE.onEntitiesDestroyed(packet.getEntityIds());
    }

    @Inject(method = "onEntityPosition", at = @At("HEAD"), require = 0)
    private void claudemc$onEntityPosition(EntityPositionS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        VanishDetect.INSTANCE.onGhostEntityPosition(
            packet.getId(),
            packet.getX(), packet.getY(), packet.getZ()
        );
    }

    @Inject(method = "onEntity", at = @At("HEAD"), require = 0)
    private void claudemc$onEntityMove(EntityS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        if (!(packet instanceof EntityS2CPacket.MoveRelative)
         && !(packet instanceof EntityS2CPacket.RotateAndMoveRelative)) return;
        VanishDetect.INSTANCE.onGhostEntityMoveRelative(
            packet.getId(),
            packet.getDeltaX(),
            packet.getDeltaY(),
            packet.getDeltaZ()
        );
    }
}

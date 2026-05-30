package com.claudemc.mixin;

import com.claudemc.module.impl.misc.VanishDetect;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntitiesS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts four types of entity packets to power VanishDetect's
 * packet-leak position tracking:
 *
 *  onPlayerSpawn          → record entity ID → UUID + seed last-known position
 *  onEntitiesDestroy      → detect which removed entities were (tab-list) players
 *  onEntityPosition       → absolute teleport update for ghost entities
 *  onEntity (MoveRelative)→ incremental delta update for ghost entities
 *
 * All injections use require=0 so a wrong method name produces a warning
 * rather than a hard crash, making the mod robust across minor MC updates.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class VanishTrackingMixin {

    // ── Player spawn: record entity ID → UUID ────────────────────────────

    @Inject(method = "onPlayerSpawn", at = @At("HEAD"), require = 0)
    private void claudemc$onPlayerSpawn(PlayerSpawnS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        VanishDetect.INSTANCE.onPlayerSpawned(
            packet.getId(),
            packet.getPlayerUuid(),
            packet.getX(), packet.getY(), packet.getZ()
        );
    }

    // ── Entity removed: check if any were vanish-hiding players ──────────

    @Inject(method = "onEntitiesDestroy", at = @At("HEAD"), require = 0)
    private void claudemc$onEntitiesDestroy(RemoveEntitiesS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        VanishDetect.INSTANCE.onEntitiesDestroyed(
            (net.minecraft.util.collection.IntArrayList) packet.getEntityIds()
        );
    }

    // ── Absolute position update: live-track ghost position ──────────────

    @Inject(method = "onEntityPosition", at = @At("HEAD"), require = 0)
    private void claudemc$onEntityPosition(EntityPositionS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        VanishDetect.INSTANCE.onGhostEntityPosition(
            packet.getId(),
            packet.getX(), packet.getY(), packet.getZ()
        );
    }

    // ── Relative movement: incremental ghost position delta ───────────────
    // EntityS2CPacket.MoveRelative / RotateAndMoveRelative both hit onEntity.

    @Inject(method = "onEntity", at = @At("HEAD"), require = 0)
    private void claudemc$onEntityMove(EntityS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        if (!(packet instanceof EntityS2CPacket.MoveRelative move)
         && !(packet instanceof EntityS2CPacket.RotateAndMoveRelative)) return;

        VanishDetect.INSTANCE.onGhostEntityMoveRelative(
            packet.getId(),
            packet.getDeltaX(),
            packet.getDeltaY(),
            packet.getDeltaZ()
        );
    }
}

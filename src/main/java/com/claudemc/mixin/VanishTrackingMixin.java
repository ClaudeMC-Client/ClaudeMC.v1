package com.claudemc.mixin;

import com.claudemc.module.impl.misc.VanishDetect;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts entity-removal and movement packets for VanishDetect Layer 2.
 * Entity ID→UUID mapping is populated by VanishDetect.onTick() reading live world entities.
 * onEntitySpawn injection removed — the record accessor names changed in 1.20.5+ Yarn.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class VanishTrackingMixin {

    // Log a reflection failure at most once, so a future Yarn field rename is diagnosable
    // instead of silently disabling VanishDetect's packet layer.
    private static final java.util.concurrent.atomic.AtomicBoolean WARNED =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    private static void warnOnce(Exception e) {
        if (WARNED.compareAndSet(false, true)) {
            com.claudemc.ClaudeMCMod.LOGGER.warn(
                "[VanishDetect] packet field reflection failed (mappings may have changed): {}",
                e.toString());
        }
    }

    @Inject(method = "onEntitiesDestroy", at = @At("HEAD"), require = 0)
    private void claudemc$onEntitiesDestroy(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        VanishDetect.INSTANCE.onEntitiesDestroyed(packet.getEntityIds());
    }

    @Inject(method = "onEntityPosition", at = @At("HEAD"), require = 0)
    private void claudemc$onEntityPosition(EntityPositionS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        try {
            // Use the public 1.21.11 accessors directly — mixin method refs are remapped at
            // build time, so this works in production (unlike literal-name reflection). The
            // packet's position now lives in an EntityPosition "change" component, not x/y/z fields.
            var change = packet.change();          // net.minecraft.entity.EntityPosition
            var pos    = change.position();        // Vec3d
            VanishDetect.INSTANCE.onGhostEntityPosition(packet.entityId(), pos.x, pos.y, pos.z);
        } catch (Exception e) { warnOnce(e); }
    }

    @Inject(method = "onEntity", at = @At("HEAD"), require = 0)
    private void claudemc$onEntityMove(EntityS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        if (!(packet instanceof EntityS2CPacket.MoveRelative)
         && !(packet instanceof EntityS2CPacket.RotateAndMoveRelative)) return;
        try {
            // Deltas have public getters; the entity id has no accessor, so read it reflectively
            // with both the yarn (dev) and intermediary (prod) field names.
            short dx = packet.getDeltaX();
            short dy = packet.getDeltaY();
            short dz = packet.getDeltaZ();
            int   id = getIntField(packet.getClass().getSuperclass(), packet, "id", "entityId", "field_12310");
            VanishDetect.INSTANCE.onGhostEntityMoveRelative(id, dx, dy, dz);
        } catch (Exception e) { warnOnce(e); }
    }

    // ── Reflection helpers ────────────────────────────────────────────────

    private int getIntField(Class<?> clazz, Object obj, String... names) throws Exception {
        for (String name : names) {
            try {
                var f = findField(clazz, name);
                f.setAccessible(true);
                return f.getInt(obj);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException("int field not found");
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        while (clazz != null) {
            try { return clazz.getDeclaredField(name); }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}

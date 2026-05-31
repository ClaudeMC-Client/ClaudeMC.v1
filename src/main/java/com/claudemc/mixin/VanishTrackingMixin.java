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

    @Inject(method = "onEntitiesDestroy", at = @At("HEAD"), require = 0)
    private void claudemc$onEntitiesDestroy(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        VanishDetect.INSTANCE.onEntitiesDestroyed(packet.getEntityIds());
    }

    @Inject(method = "onEntityPosition", at = @At("HEAD"), require = 0)
    private void claudemc$onEntityPosition(EntityPositionS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        try {
            // Access fields via reflection to avoid breaking on Yarn accessor name changes
            var clazz = packet.getClass();
            int id = getIntField(clazz, packet, "entityId", "id");
            double x = getDoubleField(clazz, packet, "x");
            double y = getDoubleField(clazz, packet, "y");
            double z = getDoubleField(clazz, packet, "z");
            VanishDetect.INSTANCE.onGhostEntityPosition(id, x, y, z);
        } catch (Exception ignored) {}
    }

    @Inject(method = "onEntity", at = @At("HEAD"), require = 0)
    private void claudemc$onEntityMove(EntityS2CPacket packet, CallbackInfo ci) {
        if (VanishDetect.INSTANCE == null || !VanishDetect.INSTANCE.isEnabled()) return;
        if (!(packet instanceof EntityS2CPacket.MoveRelative)
         && !(packet instanceof EntityS2CPacket.RotateAndMoveRelative)) return;
        try {
            var clazz = packet.getClass().getSuperclass(); // fields on parent
            int id = getIntField(clazz, packet, "entityId", "id");
            short dx = getShortField(clazz, packet, "deltaX", "dx");
            short dy = getShortField(clazz, packet, "deltaY", "dy");
            short dz = getShortField(clazz, packet, "deltaZ", "dz");
            VanishDetect.INSTANCE.onGhostEntityMoveRelative(id, dx, dy, dz);
        } catch (Exception ignored) {}
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

    private double getDoubleField(Class<?> clazz, Object obj, String name) throws Exception {
        var f = findField(clazz, name);
        f.setAccessible(true);
        return f.getDouble(obj);
    }

    private short getShortField(Class<?> clazz, Object obj, String... names) throws Exception {
        for (String name : names) {
            try {
                var f = findField(clazz, name);
                f.setAccessible(true);
                return f.getShort(obj);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException("short field not found");
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        while (clazz != null) {
            try { return clazz.getDeclaredField(name); }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}

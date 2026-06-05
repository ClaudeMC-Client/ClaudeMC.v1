package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Reduces knockback from hits AND explosion blasts (crystals, TNT, beds, anchors).
 * Packet-level interception in ClientPlayNetworkHandlerMixin handles both
 * EntityVelocityUpdateS2CPacket (melee/projectile) and ExplosionS2CPacket.
 */
public class Velocity extends Module {

    public static Velocity INSTANCE;

    public Velocity() {
        super("Velocity", "Reduces knockback from hits and explosions", Category.COMBAT);
        addNumber("H-Mult", 0.0, 0.0, 1.0, 0.1, false); // 0.0 = no horizontal knockback
        addNumber("V-Mult", 1.0, 0.0, 1.0, 0.1, false); // 1.0 = normal vertical
        INSTANCE = this;
    }

    @Override public void onEnable()  {}
    @Override public void onDisable() {}
    @Override public void onTick(MinecraftClient client) {}

    public double getHorizontalMultiplier() {
        try { return Double.parseDouble(getSetting("H-Mult")); } catch (Exception e) { return 0.0; }
    }

    public double getVerticalMultiplier() {
        try { return Double.parseDouble(getSetting("V-Mult")); } catch (Exception e) { return 1.0; }
    }
}

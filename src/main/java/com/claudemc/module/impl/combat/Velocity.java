package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Reduces the velocity (knockback) received when hit.
 * Works by zeroing horizontal velocity each tick while active.
 * The mixin in ClientPlayerEntityMixin handles packet-level reduction.
 */
public class Velocity extends Module {

    public static Velocity INSTANCE;

    public Velocity() {
        super("Velocity", "Reduces knockback taken from hits", Category.COMBAT);
        addSetting("H-Mult", "0.0"); // 0.0 = no horizontal knockback
        addSetting("V-Mult", "1.0"); // 1.0 = normal vertical
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

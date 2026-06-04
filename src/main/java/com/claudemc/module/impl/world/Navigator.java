package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class Navigator extends Module {

    public static Navigator INSTANCE;

    public Navigator() {
        super("Navigator", "Navigates toward target coordinates", Category.WORLD);
        INSTANCE = this;
        addNumber("TargetX", 0, -30000000, 30000000, 1, true);
        addNumber("TargetZ", 0, -30000000, 30000000, 1, true);
        addNumber("Tolerance", 3, 1, 20, 1, true);
        addNumber("Speed", 0.2, 0.05, 1.0, 0.05, false);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        double targetX = parseDouble(getSetting("TargetX"), 0);
        double targetZ = parseDouble(getSetting("TargetZ"), 0);
        double tolerance = parseDouble(getSetting("Tolerance"), 3);
        double speed = parseDouble(getSetting("Speed"), 0.2);

        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist <= tolerance) {
            mc.player.sendMessage(Text.literal("§aNavigator: Arrived at destination!"), false);
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            setEnabled(false);
            return;
        }

        // Calculate direction and apply velocity
        double nx = dx / dist * speed;
        double nz = dz / dist * speed;
        mc.player.setVelocity(nx, mc.player.getVelocity().y, nz);

        // Calculate yaw angle toward target
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        mc.player.setYaw((float) angle);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        }
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}

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
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        double targetX = parseDouble(getSetting("TargetX"), 0);
        double targetZ = parseDouble(getSetting("TargetZ"), 0);
        double tolerance = parseDouble(getSetting("Tolerance"), 3);

        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist <= tolerance) {
            mc.player.sendMessage(Text.literal("§aNavigator: Arrived at destination!"), false);
            mc.options.forwardKey.setPressed(false);
            setEnabled(false);
            return;
        }

        // Calculate yaw angle toward target
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        mc.player.setYaw((float) angle);

        mc.options.forwardKey.setPressed(true);
        mc.player.input.movementForward = 1.0f;
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
        }
        if (mc.player != null) {
            mc.player.input.movementForward = 0;
        }
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}

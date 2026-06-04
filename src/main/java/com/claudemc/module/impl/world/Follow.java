package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public class Follow extends Module {

    public static Follow INSTANCE;

    public Follow() {
        super("Follow", "Follows a named player", Category.WORLD);
        INSTANCE = this;
        addSetting("Target", "");
        addNumber("Distance", 3, 1, 20, 1, true);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        String target = getSetting("Target").toLowerCase();
        if (target.isBlank()) return;
        double minDist = parseInt(getSetting("Distance"), 3);

        AbstractClientPlayerEntity targetPlayer = null;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.getName().getString().toLowerCase().contains(target)) {
                targetPlayer = p;
                break;
            }
        }
        if (targetPlayer == null) return;

        double dx = targetPlayer.getX() - mc.player.getX();
        double dz = targetPlayer.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist <= minDist) return;

        // Point toward target and move forward
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        mc.player.setYaw((float) angle);

        // Simulate forward movement
        mc.player.input.movementForward = 1.0f;
        mc.options.forwardKey.setPressed(true);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.input.movementForward = 0;
        }
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
        }
    }

    private int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }
}

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
        addNumber("Speed", 0.2, 0.05, 1.0, 0.05, false);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        String target = getSetting("Target").toLowerCase();
        if (target.isBlank()) return;
        double minDist = parseDouble(getSetting("Distance"), 3);
        double speed = parseDouble(getSetting("Speed"), 0.2);

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

        if (dist <= minDist) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            return;
        }

        // Normalize and apply velocity toward target
        double nx = dx / dist * speed;
        double nz = dz / dist * speed;

        mc.player.setVelocity(nx, mc.player.getVelocity().y, nz);

        // Turn player toward target
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

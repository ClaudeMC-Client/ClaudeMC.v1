package com.claudemc.module.impl;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimAssistModule extends Module {

    public AimAssistModule() {
        super("AimAssist", "Smoothly rotates toward the nearest valid target", Category.COMBAT);
        addNumber("Range",     20.0, 1.0, 60.0, 0.5,  false);
        addNumber("Smoothing",  0.15, 0.01, 1.0, 0.01, false);
        addMode("Target", "Players", "Players", "Hostile+Players", "Hostile", "All");
    }

    @Override public void onEnable()  {}
    @Override public void onDisable() {}

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        double range     = parseDouble(getSetting("Range"),     20.0);
        double smoothing = parseDouble(getSetting("Smoothing"), 0.15);
        String mode      = getSetting("Target");

        Entity target = findNearest(client, range, mode);
        if (target == null) return;

        Vec3d eye    = client.player.getEyePos();
        Vec3d centre = target.getBoundingBox().getCenter();
        Vec3d delta  = centre.subtract(eye);

        double horiz    = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float wantYaw   = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float wantPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horiz));

        float curYaw   = client.player.getYaw();
        float curPitch = client.player.getPitch();

        float dyaw   = MathHelper.wrapDegrees(wantYaw - curYaw);
        float dpitch = wantPitch - curPitch;

        client.player.setYaw(curYaw   + dyaw   * (float) smoothing);
        client.player.setPitch(MathHelper.clamp(curPitch + dpitch * (float) smoothing, -90f, 90f));
    }

    private Entity findNearest(MinecraftClient client, double range, String mode) {
        Entity nearest  = null;
        double bestDist = range * range;
        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            if (!shouldTarget(le, mode)) continue;
            double d = e.squaredDistanceTo(client.player);
            if (d < bestDist) { bestDist = d; nearest = e; }
        }
        return nearest;
    }

    private boolean shouldTarget(LivingEntity e, String mode) {
        return switch (mode) {
            case "Players"         -> e instanceof PlayerEntity;
            case "Hostile"         -> e instanceof HostileEntity || e instanceof SlimeEntity;
            case "Hostile+Players" -> e instanceof PlayerEntity || e instanceof HostileEntity || e instanceof SlimeEntity;
            default                -> true;
        };
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}

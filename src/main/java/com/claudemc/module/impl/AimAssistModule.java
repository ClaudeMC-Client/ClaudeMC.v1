package com.claudemc.module.impl;

import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashSet;
import java.util.Set;

public class AimAssistModule extends Module {

    // Entity type IDs the user wants to aim at (e.g. "minecraft:player")
    private final Set<String> targetTypes = new LinkedHashSet<>();
    private float smoothing = 0.15f; // 0.01 = very slow, 1.0 = instant snap
    private double range = 20.0;

    public AimAssistModule() {
        super("AimAssist", "Smoothly aims at selected nearby entities", "Combat");
        targetTypes.add("minecraft:player"); // default target
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return; // don't aim while menu is open

        Entity target = findNearest(client);
        if (target == null) return;

        Vec3d eye = client.player.getEyePos();
        Vec3d center = target.getBoundingBox().getCenter();
        Vec3d delta = center.subtract(eye);

        double horizDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float wantYaw   = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float wantPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizDist));

        float curYaw   = client.player.getYaw();
        float curPitch = client.player.getPitch();

        float dyaw   = MathHelper.wrapDegrees(wantYaw - curYaw);
        float dpitch = wantPitch - curPitch;

        client.player.setYaw(curYaw + dyaw * smoothing);
        client.player.setPitch(MathHelper.clamp(curPitch + dpitch * smoothing, -90f, 90f));
    }

    private Entity findNearest(MinecraftClient client) {
        Entity nearest = null;
        double nearestDist = range * range;
        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le) || le.isDead()) continue;
            String id = EntityType.getId(e.getType()).toString();
            if (!targetTypes.contains(id)) continue;
            double d = e.squaredDistanceTo(client.player);
            if (d < nearestDist) { nearestDist = d; nearest = e; }
        }
        return nearest;
    }

    @Override public void onEnable()  {}
    @Override public void onDisable() {}

    public Set<String> getTargetTypes()          { return targetTypes; }
    public void addTarget(String id)             { targetTypes.add(id); }
    public void removeTarget(String id)          { targetTypes.remove(id); }
    public boolean hasTarget(String id)          { return targetTypes.contains(id); }

    public float getSmoothing()                  { return smoothing; }
    public void setSmoothing(float v)            { smoothing = MathHelper.clamp(v, 0.01f, 1.0f); }

    public double getRange()                     { return range; }
    public void setRange(double v)               { range = MathHelper.clamp((float)v, 1.0f, 60.0f); }
}

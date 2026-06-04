package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.BoolSetting;
import com.claudemc.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/**
 * ClickAura — attacks the nearest entity on every left-click.
 * Simpler KillAura variant that only fires when the attack key is pressed.
 * Adapted from Wurst's ClickAuraHack.
 */
public class ClickAura extends Module {

    public static ClickAura INSTANCE;

    private final NumberSetting rangeSetting;
    private final NumberSetting fovSetting;
    private final BoolSetting   targetPlayersSetting;
    private final BoolSetting   targetHostileSetting;
    private final BoolSetting   rotateSetting;

    private long lastAttackMs = 0L;

    public ClickAura() {
        super("ClickAura", "Attacks nearest entity on left-click", Category.COMBAT);
        INSTANCE = this;
        rangeSetting         = addNumber("Range",         5.0, 1.0, 10.0, 0.5, false);
        fovSetting           = addNumber("FOV",           360.0, 30.0, 360.0, 10.0, false);
        targetPlayersSetting = addBool("TargetPlayers",   true);
        targetHostileSetting = addBool("TargetHostile",   true);
        rotateSetting        = addBool("Rotate",          true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Only fire when attack key is held
        if (!client.options.attackKey.isPressed()) return;

        // Rate-limit: don't spam faster than attack cooldown allows
        long now = System.currentTimeMillis();
        if (now - lastAttackMs < 50) return; // 50ms min between attacks

        attack(client);
    }

    private void attack(MinecraftClient client) {
        double range   = rangeSetting.get();
        double fov     = fovSetting.get();
        double rangeSq = range * range;

        Entity best  = null;
        double bestAngle = Double.MAX_VALUE;

        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive()) continue;
            if (!shouldTarget(e)) continue;
            if (e.squaredDistanceTo(client.player) > rangeSq) continue;

            // FOV filter
            if (fov < 360.0) {
                double angle = getAngleToTarget(client, e.getBoundingBox().getCenter());
                if (angle > fov / 2.0) continue;
                if (angle < bestAngle) { bestAngle = angle; best = e; }
            } else {
                double d = e.squaredDistanceTo(client.player);
                if (best == null || d < best.squaredDistanceTo(client.player)) {
                    best = e;
                }
            }
        }

        if (best == null) return;

        if (rotateSetting.get()) faceEntity(client, best);

        client.interactionManager.attackEntity(client.player, best);
        client.player.swingHand(Hand.MAIN_HAND);
        lastAttackMs = System.currentTimeMillis();
    }

    private boolean shouldTarget(Entity e) {
        if (targetPlayersSetting.get() && e instanceof PlayerEntity) return true;
        if (targetHostileSetting.get() && e instanceof HostileEntity) return true;
        return false;
    }

    private double getAngleToTarget(MinecraftClient client, net.minecraft.util.math.Vec3d target) {
        var lookVec  = client.player.getRotationVec(1.0f);
        var toTarget = target.subtract(client.player.getEyePos()).normalize();
        double dot   = lookVec.dotProduct(toTarget);
        return Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
    }

    private void faceEntity(MinecraftClient client, Entity target) {
        var eye  = client.player.getEyePos();
        var tPos = target.getBoundingBox().getCenter();
        var d    = tPos.subtract(eye);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw   = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, h));
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
    }
}

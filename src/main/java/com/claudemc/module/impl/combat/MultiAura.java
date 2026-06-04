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
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * MultiAura — attacks ALL valid entities within range every tick.
 * Unlike KillAura which targets one entity, MultiAura hits everyone.
 * Adapted from Wurst's MultiAuraHack.
 */
public class MultiAura extends Module {

    public static MultiAura INSTANCE;

    private final NumberSetting rangeSetting;
    private final NumberSetting delayMsSetting;
    private final NumberSetting fovSetting;
    private final BoolSetting   targetPlayersSetting;
    private final BoolSetting   targetHostileSetting;
    private final BoolSetting   fullChargeSetting;

    private long lastAttackMs = 0L;

    public MultiAura() {
        super("MultiAura", "Attacks all entities in range simultaneously", Category.COMBAT);
        INSTANCE = this;
        rangeSetting         = addNumber("Range",         5.0,  1.0, 10.0, 0.5, false);
        delayMsSetting       = addNumber("DelayMs",       0.0,  0.0, 1000.0, 50.0, false);
        fovSetting           = addNumber("FOV",           360.0, 30.0, 360.0, 10.0, false);
        targetPlayersSetting = addBool("TargetPlayers",   true);
        targetHostileSetting = addBool("TargetHostile",   true);
        fullChargeSetting    = addBool("FullCharge",      false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Delay check
        if (delayMsSetting.get() > 0) {
            long now = System.currentTimeMillis();
            if (now - lastAttackMs < delayMsSetting.get()) return;
        }

        // Full charge check
        if (fullChargeSetting.get() && client.player.getAttackCooldownProgress(0f) < 1.0f) return;

        double range   = rangeSetting.get();
        double fov     = fovSetting.get();
        double rangeSq = range * range;

        List<Entity> targets = new ArrayList<>();

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
            }

            targets.add(e);
        }

        if (targets.isEmpty()) return;

        // Attack all targets
        for (Entity target : targets) {
            // Face each target briefly before attacking
            faceEntity(client, target);
            client.interactionManager.attackEntity(client.player, target);
        }

        // Swing once after all attacks
        client.player.swingHand(Hand.MAIN_HAND);
        lastAttackMs = System.currentTimeMillis();
    }

    private boolean shouldTarget(Entity e) {
        if (targetPlayersSetting.get() && e instanceof PlayerEntity) return true;
        if (targetHostileSetting.get() && e instanceof HostileEntity) return true;
        return false;
    }

    private double getAngleToTarget(MinecraftClient client, Vec3d target) {
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

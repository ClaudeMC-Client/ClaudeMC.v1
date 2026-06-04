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

/**
 * KillauraLegit — slower, human-like KillAura with rotation smoothing.
 * Adapted from Wurst's KillauraLegitHack.
 *
 * Key differences from KillAura:
 * - Smooth rotation (turns towards target gradually)
 * - Attack speed randomization to evade anti-cheat
 * - Only attacks when actually facing the target
 * - Line-of-sight check
 */
public class KillauraLegit extends Module {

    public static KillauraLegit INSTANCE;

    private final NumberSetting rangeSetting;
    private final NumberSetting rotSpeedSetting;
    private final NumberSetting attackDelayMsSetting;
    private final NumberSetting speedRandMsSetting;
    private final NumberSetting fovSetting;
    private final BoolSetting   targetPlayersSetting;
    private final BoolSetting   targetHostileSetting;
    private final BoolSetting   checkLosSetting;

    private long   lastAttackMs   = 0L;
    private long   nextAttackDelay = 300L;
    private Entity currentTarget  = null;

    private static final java.util.Random RANDOM = new java.util.Random();

    public KillauraLegit() {
        super("KillauraLegit", "Human-like KillAura with smooth rotation and attack timing", Category.COMBAT);
        INSTANCE = this;
        rangeSetting        = addNumber("Range",        4.25, 1.0, 4.25, 0.05, false);
        rotSpeedSetting     = addNumber("RotSpeed",     600.0, 10.0, 3600.0, 10.0, false); // degrees/sec
        attackDelayMsSetting= addNumber("AttackDelay",  300.0, 100.0, 1000.0, 50.0, false);
        speedRandMsSetting  = addNumber("SpeedRandMs",  100.0, 0.0, 1000.0, 50.0, false);
        fovSetting          = addNumber("FOV",          360.0, 30.0, 360.0, 10.0, false);
        targetPlayersSetting= addBool("TargetPlayers",  true);
        targetHostileSetting= addBool("TargetHostile",  false);
        checkLosSetting     = addBool("CheckLOS",       true);
    }

    @Override
    public void onEnable() {
        currentTarget = null;
        recalcDelay();
    }

    @Override
    public void onDisable() {
        currentTarget = null;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        double range   = rangeSetting.get();
        double fov     = fovSetting.get();
        double rangeSq = range * range;

        // Find target
        currentTarget = findTarget(client, rangeSq, fov);
        if (currentTarget == null) return;

        // Smooth rotation towards target
        boolean facingTarget = smoothRotate(client, currentTarget);

        // Only attack if: facing target + cooldown ready + delay elapsed
        if (!facingTarget) return;
        if (client.player.getAttackCooldownProgress(0f) < 0.9f) return;

        long now = System.currentTimeMillis();
        if (now - lastAttackMs < nextAttackDelay) return;

        // Optional line-of-sight check
        if (checkLosSetting.get() && !hasLineOfSight(client, currentTarget)) return;

        client.interactionManager.attackEntity(client.player, currentTarget);
        client.player.swingHand(Hand.MAIN_HAND);
        lastAttackMs = now;
        recalcDelay();
    }

    private Entity findTarget(MinecraftClient client, double rangeSq, double fov) {
        Entity best = null;
        double bestAngle = Double.MAX_VALUE;

        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive()) continue;
            if (!shouldTarget(e)) continue;
            if (e.squaredDistanceTo(client.player) > rangeSq) continue;

            double angle = getAngleToTarget(client, e.getBoundingBox().getCenter());
            if (fov < 360.0 && angle > fov / 2.0) continue;
            if (angle < bestAngle) { bestAngle = angle; best = e; }
        }
        return best;
    }

    private boolean shouldTarget(Entity e) {
        if (targetPlayersSetting.get() && e instanceof PlayerEntity) return true;
        if (targetHostileSetting.get() && e instanceof HostileEntity) return true;
        return false;
    }

    /**
     * Smoothly rotate toward the target. Returns true if we're now facing the target.
     */
    private boolean smoothRotate(MinecraftClient client, Entity target) {
        Vec3d center = target.getBoundingBox().getCenter();
        var eye = client.player.getEyePos();
        var d = center.subtract(eye);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);

        float targetYaw   = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(d.y, h));

        float currentYaw   = client.player.getYaw();
        float currentPitch = client.player.getPitch();

        // Max rotation per tick (rotSpeed degrees/sec / 20 ticks per sec)
        float maxRotPerTick = (float) (rotSpeedSetting.get() / 20.0);

        float newYaw   = limitRotation(currentYaw,   targetYaw,   maxRotPerTick);
        float newPitch = limitRotation(currentPitch, targetPitch, maxRotPerTick);

        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);

        // Check if we're now approximately facing the target
        float yawDiff   = Math.abs(wrapDegrees(newYaw - targetYaw));
        float pitchDiff = Math.abs(newPitch - targetPitch);
        return yawDiff < 5f && pitchDiff < 5f;
    }

    private float limitRotation(float current, float target, float maxDelta) {
        float diff = wrapDegrees(target - current);
        if (Math.abs(diff) <= maxDelta) return target;
        return current + Math.signum(diff) * maxDelta;
    }

    private float wrapDegrees(float deg) {
        while (deg > 180f) deg -= 360f;
        while (deg < -180f) deg += 360f;
        return deg;
    }

    private double getAngleToTarget(MinecraftClient client, Vec3d target) {
        var lookVec  = client.player.getRotationVec(1.0f);
        var toTarget = target.subtract(client.player.getEyePos()).normalize();
        double dot   = lookVec.dotProduct(toTarget);
        return Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
    }

    private boolean hasLineOfSight(MinecraftClient client, Entity target) {
        return client.player.canSee(target);
    }

    private void recalcDelay() {
        double base = attackDelayMsSetting.get();
        double rand = speedRandMsSetting.get();
        nextAttackDelay = (long) (base + (rand > 0 ? (RANDOM.nextDouble() * 2 - 1) * rand : 0));
    }
}

package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.NumberSetting;
import com.claudemc.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public class KillAura extends Module {

    private final NumberSetting rangeSetting;
    private final NumberSetting delayMsSetting;
    private final BoolSetting   rotateSetting;
    private final BoolSetting   fullChargeSetting;

    private long lastAttackMs = 0L;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities", Category.COMBAT);
        rangeSetting      = addNumber("Range",      4.0,  1.0, 10.0, 0.5, false);
        delayMsSetting    = addNumber("DelayMs",    0.0,  0.0, 1000.0, 50.0, false);
        rotateSetting     = addBool("Rotate",       true);
        fullChargeSetting = addBool("FullCharge",   false);
        addMode("Target", "Hostile+Players", "Hostile+Players", "Players", "Hostile", "All");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Delay check
        if (delayMsSetting.get() > 0 && System.currentTimeMillis() - lastAttackMs < delayMsSetting.get()) return;

        // Full charge check
        if (fullChargeSetting.get() && client.player.getAttackCooldownProgress(0f) < 1.0f) return;

        double range  = rangeSetting.get();
        String target = getSetting("Target");

        Entity best = null;
        double bestDist = range * range;

        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive()) continue;
            if (!shouldTarget(le, target)) continue;

            double d = e.squaredDistanceTo(client.player);
            if (d < bestDist) { bestDist = d; best = e; }
        }

        if (best == null) return;

        if (rotateSetting.get()) {
            faceEntity(client, best);
        }
        client.interactionManager.attackEntity(client.player, best);
        client.player.swingHand(Hand.MAIN_HAND);
        lastAttackMs = System.currentTimeMillis();
    }

    private boolean shouldTarget(LivingEntity e, String mode) {
        return switch (mode) {
            case "Players"       -> e instanceof PlayerEntity;
            case "Hostile"       -> e instanceof HostileEntity || e instanceof SlimeEntity;
            case "Hostile+Players" -> e instanceof HostileEntity || e instanceof PlayerEntity || e instanceof SlimeEntity;
            default              -> true;
        };
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

package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.BoolSetting;
import com.claudemc.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/**
 * Automatically attacks nearby entities.
 * Attack logic cross-referenced against Wurst7 KillauraHack and Meteor KillAura
 * for correct MC 1.21.x API usage (interactionManager.attackEntity, attack cooldown).
 */
public class KillAura extends Module {

    private final NumberSetting rangeSetting;
    private final BoolSetting   rotateSetting;
    private final BoolSetting   fullChargeSetting;
    private final BoolSetting   wallCheckSetting;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities", Category.COMBAT);
        rangeSetting      = addNumber("Range",       4.0,  1.0, 10.0, 0.5, false);
        rotateSetting     = addBool("Rotate",        true);
        fullChargeSetting = addBool("FullCharge",    false);
        wallCheckSetting  = addBool("WallCheck",     false);
        addMode("Target", "Hostile+Players", "Hostile+Players", "Players", "Hostile", "All");
        addNumber("FOV",  360, 30, 360, 10, true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Respect MC attack cooldown (same check as Meteor KillAura)
        if (fullChargeSetting.get() && client.player.getAttackCooldownProgress(0f) < 1.0f) return;

        double range    = rangeSetting.get();
        String target   = getSetting("Target");
        double fovLimit = Double.parseDouble(getSetting("FOV"));

        Entity best = null;
        double bestDist = range * range;

        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive()) continue;
            if (!shouldTarget(le, target)) continue;

            double d = e.squaredDistanceTo(client.player);
            if (d >= bestDist) continue;

            // FOV check (Wurst KillauraHack uses same logic)
            if (fovLimit < 360) {
                double angle = getAngleTo(client, e);
                if (angle > fovLimit / 2.0) continue;
            }

            // Optional wall check
            if (wallCheckSetting.get() && !client.player.canSee(e)) continue;

            bestDist = d;
            best = e;
        }

        if (best == null) return;

        if (rotateSetting.get()) {
            faceEntity(client, best);
        }

        // Use interactionManager.attackEntity — correct Fabric API (same as Wurst's gameMode.attack)
        client.interactionManager.attackEntity(client.player, best);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean shouldTarget(LivingEntity e, String mode) {
        return switch (mode) {
            case "Players"         -> e instanceof PlayerEntity;
            case "Hostile"         -> e instanceof HostileEntity || e instanceof SlimeEntity;
            case "Hostile+Players" -> e instanceof HostileEntity || e instanceof PlayerEntity || e instanceof SlimeEntity;
            default                -> true;
        };
    }

    private double getAngleTo(MinecraftClient client, Entity target) {
        var eye  = client.player.getEyePos();
        var tPos = target.getBoundingBox().getCenter();
        var d    = tPos.subtract(eye).normalize();
        var look = client.player.getRotationVec(1.0f).normalize();
        double dot = d.x * look.x + d.y * look.y + d.z * look.z;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
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

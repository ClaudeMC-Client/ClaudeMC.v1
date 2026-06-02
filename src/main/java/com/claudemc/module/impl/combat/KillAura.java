package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public class KillAura extends Module {

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities", Category.COMBAT);
        addNumber("Range",      4.0, 2.0, 6.0, 0.5, false);
        addMode("Target", "Hostile+Players", "Hostile+Players", "Players", "Hostile", "All");
        addBool("Rotate",       true);
        // When true, only swing once the vanilla attack cooldown has fully recharged, so each
        // hit deals full (sharpness/charged) damage instead of weak no-cooldown spam taps.
        addBool("FullCharge",   true);
        // Extra delay (ms) between attacks on top of the cooldown, for a configurable CPS cap.
        addNumber("DelayMs",   50, 0, 1000, 10, true);
    }

    private long lastAttack = 0L;

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        long now = System.currentTimeMillis();
        if (now - lastAttack < parseDouble(getSetting("DelayMs"), 50)) return;

        // Respect the vanilla 1.9+ attack cooldown so hits actually land full damage.
        if (Boolean.parseBoolean(getSetting("FullCharge"))
                && client.player.getAttackCooldownProgress(0f) < 1.0f) return;

        double range  = parseDouble(getSetting("Range"), 4.0);
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

        if (Boolean.parseBoolean(getSetting("Rotate"))) {
            faceEntity(client, best);
        }
        client.interactionManager.attackEntity(client.player, best);
        client.player.swingHand(Hand.MAIN_HAND);
        client.player.resetLastAttackedTicks();   // restart the cooldown bar
        lastAttack = now;
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

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}

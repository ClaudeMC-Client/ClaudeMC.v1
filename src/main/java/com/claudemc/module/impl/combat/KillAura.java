package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Meteor Client-style KillAura.
 * Targets: Players / Mobs / All — sorted by Closest / LowestHP / HighestHP / Angle.
 * Only attacks when the attack cooldown is fully charged.
 */
public class KillAura extends Module {

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities", Category.COMBAT);
        addNumber("Range",    4.0, 1.0, 10.0, 0.5, false);
        addMode("Target",     "Players", "Players", "Mobs", "All");
        addMode("Sort",       "Closest", "Closest", "LowestHP", "HighestHP", "Angle");
        addBool("Rotate",     true);
        addBool("CheckWalls", false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Meteor: only attack on full cooldown charge
        if (client.player.getAttackCooldownProgress(0f) < 1.0f) return;

        double range  = Double.parseDouble(getSetting("Range"));
        String target = getSetting("Target");
        String sort   = getSetting("Sort");

        List<LivingEntity> targets = new ArrayList<>();
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le == client.player) continue;
            if (!le.isAlive()) continue;
            if (le.squaredDistanceTo(client.player) > range * range) continue;
            if (!shouldTarget(le, target)) continue;
            if (Boolean.parseBoolean(getSetting("CheckWalls")) && !client.player.canSee(le)) continue;
            targets.add(le);
        }
        if (targets.isEmpty()) return;

        switch (sort) {
            case "LowestHP"  -> targets.sort(Comparator.comparingDouble(LivingEntity::getHealth));
            case "HighestHP" -> targets.sort(Comparator.comparingDouble(LivingEntity::getHealth).reversed());
            case "Angle"     -> targets.sort(Comparator.comparingDouble(e -> getAngleTo(client, e)));
            default          -> targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)));
        }

        LivingEntity best = targets.get(0);

        if (Boolean.parseBoolean(getSetting("Rotate"))) faceEntity(client, best);

        client.interactionManager.attackEntity(client.player, best);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean shouldTarget(LivingEntity e, String mode) {
        return switch (mode) {
            case "Players" -> e instanceof PlayerEntity;
            case "Mobs"    -> e instanceof HostileEntity || e instanceof SlimeEntity || e instanceof AnimalEntity;
            default        -> true; // All
        };
    }

    private double getAngleTo(MinecraftClient client, Entity target) {
        var eye  = client.player.getEyePos();
        var tPos = target.getBoundingBox().getCenter();
        var d    = tPos.subtract(eye).normalize();
        var look = client.player.getRotationVec(1.0f).normalize();
        double dot = Math.max(-1, Math.min(1, d.x * look.x + d.y * look.y + d.z * look.z));
        return Math.toDegrees(Math.acos(dot));
    }

    private void faceEntity(MinecraftClient client, Entity target) {
        var eye  = client.player.getEyePos();
        var tPos = target.getBoundingBox().getCenter();
        var d    = tPos.subtract(eye);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        client.player.setYaw((float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f);
        client.player.setPitch((float) -Math.toDegrees(Math.atan2(d.y, h)));
    }
}

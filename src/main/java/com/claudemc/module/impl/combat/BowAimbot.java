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
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/**
 * BowAimbot — auto-aims bow/crossbow at entities, accounting for
 * arrow drop (gravity) and target movement prediction.
 * Adapted from Wurst's BowAimbotHack.
 */
public class BowAimbot extends Module {

    public static BowAimbot INSTANCE;

    private final NumberSetting rangeSetting;
    private final NumberSetting predictSetting;
    private final BoolSetting   targetPlayersSetting;
    private final BoolSetting   targetHostileSetting;

    public BowAimbot() {
        super("BowAimbot", "Auto-aims bow at entities with arrow drop correction", Category.COMBAT);
        INSTANCE = this;
        rangeSetting         = addNumber("Range",          64.0, 1.0, 100.0, 1.0, false);
        predictSetting       = addNumber("Prediction",     0.2,  0.0, 2.0,   0.05, false);
        targetPlayersSetting = addBool("TargetPlayers",    true);
        targetHostileSetting = addBool("TargetHostile",    false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Check if holding a bow or crossbow
        var mainItem = client.player.getMainHandStack().getItem();
        boolean isBow      = mainItem instanceof BowItem;
        boolean isCrossbow = mainItem instanceof CrossbowItem;

        if (!isBow && !isCrossbow) return;

        // For bow: must be actively drawing
        if (isBow && !client.player.isUsingItem()) return;

        // For crossbow: must be loaded
        if (isCrossbow && !CrossbowItem.isCharged(client.player.getMainHandStack())) return;

        // Calculate velocity (charge) for bow
        float velocity = 1.0f;
        if (isBow) {
            int useTicks = client.player.getItemUseTimeLeft();
            // bow use time is 72000 ticks max, getItemUseTimeLeft decreases
            int chargedTicks = 72000 - useTicks;
            velocity = chargedTicks / 20.0f;
            velocity = (velocity * velocity + velocity * 2) / 3;
            if (velocity > 1) velocity = 1;
        }

        // Find best target
        Entity target = findTarget(client);
        if (target == null) return;

        aimAtTarget(client, target, velocity);
    }

    private Entity findTarget(MinecraftClient client) {
        double rangeSq = rangeSetting.get() * rangeSetting.get();
        Entity best = null;
        double bestAngle = Double.MAX_VALUE;

        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive()) continue;
            if (!shouldTarget(e)) continue;
            if (e.squaredDistanceTo(client.player) > rangeSq) continue;

            // Select by smallest angle to look direction
            Vec3d center = e.getBoundingBox().getCenter();
            double angle = getAngleToLookVec(client, center);
            if (angle < bestAngle) { bestAngle = angle; best = e; }
        }
        return best;
    }

    private boolean shouldTarget(Entity e) {
        if (targetPlayersSetting.get() && e instanceof PlayerEntity) return true;
        if (targetHostileSetting.get() && e instanceof HostileEntity) return true;
        return false;
    }

    private void aimAtTarget(MinecraftClient client, Entity target, float velocity) {
        double predict = predictSetting.get();

        var eyePos  = client.player.getEyePos();
        var tCenter = target.getBoundingBox().getCenter();

        double dist = eyePos.distanceTo(tCenter);

        // Movement prediction: extrapolate target position
        double d = dist * predict;
        double posX = target.getX() + (target.getX() - target.lastX) * d - client.player.getX();
        double posY = target.getY() + (target.getY() - target.lastY) * d
                + target.getHeight() * 0.5
                - client.player.getY() - client.player.getStandingEyeHeight();
        double posZ = target.getZ() + (target.getZ() - target.lastZ) * d - client.player.getZ();

        // Yaw
        float neededYaw = (float) Math.toDegrees(Math.atan2(posZ, posX)) - 90f;
        client.player.setYaw(neededYaw);

        // Pitch with gravity correction
        double hDist = Math.sqrt(posX * posX + posZ * posZ);
        double hDistSq = hDist * hDist;
        float g = 0.006f;
        float velSq = velocity * velocity;
        float velPow4 = velSq * velSq;
        double discriminant = velPow4 - g * (g * hDistSq + 2 * posY * velSq);

        if (discriminant < 0) {
            // No valid arc; just aim straight at the target
            double h2 = Math.sqrt(posX * posX + posZ * posZ);
            float pitch = (float) -Math.toDegrees(Math.atan2(posY, h2));
            client.player.setPitch(pitch);
        } else {
            float neededPitch = (float) -Math.toDegrees(
                    Math.atan((velSq - Math.sqrt(discriminant)) / (g * hDist)));
            client.player.setPitch(neededPitch);
        }
    }

    private double getAngleToLookVec(MinecraftClient client, Vec3d target) {
        var lookVec = client.player.getRotationVec(1.0f);
        var toTarget = target.subtract(client.player.getEyePos()).normalize();
        double dot = lookVec.dotProduct(toTarget);
        return Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
    }
}

package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Protects a specified player by teleporting between them and threatening entities.
 * (Client-side approximation — moves the local player toward the target.)
 */
public class Protect extends Module {

    public static Protect INSTANCE;

    public Protect() {
        super("Protect", "Moves toward a specified player to protect them", Category.PLAYER);
        addSetting("Target", "");
        addNumber("Range", 8, 1, 20, 1, true);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        String target = getSetting("Target").trim();
        if (target.isEmpty()) return;

        double range = parseDouble(getSetting("Range"), 8.0);

        PlayerEntity targetPlayer = null;
        for (Entity e : client.world.getEntities()) {
            if (e instanceof PlayerEntity p && p != client.player
                    && p.getName().getString().equalsIgnoreCase(target)) {
                targetPlayer = p;
                break;
            }
        }

        if (targetPlayer == null) return;
        if (client.player.distanceTo(targetPlayer) > range) return;

        // Move toward the target player
        Vec3d targetVec = new Vec3d(targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ());
        Vec3d playerVec = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        Vec3d dir = targetVec.subtract(playerVec).normalize();
        client.player.setVelocity(dir.x * 0.3, client.player.getVelocity().y, dir.z * 0.3);
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}

package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * RemoteView — view from another entity's perspective by setting the camera entity.
 * Cycles through nearby living entities or targets the nearest player.
 * Toggle off or press again to return to the player's own perspective.
 */
public class RemoteView extends Module {

    public static RemoteView INSTANCE;

    private Entity target = null;

    public RemoteView() {
        super("RemoteView", "View from another entity's perspective (spectator-like camera)", Category.RENDER);
        addMode("Target", "NearestPlayer", "NearestPlayer", "NearestEntity", "CyclePlayers");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        target = findTarget(client);
        if (target != null) {
            client.setCameraEntity(target);
        }
    }

    @Override
    public void onDisable() {
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.setCameraEntity(client.player);
        }
        target = null;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        // If target has gone away, reset
        if (target != null && (!target.isAlive() || !client.world.getEntities().iterator().hasNext())) {
            target = findTarget(client);
            if (target != null) {
                client.setCameraEntity(target);
            } else {
                client.setCameraEntity(client.player);
            }
        }
    }

    private Entity findTarget(MinecraftClient client) {
        String mode = getSetting("Target");
        var eyePos  = client.player.getEyePos();
        Entity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            if ("NearestPlayer".equals(mode) && !(e instanceof PlayerEntity)) continue;

            double dist = e.squaredDistanceTo(client.player);
            if (dist < bestDist) {
                bestDist = dist;
                best = e;
            }
        }
        return best;
    }
}

package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks positions where players were last seen when they disconnect.
 * Detects removal by comparing world entity UUIDs each tick vs a snapshot.
 */
public class LogoutSpots extends Module {

    public static LogoutSpots INSTANCE;

    private final Map<UUID, LogoutEntry> spots = new ConcurrentHashMap<>();
    private final Map<UUID, Vec3d>       seen  = new ConcurrentHashMap<>();

    public record LogoutEntry(Vec3d pos, String name) {}

    public LogoutSpots() {
        super("LogoutSpots", "Shows where players logged out during the session", Category.RENDER);
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            if (spots.isEmpty()) return;

            var cam      = context.worldState().cameraRenderState.pos;
            var matrices = context.matrices();
            if (matrices == null) return;
            var consumers = context.consumers();
            if (consumers == null) return;

            for (LogoutEntry entry : spots.values()) {
                Vec3d p = entry.pos();
                double rx = p.x - cam.x, ry = p.y - cam.y, rz = p.z - cam.z;
                Box box = new Box(rx - 0.4, ry, rz - 0.4, rx + 0.4, ry + 1.8, rz + 0.4);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, 1f, 0.8f, 0.1f, 1f);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            spots.clear();
            seen.clear();
        });
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        java.util.Set<UUID> current = new java.util.HashSet<>();

        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof PlayerEntity p) || e == client.player) continue;
            UUID uid = p.getUuid();
            current.add(uid);
            seen.put(uid, p.getEntityPos());
        }

        // Any UUID that was seen last tick but not this tick → logged out
        for (var entry : seen.entrySet()) {
            UUID uid = entry.getKey();
            if (!current.contains(uid) && !spots.containsKey(uid)) {
                // Resolve name from network handler if possible
                String name = uid.toString().substring(0, 8);
                if (client.getNetworkHandler() != null) {
                    var listEntry = client.getNetworkHandler().getPlayerListEntry(uid);
                    if (listEntry != null) name = listEntry.getProfile().name();
                }
                spots.put(uid, new LogoutEntry(entry.getValue(), name));
            }
        }

        seen.keySet().retainAll(current);
    }

    @Override
    public void onDisable() {
        spots.clear();
        seen.clear();
    }

    public Map<UUID, LogoutEntry> getSpots() { return spots; }
}

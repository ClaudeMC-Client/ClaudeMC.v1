package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Detects players who are vanished (present in tab list but no entity in world).
 * Shows a marker at the last known or estimated position.
 */
public class VanishDetect extends Module {

    public static VanishDetect INSTANCE;

    // UUID → last known world position
    private final Map<UUID, Vec3d> vanishedPositions = new HashMap<>();

    public VanishDetect() {
        super("VanishDetect", "Detects and marks vanished/invisible players", Category.MISC);
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            if (client.getNetworkHandler() == null) return;

            var cam = context.camera().getPos();
            var matrices = context.matrixStack();
            if (matrices == null) return;
            var consumers = context.consumers();
            if (consumers == null) return;

            // Build set of UUIDs with actual entities
            Set<UUID> presentUuids = new HashSet<>();
            for (Entity e : client.world.getEntities()) {
                if (e instanceof PlayerEntity) presentUuids.add(e.getUuid());
            }

            // Tab-list players with NO entity = potentially vanished
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                UUID uid = entry.getProfile().getId();
                if (uid.equals(client.player.getUuid())) continue;
                if (presentUuids.contains(uid)) {
                    vanishedPositions.remove(uid); // they appeared, clear marker
                    continue;
                }

                // Show a magenta box at last known position (or just above player's head)
                Vec3d pos = vanishedPositions.getOrDefault(uid,
                    client.player.getPos().add(0, 3, 0));
                Box box = new Box(
                    pos.x - cam.x - 0.4, pos.y - cam.y,       pos.z - cam.z - 0.4,
                    pos.x - cam.x + 0.4, pos.y - cam.y + 1.8, pos.z - cam.z + 0.4
                );
                RenderUtils.drawOutlinedBox(matrices, consumers, box, 1f, 0.2f, 1f, 1f);
            }
        });
    }

    /** Call from the network handler mixin when a player move packet is seen. */
    public void updatePosition(UUID uid, Vec3d pos) {
        vanishedPositions.put(uid, pos);
    }

    @Override public void onTick(MinecraftClient client) {}
}

package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects vanished players using two complementary techniques:
 *
 * 1) TAB-LIST CROSS-REFERENCE (works on all vanish plugins):
 *    Every tick, compare UUIDs in the server tab-list against UUIDs of actual
 *    world entities.  Any UUID present in the tab-list but absent in the world
 *    is labelled "vanished".  We show a marker at the last position we saw them.
 *
 * 2) PACKET POSITION LEAK (works on poorly patched vanish plugins):
 *    Most simple vanish plugins only suppress the SpawnEntity packet but still
 *    forward EntityPosition / MoveRelative packets for the hidden entity ID.
 *    VanishTrackingMixin intercepts those packets and feeds live coordinates
 *    here, so the outline moves in real-time even though no entity exists in
 *    the client world.
 */
public class VanishDetect extends Module {

    public static VanishDetect INSTANCE;

    // ── Tick-based tracking ───────────────────────────────────────────────
    /** entity ID → UUID for every OTHER player currently in the world */
    public final Map<Integer, UUID> entityIdToUuid     = new ConcurrentHashMap<>();
    /** UUID → last confirmed world position (when entity was visible) */
    public final Map<UUID, Vec3d>   lastKnownPos       = new ConcurrentHashMap<>();
    /** entity IDs destroyed via RemoveEntitiesS2CPacket that were players */
    public final Set<Integer>        removedPlayerIds   = ConcurrentHashMap.newKeySet();
    /** UUID → live-updated ghost position from leaked movement packets */
    public final Map<UUID, Vec3d>   ghostPos           = new ConcurrentHashMap<>();

    public VanishDetect() {
        super("VanishDetect",
              "Tracks vanished/invisible players via tab-list + packet-leak detection",
              Category.MISC);
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            if (client.getNetworkHandler() == null) return;

            var matrices  = context.matrices();
            var consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            Vec3d cam = context.worldState().cameraRenderState.pos;

            // Tab-list UUIDs
            Set<UUID> tabUuids = new HashSet<>();
            for (PlayerListEntry e : client.getNetworkHandler().getPlayerList()) {
                tabUuids.add(e.getProfile().id());
            }
            tabUuids.remove(client.player.getUuid());

            // World UUIDs (actually present as entities)
            Set<UUID> worldUuids = new HashSet<>();
            for (Entity e : client.world.getEntities()) {
                if (e instanceof PlayerEntity) worldUuids.add(e.getUuid());
            }

            for (UUID uid : tabUuids) {
                if (worldUuids.contains(uid)) continue; // visible – skip

                // Prefer live ghost position from packet leak; fall back to last seen
                Vec3d pos = ghostPos.getOrDefault(uid, lastKnownPos.get(uid));
                if (pos == null) continue;

                // Render a magenta outline at their position
                double rx = pos.x - cam.x, ry = pos.y - cam.y, rz = pos.z - cam.z;
                Box box = new Box(rx - 0.4, ry, rz - 0.4, rx + 0.4, ry + 1.8, rz + 0.4);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, 1f, 0.15f, 1f, 1f);
            }
        });
    }

    // ── Called from VanishTrackingMixin ──────────────────────────────────

    /** Called when a player entity is freshly spawned into the world. */
    public void onPlayerSpawned(int entityId, UUID uuid, double x, double y, double z) {
        entityIdToUuid.put(entityId, uuid);
        lastKnownPos.put(uuid, new Vec3d(x, y, z));
        removedPlayerIds.remove(entityId);
        ghostPos.remove(uuid); // they're visible again
    }

    /** Called when entity IDs are removed by RemoveEntitiesS2CPacket. */
    public void onEntitiesDestroyed(it.unimi.dsi.fastutil.ints.IntList ids) {
        var client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return;

        Set<UUID> tabUuids = new HashSet<>();
        for (PlayerListEntry e : client.getNetworkHandler().getPlayerList()) {
            tabUuids.add(e.getProfile().id());
        }

        for (int id : ids) {
            UUID uuid = entityIdToUuid.get(id);
            if (uuid != null && tabUuids.contains(uuid)) {
                // Still in tab list but entity removed → vanished
                removedPlayerIds.add(id);
                // Seed ghost position with last known position so we don't lose them immediately
                Vec3d last = lastKnownPos.get(uuid);
                if (last != null) ghostPos.putIfAbsent(uuid, last);
            }
        }
    }

    /**
     * Called when an absolute-position packet arrives for a removed entity.
     * This is the core of the packet-leak detection.
     */
    public void onGhostEntityPosition(int entityId, double x, double y, double z) {
        if (!removedPlayerIds.contains(entityId)) return;
        UUID uuid = entityIdToUuid.get(entityId);
        if (uuid != null) ghostPos.put(uuid, new Vec3d(x, y, z));
    }

    /**
     * Called when a relative-move packet arrives for a removed entity.
     * Delta values are in 1/4096ths of a block (Minecraft wire format).
     */
    public void onGhostEntityMoveRelative(int entityId, short dx, short dy, short dz) {
        if (!removedPlayerIds.contains(entityId)) return;
        UUID uuid = entityIdToUuid.get(entityId);
        if (uuid == null) return;
        Vec3d current = ghostPos.get(uuid);
        if (current == null) current = lastKnownPos.getOrDefault(uuid, Vec3d.ZERO);
        ghostPos.put(uuid, current.add(dx / 4096.0, dy / 4096.0, dz / 4096.0));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        // Continuously refresh entity-ID → UUID mapping while entities are visible
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof PlayerEntity) || e == client.player) continue;
            entityIdToUuid.put(e.getId(), e.getUuid());
            lastKnownPos.put(e.getUuid(), e.getEntityPos());
        }
    }
}

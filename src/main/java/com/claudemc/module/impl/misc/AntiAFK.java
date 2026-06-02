package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Advanced AntiAFK with staff-detection and human-like evasion:
 *
 * DETECTION TRIGGERS:
 *   1) Vanished player detected within alert range → human look-around + pause + notify
 *   2) Player teleporting close suddenly (staff TP) → same
 *   3) Incoming private message / AFK-check phrase → look-around + AutoReply handles the reply
 *
 * RESPONSE:
 *   - Pause the active macro/task for a configurable duration
 *   - Look around naturally (random yaw/pitch deltas over several ticks)
 *   - Optionally send a notification to the player (action bar or chat)
 *   - After the pause period, resume normal behaviour
 */
public class AntiAFK extends Module {

    public static AntiAFK INSTANCE;

    // Evasion state
    private boolean evading = false;
    private int     evadeTicks = 0;
    private int     lookPhase  = 0;
    private final Random rng   = new Random();

    // Track player positions to detect sudden TPs
    private final java.util.Map<UUID, Vec3d> prevPlayerPos = new java.util.concurrent.ConcurrentHashMap<>();
    private int vanishCheckCooldown = 0;

    public AntiAFK() {
        super("AntiAFK",
              "Detects staff (vanish/TP/DM) and responds with human-like behaviour to avoid detection",
              Category.UTILITY);
        addNumber("AlertRange",    20.0,  5.0, 64.0, 1.0, false);
        addNumber("PauseTicks",    120,   20, 600,  20,  true);
        addBool("NotifyOnDetect",  true);
        addBool("LookAround",      true);
        INSTANCE = this;

        // Chat listener — incoming message triggers evasion
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, ts) -> {
            if (!isEnabled()) return;
            String lower = message.getString().toLowerCase();
            if (containsAFKCheck(lower)) triggerEvasion("Incoming AFK-check message");
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!isEnabled() || overlay) return;
            String lower = message.getString().toLowerCase();
            if (containsAFKCheck(lower)) triggerEvasion("Incoming AFK-check game message");
        });
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        double alertRange = parseDouble(getSetting("AlertRange"), 20.0);

        // --- Check for vanished players nearby ---
        if (--vanishCheckCooldown <= 0) {
            vanishCheckCooldown = 20;
            if (VanishDetect.INSTANCE != null && VanishDetect.INSTANCE.isEnabled()) {
                for (Map.Entry<UUID, Vec3d> entry : VanishDetect.INSTANCE.ghostPos.entrySet()) {
                    Vec3d gp = entry.getValue();
                    if (client.player.getPos().distanceTo(gp) < alertRange) {
                        triggerEvasion("Vanished staff detected nearby!");
                        break;
                    }
                }
            }
        }

        // --- Detect sudden player TP (staff teleporting in) ---
        for (net.minecraft.entity.Entity e : client.world.getEntities()) {
            if (!(e instanceof net.minecraft.entity.player.PlayerEntity p) || e == client.player) continue;
            UUID uid = p.getUuid();
            Vec3d prev = prevPlayerPos.get(uid);
            Vec3d curr = p.getPos();
            if (prev != null) {
                double moved = prev.distanceTo(curr);
                double dist  = curr.distanceTo(client.player.getPos());
                // Moved >15 blocks in one tick AND now within alertRange → likely TP
                if (moved > 15 && dist < alertRange) {
                    triggerEvasion("Staff player teleported nearby!");
                }
            }
            prevPlayerPos.put(uid, curr);
        }
        java.util.Set<UUID> currentUuids = new java.util.HashSet<>();
        for (net.minecraft.entity.Entity e : client.world.getEntities()) {
            if (e instanceof net.minecraft.entity.player.PlayerEntity && e != client.player) {
                currentUuids.add(e.getUuid());
            }
        }
        prevPlayerPos.keySet().retainAll(currentUuids);

        // --- Execute evasion ---
        if (!evading) return;

        if (evadeTicks <= 0) {
            evading   = false;
            lookPhase = 0;
            return;
        }
        evadeTicks--;

        // Human-like look-around: random yaw/pitch nudges
        if (Boolean.parseBoolean(getSetting("LookAround"))) {
            lookPhase++;
            if (lookPhase % 3 == 0) {
                float dYaw   = (rng.nextFloat() - 0.5f) * 18f;
                float dPitch = (rng.nextFloat() - 0.5f) * 12f;
                float newYaw   = client.player.getYaw()   + dYaw;
                float newPitch = Math.max(-30f, Math.min(40f, client.player.getPitch() + dPitch));
                client.player.setYaw(newYaw);
                client.player.setPitch(newPitch);
            }
        }
    }

    private void triggerEvasion(String reason) {
        if (evading) return; // already evading

        var client = MinecraftClient.getInstance();
        if (client == null) return;

        evading    = true;
        evadeTicks = parseInt(getSetting("PauseTicks"), 120);
        lookPhase  = 0;

        // Notify player
        if (Boolean.parseBoolean(getSetting("NotifyOnDetect")) && client.player != null) {
            client.player.sendMessage(
                Text.literal("§c[AntiAFK] §e⚠ " + reason + " — pausing for "
                    + evadeTicks + " ticks"), false);
        }
    }

    private boolean containsAFKCheck(String lower) {
        return lower.contains("afk") || lower.contains("you there")
            || lower.contains("macro") || lower.contains("autoclicker")
            || lower.contains("are you") || lower.contains("u there")
            || lower.contains("hello?");
    }

    @Override
    public void onDisable() {
        evading    = false;
        evadeTicks = 0;
        lookPhase  = 0;
        prevPlayerPos.clear();
    }

    public boolean isEvading() { return evading; }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}

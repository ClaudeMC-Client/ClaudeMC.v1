package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Meteor Client-style Flight.
 * Vanilla mode: sets allowFlying/flying and flySpeed directly (no reflection needed —
 *   PlayerAbilities.flySpeed is a public field in vanilla/Fabric 1.21.x).
 * Packet mode: additionally sends onGround=false each tick to prevent server kicks.
 * Anti-kick: periodically drops 0.04 blocks so the server doesn't flag stationary hover.
 */
public class Flight extends Module {

    private int     antiKickTimer = 0;
    private boolean antiKickPhase = false;

    public Flight() {
        super("Flight", "Creative-style flight in any game mode", Category.MOVEMENT);
        addNumber("Speed",     0.1, 0.01, 5.0, 0.01, false);
        addMode("Mode",        "Vanilla", "Vanilla", "Packet");
        addBool("AntiKick",    true);
        addNumber("KickTicks", 40, 5, 200, 5, true);
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player == null) return;
        enableFlying(c);
        antiKickTimer = 0;
        antiKickPhase = false;
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player == null) return;
        var ab = c.player.getAbilities();
        if (!ab.creativeMode) {
            ab.allowFlying = false;
            ab.flying      = false;
        }
        setFlySpeed(ab, 0.05f); // restore vanilla default
        c.player.sendAbilitiesUpdate();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        var ab = client.player.getAbilities();

        if (!ab.allowFlying || !ab.flying) enableFlying(client);

        // Set flySpeed via reflection (field is private in MC 1.21.x yarn mappings)
        setFlySpeed(ab, (float) parseDouble(getSetting("Speed"), 0.1));

        // Anti-kick: brief downward nudge every N ticks
        if (Boolean.parseBoolean(getSetting("AntiKick"))) {
            int kickTicks = parseInt(getSetting("KickTicks"), 40);
            if (++antiKickTimer >= kickTicks) {
                antiKickTimer = 0;
                antiKickPhase = !antiKickPhase;
                var vel = client.player.getVelocity();
                client.player.setVelocity(vel.x, antiKickPhase ? -0.04 : 0.0, vel.z);
            }
        }

        // Packet mode: spoof onGround=false to prevent "moved wrongly" kicks
        if ("Packet".equals(getSetting("Mode"))) {
            var nh = client.getNetworkHandler();
            if (nh != null) nh.sendPacket(
                new PlayerMoveC2SPacket.OnGroundOnly(false, client.player.horizontalCollision));
        }
    }

    private void enableFlying(MinecraftClient c) {
        var ab = c.player.getAbilities();
        ab.allowFlying = true;
        ab.flying      = true;
        c.player.sendAbilitiesUpdate();
    }

    private static final String[] FLY_SPEED_FIELDS = {"flySpeed", "field_12643"};

    private static void setFlySpeed(net.minecraft.entity.player.PlayerAbilities ab, float value) {
        for (String name : FLY_SPEED_FIELDS) {
            try {
                var f = ab.getClass().getDeclaredField(name);
                f.setAccessible(true);
                f.setFloat(ab, value);
                return;
            } catch (Exception ignored) {}
        }
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}

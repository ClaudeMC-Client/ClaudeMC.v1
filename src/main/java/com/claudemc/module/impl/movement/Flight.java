package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;

/**
 * Creative-style flight in any game mode.
 * Anti-kick technique adapted from Wurst7 FlightHack: oscillate vertical velocity
 * on a configurable interval so the server doesn't detect the player standing still
 * in mid-air (which triggers a "moved too quickly" / flying kick on many servers).
 */
public class Flight extends Module {

    private int antiKickTimer = 0;
    private boolean antiKickPhase = false;

    public Flight() {
        super("Flight", "Creative-style flight in any game mode", Category.MOVEMENT);
        addNumber("Speed",     0.10, 0.01, 5.0, 0.01, false);
        addMode("Mode",        "Vanilla", "Vanilla", "Packet");
        addBool("AntiKick",    true);
        addNumber("KickTicks", 40, 5, 200, 5, true);
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player == null) return;
        var ab = c.player.getAbilities();
        ab.allowFlying = true;
        ab.flying = true;
        c.player.sendAbilitiesUpdate();
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
            ab.flying = false;
        }
        c.player.sendAbilitiesUpdate();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        var ab = client.player.getAbilities();

        if (!ab.allowFlying) {
            ab.allowFlying = true;
            ab.flying = true;
            client.player.sendAbilitiesUpdate();
        }

        // flySpeed has private access in 1.21.x — use reflection
        try {
            var f = ab.getClass().getDeclaredField("flySpeed");
            f.setAccessible(true);
            f.setFloat(ab, (float) Double.parseDouble(getSetting("Speed")));
        } catch (Exception ignored) {}

        // Anti-kick: oscillate velocity slightly so the server doesn't flag stationary flight
        if (Boolean.parseBoolean(getSetting("AntiKick"))) {
            int kickTicks = parseInt(getSetting("KickTicks"), 40);
            antiKickTimer++;
            if (antiKickTimer >= kickTicks) {
                antiKickTimer = 0;
                antiKickPhase = !antiKickPhase;
                double nudge = antiKickPhase ? -0.04 : 0.04;
                var vel = client.player.getVelocity();
                client.player.setVelocity(vel.x, nudge, vel.z);
            }
        }
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}

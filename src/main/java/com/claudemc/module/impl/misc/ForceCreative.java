package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;

/**
 * ForceCreative — attempts to set your game mode to Creative on the server.
 *
 * TECHNIQUES:
 *
 *  1. Chat-command: "/gamemode creative" — works if you have OP or a
 *     permissions plugin that grants it.
 *
 *  2. Abilities-packet spoof: sets creativeMode / allowFlying / invulnerable
 *     locally and calls sendAbilitiesUpdate() each tick.  Some poorly
 *     configured servers (Spigot without anti-cheat, Minehut old versions,
 *     CraftBukkit) accept these ability flags without re-validating game mode.
 *     This grants: fly, no damage, and creative-speed movement.
 *     It does NOT open the creative inventory on a patched server.
 *
 *  3. Gamemode spoof (client-side only): forces the local game-mode field so
 *     that right-click interactions, block placement speed, etc. behave as if
 *     in creative even if the server hasn't confirmed it.
 *
 * ⚠  Fully patched Paper / Purpur will reject ability spoofing immediately.
 *    The command technique requires a permission grant from the server.
 */
public class ForceCreative extends Module {

    private static final int REATTEMPT_TICKS = 20; // re-send abilities every second
    private int ticker = 0;

    public ForceCreative() {
        super("ForceCreative",
              "Attempts to switch to Creative mode on the server",
              Category.MISC);
        addSetting("Technique", "All"); // All | Command | Abilities | Spoof
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String tech = getSetting("Technique");
        if ("All".equals(tech) || "Command".equals(tech)) tryCommand(client);
        if ("All".equals(tech) || "Abilities".equals(tech)) spoofAbilities(client, true);
        if ("All".equals(tech) || "Spoof".equals(tech)) spoofClientMode(client);

        ticker = 0;
    }

    @Override
    public void onDisable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        // Restore survival abilities
        PlayerAbilities ab = client.player.getAbilities();
        if (!ab.creativeMode) {
            ab.allowFlying    = false;
            ab.flying         = false;
            ab.invulnerable   = false;
            ab.flySpeed       = 0.05f;
            ab.walkSpeed      = 0.1f;
        }
        client.player.sendAbilitiesUpdate();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        String tech = getSetting("Technique");

        // Re-send abilities packet periodically to resist server resets
        if ("All".equals(tech) || "Abilities".equals(tech)) {
            if (++ticker >= REATTEMPT_TICKS) {
                ticker = 0;
                spoofAbilities(client, false);
            }
        }
    }

    // ── Techniques ────────────────────────────────────────────────────────

    private void tryCommand(MinecraftClient client) {
        try {
            client.getNetworkHandler().sendChatCommand("gamemode creative");
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ForceCreative][Command] {}", e.getMessage());
        }
    }

    /**
     * Sets creative-equivalent ability flags and sends the abilities update
     * packet.  The server will accept this if it doesn't validate game mode.
     */
    private void spoofAbilities(MinecraftClient client, boolean verbose) {
        try {
            PlayerAbilities ab = client.player.getAbilities();
            ab.creativeMode  = true;
            ab.allowFlying   = true;
            ab.flying        = true;
            ab.invulnerable  = true;
            ab.flySpeed      = 0.05f;
            ab.walkSpeed     = 0.1f;
            client.player.sendAbilitiesUpdate();
            if (verbose) ClaudeMCMod.LOGGER.info("[ForceCreative] Abilities packet sent.");
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ForceCreative][Abilities] {}", e.getMessage());
        }
    }

    /**
     * Overrides the client-side interactionManager gameMode field so that
     * local behaviour (block break speed, item use) matches creative,
     * regardless of what the server believes.
     */
    private void spoofClientMode(MinecraftClient client) {
        try {
            var im = client.interactionManager;
            if (im == null) return;
            // Access the currentGameMode field via reflection
            var f = im.getClass().getDeclaredField("currentGameMode");
            f.setAccessible(true);
            f.set(im, net.minecraft.world.GameMode.CREATIVE);
            ClaudeMCMod.LOGGER.info("[ForceCreative] Client game-mode spoofed.");
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ForceCreative][Spoof] {}", e.getMessage());
        }
    }
}

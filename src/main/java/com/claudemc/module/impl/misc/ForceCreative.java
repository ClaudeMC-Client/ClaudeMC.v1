package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;

import java.lang.reflect.Field;

public class ForceCreative extends Module {

    private static final int REATTEMPT_TICKS = 20;
    private int ticker = 0;

    public ForceCreative() {
        super("ForceCreative", "Attempts to switch to Creative mode on the server", Category.EXPLOIT);
        addSetting("Technique", "All");
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        String tech = getSetting("Technique");
        if ("All".equals(tech) || "Command".equals(tech))   tryCommand(client);
        if ("All".equals(tech) || "Abilities".equals(tech)) spoofAbilities(client, true);
        if ("All".equals(tech) || "Spoof".equals(tech))     spoofClientMode(client);
        ticker = 0;
    }

    @Override
    public void onDisable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        PlayerAbilities ab = client.player.getAbilities();
        if (!ab.creativeMode) {
            ab.allowFlying  = false;
            ab.flying       = false;
            ab.invulnerable = false;
            setFloat(ab, "flySpeed",  0.05f);
            setFloat(ab, "walkSpeed", 0.1f);
        }
        client.player.sendAbilitiesUpdate();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        String tech = getSetting("Technique");
        if ("All".equals(tech) || "Abilities".equals(tech)) {
            if (++ticker >= REATTEMPT_TICKS) { ticker = 0; spoofAbilities(client, false); }
        }
    }

    private void tryCommand(MinecraftClient client) {
        try { client.getNetworkHandler().sendChatCommand("gamemode creative"); }
        catch (Exception e) { ClaudeMCMod.LOGGER.warn("[ForceCreative][Command] {}", e.getMessage()); }
    }

    private void spoofAbilities(MinecraftClient client, boolean verbose) {
        try {
            PlayerAbilities ab = client.player.getAbilities();
            ab.creativeMode = true;
            ab.allowFlying  = true;
            ab.flying       = true;
            ab.invulnerable = true;
            setFloat(ab, "flySpeed",  0.05f);
            setFloat(ab, "walkSpeed", 0.1f);
            client.player.sendAbilitiesUpdate();
            if (verbose) ClaudeMCMod.LOGGER.info("[ForceCreative] Abilities packet sent.");
        } catch (Exception e) { ClaudeMCMod.LOGGER.warn("[ForceCreative][Abilities] {}", e.getMessage()); }
    }

    private void spoofClientMode(MinecraftClient client) {
        try {
            var im = client.interactionManager;
            if (im == null) return;
            var f = im.getClass().getDeclaredField("currentGameMode");
            f.setAccessible(true);
            f.set(im, net.minecraft.world.GameMode.CREATIVE);
        } catch (Exception e) { ClaudeMCMod.LOGGER.warn("[ForceCreative][Spoof] {}", e.getMessage()); }
    }

    private void setFloat(Object obj, String fieldName, float value) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.setFloat(obj, value);
        } catch (Exception ignored) {}
    }
}

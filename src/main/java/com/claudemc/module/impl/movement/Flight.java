package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;

import java.lang.reflect.Field;

public class Flight extends Module {

    public Flight() {
        super("Flight", "Creative-style flight in any game mode", Category.MOVEMENT);
        addSetting("Speed",  "0.10");
        addSetting("Mode",   "Vanilla");
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player == null) return;
        c.player.getAbilities().allowFlying = true;
        c.player.sendAbilitiesUpdate();
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
        float spd = parseFloat(getSetting("Speed"), 0.10f);
        var ab = client.player.getAbilities();
        if (!ab.allowFlying) { ab.allowFlying = true; client.player.sendAbilitiesUpdate(); }
        setFlySpeed(ab, spd);
    }

    private void setFlySpeed(PlayerAbilities ab, float speed) {
        try {
            Field f = ab.getClass().getDeclaredField("flySpeed");
            f.setAccessible(true);
            f.setFloat(ab, speed);
        } catch (Exception ignored) {
            // fallback: field may be public in this MC version
            try { ab.flySpeed = speed; } catch (Exception ignored2) {}
        }
    }

    private float parseFloat(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }
}

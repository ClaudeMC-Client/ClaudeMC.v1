package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

public class Step extends Module {

    private float oldStepHeight = 0.6f;

    public Step() {
        super("Step", "Step up full blocks instantly", Category.MOVEMENT);
        addSetting("Height", "1.0");
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) {
            oldStepHeight = c.player.stepHeight;
            c.player.stepHeight = parseFloat(getSetting("Height"), 1.0f);
        }
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) c.player.stepHeight = oldStepHeight;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        float h = parseFloat(getSetting("Height"), 1.0f);
        if (client.player.stepHeight != h) client.player.stepHeight = h;
    }

    private float parseFloat(String s, float d) {
        try { return Float.parseFloat(s); } catch (Exception e) { return d; }
    }
}

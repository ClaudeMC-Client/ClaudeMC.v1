package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

public class Fullbright extends Module {

    public static Fullbright INSTANCE;
    private double savedGamma = 1.0;

    public Fullbright() {
        super("Fullbright", "Makes everything fully lit regardless of light level", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.options != null) {
            savedGamma = c.options.getGamma().getValue();
        }
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.options != null) c.options.getGamma().setValue(savedGamma);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.options != null) client.options.getGamma().setValue(100.0);
    }
}

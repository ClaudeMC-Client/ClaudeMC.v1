package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Shows enhanced nametags for players (health, ping, distance).
 * Rendering is done via the Nametag mixin in ClientPlayerEntityMixin.
 */
public class Nametags extends Module {

    public static Nametags INSTANCE;

    public Nametags() {
        super("Nametags", "Show player health, ping and distance above their heads", Category.RENDER);
        addBool("Health", true);
        addBool("Ping", true);
        addBool("Dist", true);
        INSTANCE = this;
    }

    @Override public void onTick(MinecraftClient client) {}
}

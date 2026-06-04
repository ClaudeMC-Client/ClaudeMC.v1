package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * NoClip – phase through blocks by setting player.noClip = true each tick.
 */
public class NoClip extends Module {

    public static NoClip INSTANCE;

    public NoClip() {
        super("NoClip", "Phase through blocks", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) c.player.noClip = true;
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) c.player.noClip = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        client.player.noClip = true;
        // Keep gravity zeroed so player doesn't fall through the floor
        var vel = client.player.getVelocity();
        client.player.setVelocity(vel.x, vel.y, vel.z);
    }
}

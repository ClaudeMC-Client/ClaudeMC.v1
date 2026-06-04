package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * CameraNoClip – allows the camera to pass through walls by setting noClip on the player.
 * This has a spectator-like effect for the client camera in third-person views.
 *
 * Note: Full camera clip bypass (separate from player noClip) would require a mixin
 * on Camera or GameRenderer to skip block-clipping. This implementation sets player noClip
 * so the camera attached to the player no longer clips against blocks.
 */
public class CameraNoClip extends Module {

    public static CameraNoClip INSTANCE;

    public CameraNoClip() {
        super("CameraNoClip", "Camera passes through walls like spectator mode", Category.MOVEMENT);
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
    }
}

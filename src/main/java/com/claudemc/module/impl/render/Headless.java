package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Headless: Makes the player appear headless by forcing the head pitch to an extreme angle
 * that clips it out of view. This is a cosmetic client-side effect.
 *
 * For a true headless render effect, a mixin into PlayerEntityRenderer would be needed.
 * This implementation achieves a similar visual result by manipulating pitch.
 */
public class Headless extends Module {

    public static Headless INSTANCE;

    private float prevPitch = 0f;

    public Headless() {
        super("Headless", "Makes your player appear headless", Category.MISC);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            prevPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.setPitch(prevPitch);
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        // Push pitch to extreme value to hide head model
        mc.player.setPitch(90f);
    }
}

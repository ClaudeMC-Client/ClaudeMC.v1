package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Removes the pumpkin overlay shown when wearing a carved pumpkin.
 * Cancelled via NoPumpkinMixin (overrides the overlay rendering method).
 */
public class NoPumpkin extends Module {

    public static NoPumpkin INSTANCE;

    public NoPumpkin() {
        super("NoPumpkin", "Removes pumpkin head overlay", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {}
}

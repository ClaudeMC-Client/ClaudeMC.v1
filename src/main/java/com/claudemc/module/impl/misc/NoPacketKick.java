package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Prevents being kicked for invalid/flood packets.
 * Actual protection is applied in ClientPlayNetworkHandlerMixin.
 */
public class NoPacketKick extends Module {

    public static NoPacketKick INSTANCE;

    public NoPacketKick() {
        super("NoPacketKick", "Prevents being kicked for certain invalid packets", Category.MISC);
        INSTANCE = this;
    }

    @Override public void onTick(MinecraftClient client) {}
}

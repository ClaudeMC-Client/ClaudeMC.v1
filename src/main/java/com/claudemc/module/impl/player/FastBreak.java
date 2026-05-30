package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

public class FastBreak extends Module {

    public static FastBreak INSTANCE;

    public FastBreak() {
        super("FastBreak", "Removes block-breaking delay (1-tick break for instant-break blocks)", Category.PLAYER);
        INSTANCE = this;
    }

    @Override public void onTick(MinecraftClient client) {}
}

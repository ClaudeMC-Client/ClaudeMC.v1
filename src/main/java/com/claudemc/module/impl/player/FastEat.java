package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Eat food faster by reducing the use-item tick duration.
 * The speed multiplier is applied via FastEatMixin.
 */
public class FastEat extends Module {

    public static FastEat INSTANCE;

    public FastEat() {
        super("FastEat", "Eat food faster", Category.PLAYER);
        addNumber("Speed", 4, 1, 10, 1, true);
        INSTANCE = this;
    }

    /** Returns how many ticks to subtract from use duration each tick. */
    public int getSpeed() {
        try { return (int) Double.parseDouble(getSetting("Speed").trim()); }
        catch (Exception e) { return 4; }
    }

    @Override
    public void onTick(MinecraftClient client) {}
}

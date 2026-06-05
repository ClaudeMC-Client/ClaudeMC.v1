package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;

/**
 * MileyCyrus: Swings the player's arms wildly every tick.
 */
public class MileyCyrus extends Module {

    public static MileyCyrus INSTANCE;

    private int tickCounter = 0;

    public MileyCyrus() {
        super("MileyCyrus", "Swings arms wildly like Miley Cyrus", Category.MISC);
        INSTANCE = this;
        addNumber("SwingRate", 1, 1, 10, 1, true);
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        tickCounter = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;

        int rate = parseInt(getSetting("SwingRate"), 1);
        if (++tickCounter < rate) return;
        tickCounter = 0;

        // Swing main hand and offhand alternately
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.swingHand(Hand.OFF_HAND);
    }

    private int parseInt(String s, int d) {
        try { return (int) Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}

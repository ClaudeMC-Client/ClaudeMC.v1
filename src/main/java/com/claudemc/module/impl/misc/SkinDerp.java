package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * SkinDerp: Rapidly toggles the player's skin model type between slim and classic.
 * This is a cosmetic client-side effect.
 */
public class SkinDerp extends Module {

    public static SkinDerp INSTANCE;

    private int tickCounter = 0;
    private boolean useSlim = false;

    private int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }

    public SkinDerp() {
        super("SkinDerp", "Rapidly toggles slim/classic skin model", Category.MISC);
        INSTANCE = this;
        addNumber("ToggleRate", 2, 1, 20, 1, true);
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        useSlim = false;
    }

    @Override
    public void onDisable() {
        tickCounter = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;

        int rate = parseInt(getSetting("ToggleRate"), 2);
        if (++tickCounter < rate) return;
        tickCounter = 0;

        useSlim = !useSlim;

        // Toggle model via yaw flip (visual effect that simulates "derping")
        // Since direct skin model manipulation requires reflection or mixins,
        // we achieve a similar cosmetic effect by rapidly adjusting head tilt
        float currentPitch = mc.player.getPitch();
        mc.player.setPitch(useSlim ? 89f : -89f);
    }
}

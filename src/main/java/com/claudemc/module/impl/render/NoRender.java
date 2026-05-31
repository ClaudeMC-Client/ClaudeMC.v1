package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Suppresses specific HUD/world render elements.
 * Individual render-call cancellations are applied in InGameHudMixin.
 */
public class NoRender extends Module {

    public static NoRender INSTANCE;

    public NoRender() {
        super("NoRender", "Hides selected HUD and world elements", Category.RENDER);
        addBool("Totem",        true);
        addBool("Fire",         true);
        addBool("BossBar",      false);
        addBool("Scoreboard",   false);
        addBool("Particles",    false);
        addBool("PotionHUD",    false);
        INSTANCE = this;
    }

    public boolean noTotem()      { return isEnabled() && boolSetting("Totem"); }
    public boolean noFire()       { return isEnabled() && boolSetting("Fire"); }
    public boolean noBossBar()    { return isEnabled() && boolSetting("BossBar"); }
    public boolean noScoreboard() { return isEnabled() && boolSetting("Scoreboard"); }
    public boolean noParticles()  { return isEnabled() && boolSetting("Particles"); }
    public boolean noPotionHUD()  { return isEnabled() && boolSetting("PotionHUD"); }

    private boolean boolSetting(String name) {
        return Boolean.parseBoolean(getSetting(name));
    }

    @Override public void onTick(MinecraftClient client) {}
}

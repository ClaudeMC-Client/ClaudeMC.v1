package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Forces all entities to render even if invisible (invis potion or vanish plugin).
 * Works via EntityMixin which overrides Entity.isInvisible() when this module is on.
 * Vanished players (removed entity but present in tab list) are tracked separately.
 */
public class AntiInvis extends Module {

    public static AntiInvis INSTANCE;

    public AntiInvis() {
        super("AntiInvis", "See invisible and vanished players", Category.RENDER);
        addSetting("Opacity", "0.4"); // render opacity for invis entities 0-1
        INSTANCE = this;
    }

    @Override public void onTick(MinecraftClient client) {}
}

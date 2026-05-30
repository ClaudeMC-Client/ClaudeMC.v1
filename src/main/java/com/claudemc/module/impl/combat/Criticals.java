package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class Criticals extends Module {

    public Criticals() {
        super("Criticals", "Makes every melee attack a critical hit", Category.COMBAT);
        addSetting("Mode", "Jump"); // Jump | Packet
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        // Jump mode: make player have a tiny vertical velocity so criticals register
        if ("Jump".equals(getSetting("Mode")) && client.player.isOnGround()) {
            client.player.setVelocity(
                client.player.getVelocity().x,
                0.1,
                client.player.getVelocity().z
            );
        }
    }
}

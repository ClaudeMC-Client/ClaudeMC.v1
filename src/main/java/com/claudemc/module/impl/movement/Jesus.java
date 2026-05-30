package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class Jesus extends Module {

    public Jesus() {
        super("Jesus", "Walk on water and lava", Category.MOVEMENT);
        addSetting("Lava", "false");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        boolean doLava = Boolean.parseBoolean(getSetting("Lava"));

        boolean inFluid = client.player.isTouchingWater()
                        || (doLava && client.player.isInLava());

        if (inFluid && !client.player.isSneaking()) {
            var vel = client.player.getVelocity();
            if (vel.y < 0) {
                client.player.setVelocity(vel.x, 0.04, vel.z);
            }
        }
    }
}

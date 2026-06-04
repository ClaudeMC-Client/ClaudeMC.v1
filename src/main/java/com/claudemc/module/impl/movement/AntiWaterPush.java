package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * AntiWaterPush – water currents don't push the player.
 *
 * Water push is applied in Entity.updateVelocity via fluid push vectors.
 * Full prevention requires a mixin on FluidState or Entity.
 * This implementation cancels water-induced velocity each tick client-side.
 * INSTANCE is checked by AntiWaterPushMixin.
 */
public class AntiWaterPush extends Module {

    public static AntiWaterPush INSTANCE;

    private double lastX, lastZ;
    private boolean wasInWater;

    public AntiWaterPush() {
        super("AntiWaterPush", "Water currents won't push the player", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        wasInWater = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (!client.player.isTouchingWater()) { wasInWater = false; return; }

        var opts = client.options;
        boolean moving = opts.forwardKey.isPressed() || opts.backKey.isPressed()
                      || opts.leftKey.isPressed()    || opts.rightKey.isPressed();

        if (!moving) {
            // Cancel horizontal velocity from water push
            var vel = client.player.getVelocity();
            if (!wasInWater) {
                lastX = vel.x;
                lastZ = vel.z;
                wasInWater = true;
            }
            // Keep only player-intended velocity (zero water drift)
            client.player.setVelocity(0, vel.y, 0);
        } else {
            wasInWater = true;
        }
    }
}

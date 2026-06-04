package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * NoWeb – prevents cobwebs from slowing the player.
 *
 * Full prevention requires a mixin on Entity.slowMovement() or LivingEntity
 * to skip the velocity reduction when inside a cobweb block.
 * This module provides the INSTANCE flag for such a mixin and also
 * compensates post-tick by boosting velocity back when inside a cobweb.
 */
public class NoWeb extends Module {

    public static NoWeb INSTANCE;

    public NoWeb() {
        super("NoWeb", "Prevents cobwebs from slowing the player", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        // Check block below/at player position for cobweb
        var pos = client.player.getBlockPos();
        var state = client.world != null ? client.world.getBlockState(pos) : null;
        if (state == null) return;

        // net.minecraft.block.Blocks.COBWEB
        if (state.isOf(net.minecraft.block.Blocks.COBWEB)) {
            var vel = client.player.getVelocity();
            var opts = client.options;

            float yawRad = (float) Math.toRadians(client.player.getYaw());
            double mx = 0, mz = 0;
            if (opts.forwardKey.isPressed()) { mx -= Math.sin(yawRad); mz += Math.cos(yawRad); }
            if (opts.backKey.isPressed())    { mx += Math.sin(yawRad); mz -= Math.cos(yawRad); }
            if (opts.leftKey.isPressed())    { mx -= Math.cos(yawRad); mz -= Math.sin(yawRad); }
            if (opts.rightKey.isPressed())   { mx += Math.cos(yawRad); mz += Math.sin(yawRad); }

            double len = Math.sqrt(mx * mx + mz * mz);
            if (len > 0) {
                mx /= len; mz /= len;
                client.player.setVelocity(mx * 0.215, vel.y, mz * 0.215);
            }
        }
    }
}

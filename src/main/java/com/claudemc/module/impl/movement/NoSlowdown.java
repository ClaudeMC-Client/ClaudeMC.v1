package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * NoSlowdown – prevents slowdown from holding items (food, bows, shields),
 * being in cobwebs, powder snow, etc.
 *
 * The item-use slowdown is applied in LivingEntity.travel() based on
 * isUsingItem() and the item's use action. Full prevention requires a mixin
 * on LivingEntity to cancel the velocity reduction.
 *
 * This implementation overrides velocity post-tick to maintain normal speed
 * when the player would otherwise be slowed, and provides the INSTANCE flag
 * that a mixin (LivingEntityMixin) can check.
 */
public class NoSlowdown extends Module {

    public static NoSlowdown INSTANCE;

    public NoSlowdown() {
        super("NoSlowdown", "Prevents slowdown from items, cobwebs, and powder snow", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        // If player is using an item, boost back to normal walk speed
        if (client.player.isUsingItem()) {
            var vel = client.player.getVelocity();
            var opts = client.options;

            boolean fwd   = opts.forwardKey.isPressed();
            boolean back  = opts.backKey.isPressed();
            boolean left  = opts.leftKey.isPressed();
            boolean right = opts.rightKey.isPressed();

            if (fwd || back || left || right) {
                float yawRad = (float) Math.toRadians(client.player.getYaw());
                double mx = 0, mz = 0;
                if (fwd)  { mx -= Math.sin(yawRad); mz += Math.cos(yawRad); }
                if (back) { mx += Math.sin(yawRad); mz -= Math.cos(yawRad); }
                if (left) { mx -= Math.cos(yawRad); mz -= Math.sin(yawRad); }
                if (right){ mx += Math.cos(yawRad); mz += Math.sin(yawRad); }

                double len = Math.sqrt(mx * mx + mz * mz);
                if (len > 0) { mx /= len; mz /= len; }

                // Normal walk speed ~0.215
                double speed = 0.215;
                if (Math.abs(vel.x) < speed || Math.abs(vel.z) < speed) {
                    client.player.setVelocity(mx * speed, vel.y, mz * speed);
                }
            }
        }
    }
}

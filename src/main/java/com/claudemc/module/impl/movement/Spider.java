package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Spider – climb any block face like a spider.
 * Applies upward velocity when moving toward a wall.
 */
public class Spider extends Module {

    public static Spider INSTANCE;

    public Spider() {
        super("Spider", "Climb walls like a spider", Category.MOVEMENT);
        addSetting("Speed", "0.20");
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.player.isOnGround()) return;

        double speed = parseDouble(getSetting("Speed"), 0.20);

        // Check if there's a block adjacent to the player (touching a wall)
        var bPos = client.player.getBlockPos();

        boolean touchingWall = isTouchingWall(client, bPos);

        if (touchingWall) {
            var vel = client.player.getVelocity();
            var opts = client.options;

            boolean moving = opts.forwardKey.isPressed() || opts.backKey.isPressed()
                          || opts.leftKey.isPressed()    || opts.rightKey.isPressed();

            if (moving) {
                // Climb up
                client.player.setVelocity(vel.x, speed, vel.z);
            } else {
                // Cling to wall (no gravity)
                client.player.setVelocity(vel.x, 0, vel.z);
            }
        }
    }

    private boolean isTouchingWall(MinecraftClient client, BlockPos bPos) {
        return !client.world.isAir(bPos.east())  ||
               !client.world.isAir(bPos.west())  ||
               !client.world.isAir(bPos.north()) ||
               !client.world.isAir(bPos.south());
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}

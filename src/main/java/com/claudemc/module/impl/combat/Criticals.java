package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class Criticals extends Module {

    public Criticals() {
        super("Criticals", "Makes every melee attack a critical hit", Category.COMBAT);
        addMode("Mode", "Packet", "Packet", "Jump");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        String mode = getSetting("Mode");

        if ("Jump".equals(mode) && client.player.isOnGround()) {
            // Apply a tiny upward velocity so MC registers the next hit as a crit
            var vel = client.player.getVelocity();
            client.player.setVelocity(vel.x, 0.1, vel.z);

        } else if ("Packet".equals(mode) && client.player.isOnGround()) {
            // Send two spoofed position packets: briefly airborne then landing, both
            // with onGround=false. Server marks the player as falling → next attack is critical.
            var nh = client.getNetworkHandler();
            if (nh == null) return;
            double x = client.player.getX();
            double y = client.player.getY();
            double z = client.player.getZ();
            boolean hc = client.player.horizontalCollision;
            nh.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, hc));
            nh.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, hc));
        }
    }
}

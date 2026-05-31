package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

public class ElytraFlight extends Module {

    public ElytraFlight() {
        super("ElytraFlight", "Boost and control elytra flight", Category.MOVEMENT);
        addMode("Mode",   "Boost",  "Boost", "Packet", "Pitch");
        addNumber("Speed", 1.8,  0.1, 6.0, 0.1, false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (!isWearingElytra(client)) return;

        String mode  = getSetting("Mode");
        double speed = parseDouble(getSetting("Speed"), 1.8);

        if (!client.player.isFallFlying()) {
            if (client.player.getVelocity().y < -0.1) {
                // Start gliding when falling
                client.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(client.player,
                        ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            return;
        }

        switch (mode) {
            case "Boost" -> {
                Vec3d look = client.player.getRotationVec(1.0f);
                client.player.setVelocity(look.multiply(speed));
            }
            case "Packet" -> {
                // Restart gliding each tick to prevent the server killing fall-fly
                client.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(client.player,
                        ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                Vec3d look = client.player.getRotationVec(1.0f);
                client.player.setVelocity(look.multiply(speed));
            }
            case "Pitch" -> {
                // Speed based on pitch angle
                float pitch = client.player.getPitch();
                double hSpeed = Math.cos(Math.toRadians(pitch)) * speed;
                double vSpeed = -Math.sin(Math.toRadians(pitch)) * speed * 0.5;
                Vec3d look = client.player.getRotationVec(1.0f);
                client.player.setVelocity(
                    look.x * hSpeed,
                    vSpeed,
                    look.z * hSpeed);
            }
        }
    }

    private boolean isWearingElytra(MinecraftClient client) {
        var chest = client.player.getInventory().getArmorStack(2);
        return chest.getItem() == Items.ELYTRA;
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}

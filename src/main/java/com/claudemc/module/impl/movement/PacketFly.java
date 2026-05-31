package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Bypasses basic server-side anti-fly by alternating between "on-ground" and
 * flying states — the classic "packet fly" technique. Works against servers
 * that use NCP/AAC vanilla-move checks but not against advanced detections.
 */
public class PacketFly extends Module {

    private int tickCounter = 0;

    public PacketFly() {
        super("PacketFly", "Bypasses basic anti-fly via alternating ground-state packets", Category.MOVEMENT);
        addNumber("Speed",  0.8, 0.1, 5.0, 0.1, false);
        addNumber("Height", 0.42, 0.1, 1.0, 0.01, false);
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.player.getAbilities().allowFlying = true;
        client.player.getAbilities().flying      = true;
        client.player.sendAbilitiesUpdate();
    }

    @Override
    public void onDisable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        var ab = client.player.getAbilities();
        if (!ab.creativeMode) {
            ab.allowFlying = false;
            ab.flying      = false;
        }
        client.player.sendAbilitiesUpdate();
        tickCounter = 0;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        double speed  = parseDouble(getSetting("Speed"),  0.8);
        double height = parseDouble(getSetting("Height"), 0.42);

        tickCounter++;
        Vec3d vel = client.player.getVelocity();

        if (tickCounter % 2 == 0) {
            // "Ground" tick — snap down slightly to register as grounded
            client.player.setVelocity(vel.x, -0.04, vel.z);
            client.player.setOnGround(true);
        } else {
            // "Fly" tick — boost upward by height
            client.player.setVelocity(vel.x, height, vel.z);
        }

        // Apply horizontal movement
        Vec3d look  = client.player.getRotationVec(1.0f);
        boolean fwd = client.options.forwardKey.isPressed();
        boolean bk  = client.options.backKey.isPressed();
        if (fwd || bk) {
            double dir = fwd ? 1.0 : -1.0;
            client.player.setVelocity(look.x * speed * dir, client.player.getVelocity().y, look.z * speed * dir);
        }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}

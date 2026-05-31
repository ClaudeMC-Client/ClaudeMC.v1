package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class FreeCam extends Module {

    public static FreeCam INSTANCE;
    private Vec3d savedPos;
    private float savedYaw, savedPitch;

    public FreeCam() {
        super("FreeCam", "Detach your camera from your player body", Category.RENDER);
        addSetting("Speed", "0.2");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) {
            savedPos   = c.player.getPos();
            savedYaw   = c.player.getYaw();
            savedPitch = c.player.getPitch();
            c.player.getAbilities().flying = true;
        }
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null && savedPos != null) {
            c.player.refreshPositionAndAngles(savedPos.x, savedPos.y, savedPos.z, savedYaw, savedPitch);
            if (!c.player.getAbilities().allowFlying) c.player.getAbilities().flying = false;
            c.player.setVelocity(Vec3d.ZERO);
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        float spd;
        try { spd = Float.parseFloat(getSetting("Speed")); } catch (Exception e) { spd = 0.2f; }
        var opts = client.options;
        float yaw = (float) Math.toRadians(client.player.getYaw());
        float pitch = (float) Math.toRadians(client.player.getPitch());

        double mx = 0, my = 0, mz = 0;
        if (opts.forwardKey.isPressed())  { mx -= Math.sin(yaw) * Math.cos(pitch); my -= Math.sin(pitch); mz += Math.cos(yaw) * Math.cos(pitch); }
        if (opts.backKey.isPressed())     { mx += Math.sin(yaw) * Math.cos(pitch); my += Math.sin(pitch); mz -= Math.cos(yaw) * Math.cos(pitch); }
        if (opts.jumpKey.isPressed())     my += spd;
        if (opts.sneakKey.isPressed())    my -= spd;

        client.player.setVelocity(mx * spd, my * spd, mz * spd);
    }
}

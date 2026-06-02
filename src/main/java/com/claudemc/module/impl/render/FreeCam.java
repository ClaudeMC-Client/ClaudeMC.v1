package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;

public class FreeCam extends Module {

    public static FreeCam INSTANCE;
    private Vec3d savedPos;
    private float savedYaw, savedPitch;
    private Perspective savedPerspective;

    public FreeCam() {
        super("FreeCam", "Detach your camera from your player body", Category.RENDER);
        addNumber("Speed", 0.3, 0.05, 2.0, 0.05, false);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player == null) return;
        savedPos         = c.player.getPos();
        savedYaw         = c.player.getYaw();
        savedPitch       = c.player.getPitch();
        savedPerspective = c.options.getPerspective();
        c.player.noClip  = true;
        c.player.getAbilities().flying = true;
        // Switch to third-person so the player model is visible
        if (savedPerspective == Perspective.FIRST_PERSON) {
            c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player == null) return;
        c.player.noClip = false;
        c.player.setVelocity(Vec3d.ZERO);
        if (!c.player.getAbilities().allowFlying) c.player.getAbilities().flying = false;
        if (savedPos != null) {
            c.player.refreshPositionAndAngles(savedPos.x, savedPos.y, savedPos.z, savedYaw, savedPitch);
        }
        if (savedPerspective != null) {
            c.options.setPerspective(savedPerspective);
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        double spd;
        try { spd = Double.parseDouble(getSetting("Speed")); } catch (Exception e) { spd = 0.3; }

        var opts = client.options;
        float yaw   = (float) Math.toRadians(client.player.getYaw());
        float pitch = (float) Math.toRadians(client.player.getPitch());

        double mx = 0, my = 0, mz = 0;
        if (opts.forwardKey.isPressed()) { mx -= Math.sin(yaw) * Math.cos(pitch); my -= Math.sin(pitch); mz += Math.cos(yaw) * Math.cos(pitch); }
        if (opts.backKey.isPressed())    { mx += Math.sin(yaw) * Math.cos(pitch); my += Math.sin(pitch); mz -= Math.cos(yaw) * Math.cos(pitch); }
        if (opts.jumpKey.isPressed())    my += 1;
        if (opts.sneakKey.isPressed())   my -= 1;

        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        if (len > 0) { mx /= len; my /= len; mz /= len; }

        client.player.noClip = true;
        client.player.setVelocity(mx * spd, my * spd, mz * spd);
    }
}

package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Minimap-style radar drawn on the HUD via HudManager.
 * Entities are rendered as coloured dots scaled by distance.
 */
public class Radar extends Module {

    public static Radar INSTANCE;

    public Radar() {
        super("Radar", "HUD minimap showing nearby players and mobs as dots", Category.RENDER);
        addNumber("Range", 64.0, 16.0, 128.0, 8.0, false);
        addNumber("Size",  80.0, 40.0, 160.0, 8.0, false);
        addMode("Anchor", "TopRight", "TopRight", "TopLeft", "BottomLeft", "BottomRight");
        INSTANCE = this;
    }

    @Override public void onTick(MinecraftClient client) {}

    /** Called by HudManager to draw the radar overlay. */
    public void render(DrawContext ctx, MinecraftClient client) {
        if (!isEnabled() || client.player == null || client.world == null) return;

        double range  = parseDouble(getSetting("Range"), 64.0);
        int size      = parseInt(getSetting("Size"),  80);
        int half      = size / 2;
        String anchor = getSetting("Anchor");

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        int ox, oy;
        switch (anchor) {
            case "TopLeft"     -> { ox = 4;          oy = 4; }
            case "BottomLeft"  -> { ox = 4;          oy = sh - size - 4; }
            case "BottomRight" -> { ox = sw - size - 4; oy = sh - size - 4; }
            default            -> { ox = sw - size - 4; oy = 4; }
        }

        // Background
        ctx.fill(ox, oy, ox + size, oy + size, 0x88000000);
        // Border
        ctx.fill(ox,          oy,          ox + size,     oy + 1,        0xFF555555);
        ctx.fill(ox,          oy + size-1, ox + size,     oy + size,     0xFF555555);
        ctx.fill(ox,          oy,          ox + 1,        oy + size,     0xFF555555);
        ctx.fill(ox + size-1, oy,          ox + size,     oy + size,     0xFF555555);

        // Player dot (white, centre)
        ctx.fill(ox + half - 1, oy + half - 1, ox + half + 1, oy + half + 1, 0xFFFFFFFF);

        // Entity dots
        double scale = half / range;
        var eyePos = client.player.getEntityPos();
        float yaw = client.player.getYaw();
        double sinYaw = Math.sin(Math.toRadians(yaw));
        double cosYaw = Math.cos(Math.toRadians(yaw));

        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;

            double dx = e.getX() - eyePos.x;
            double dz = e.getZ() - eyePos.z;
            if (Math.abs(dx) > range || Math.abs(dz) > range) continue;

            // Rotate so forward is up on the radar
            double rx =  dx * cosYaw - dz * sinYaw;
            double rz = -dx * sinYaw - dz * cosYaw;

            int dotX = ox + half + (int)(rx * scale);
            int dotZ = oy + half + (int)(rz * scale);

            if (dotX < ox || dotX >= ox + size || dotZ < oy || dotZ >= oy + size) continue;

            int colour = 0xFF00FF00;
            if (le instanceof PlayerEntity)  colour = 0xFFFF4444;
            else if (le instanceof HostileEntity) colour = 0xFFFF8800;

            ctx.fill(dotX - 1, dotZ - 1, dotX + 1, dotZ + 1, colour);
        }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}

package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Breadcrumbs extends Module {

    private final Deque<Vec3d> trail = new ArrayDeque<>();
    private Vec3d lastPos = null;
    private static Breadcrumbs INSTANCE;

    public Breadcrumbs() {
        super("Breadcrumbs", "Draws a line trail behind you to retrace your path", Category.RENDER);
        addNumber("MaxPoints", 500, 50, 2000, 50, true);
        addNumber("MinDist",   1.0, 0.5, 5.0, 0.5, false);
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            if (trail.size() < 2) return;

            var cam      = context.worldState().cameraRenderState.pos;
            var matrices = context.matrices();
            if (matrices == null) return;
            var consumers = context.consumers();
            if (consumers == null) return;

            List<Vec3d> relPoints = new ArrayList<>(trail.size());
            for (Vec3d p : trail) {
                relPoints.add(p.subtract(cam).add(0, 0.1, 0));
            }
            RenderUtils.drawLineStrip(matrices, consumers, relPoints, 0.5f, 1f, 0.5f, 1f);
        });
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        Vec3d pos = client.player.getEntityPos();
        double minDist = parseDouble(getSetting("MinDist"), 1.0);
        if (lastPos != null && lastPos.squaredDistanceTo(pos) < minDist * minDist) return;

        trail.addLast(pos);
        lastPos = pos;

        int max = parseInt(getSetting("MaxPoints"), 500);
        while (trail.size() > max) trail.pollFirst();
    }

    @Override
    public void onDisable() {
        trail.clear();
        lastPos = null;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}

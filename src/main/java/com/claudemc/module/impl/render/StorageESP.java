package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class StorageESP extends Module {

    public static StorageESP INSTANCE;

    // Scanning block entities is expensive, so it runs on a throttled tick rather than
    // every rendered frame. The render event only draws this cached snapshot.
    private static final int SCAN_INTERVAL_TICKS = 8;
    private static final int MAX_RADIUS          = 64;
    private int scanCooldown = 0;
    private volatile java.util.List<Found> found = java.util.Collections.emptyList();

    /** A matched container with its precomputed fill colour. */
    private record Found(int x, int y, int z, float r, float g, float b) {}

    public StorageESP() {
        super("StorageESP", "Shows container contents indicator through walls", Category.RENDER);
        addSetting("Radius", "32");
        addSetting("ShowFull",  "true");
        addSetting("ShowEmpty", "false");
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam = context.camera().getPos();
            var matrices = context.matrixStack();
            if (matrices == null) return;
            var consumers = context.consumers();
            if (consumers == null) return;

            for (Found f : INSTANCE.found) {
                double bx = f.x() - cam.x, by = f.y() - cam.y, bz = f.z() - cam.z;
                Box box = new Box(bx + 0.05, by + 0.05, bz + 0.05, bx + 0.95, by + 0.95, bz + 0.95);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, f.r(), f.g(), f.b(), 1f);
            }
        });
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (--scanCooldown > 0) return;
        scanCooldown = SCAN_INTERVAL_TICKS;

        int radius = Math.min(MAX_RADIUS, parseInt(getSetting("Radius"), 32));
        var pPos = client.player.getBlockPos();

        java.util.List<Found> results = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.iterateOutwards(pPos, radius, radius, radius)) {
            var be = client.world.getBlockEntity(pos);
            if (!isContainer(be)) continue;

            float fill = getFill(be);
            // Green = full, red = empty, yellow = partially full
            float r = fill < 0.1f ? 1f : (fill < 0.9f ? 1f : 0f);
            float g = fill < 0.1f ? 0f : (fill < 0.9f ? 0.8f : 1f);
            results.add(new Found(pos.getX(), pos.getY(), pos.getZ(), r, g, 0f));
        }
        found = results;
    }

    private int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }

    private boolean isContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof ShulkerBoxBlockEntity
            || be instanceof BarrelBlockEntity || be instanceof HopperBlockEntity
            || be instanceof FurnaceBlockEntity || be instanceof DispenserBlockEntity;
    }

    private float getFill(BlockEntity be) {
        if (be instanceof ChestBlockEntity c) {
            int used = 0;
            for (int i = 0; i < c.size(); i++) if (!c.getStack(i).isEmpty()) used++;
            return (float) used / c.size();
        }
        if (be instanceof ShulkerBoxBlockEntity s) {
            int used = 0;
            for (int i = 0; i < s.size(); i++) if (!s.getStack(i).isEmpty()) used++;
            return (float) used / s.size();
        }
        return 0.5f;
    }
}

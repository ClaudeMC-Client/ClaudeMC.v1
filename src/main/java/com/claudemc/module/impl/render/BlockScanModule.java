package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for ESP modules that highlight blocks/containers in a radius.
 *
 * Scanning the world is expensive, so it is done on a throttled client tick (not per frame)
 * into an immutable snapshot; the render event only draws that cached snapshot. This keeps
 * the heavy {@code getBlockState}/{@code getBlockEntity} sweeps off the render path so frame
 * rate is unaffected by the scan size.
 *
 * Subclasses implement {@link #scan(MinecraftClient, List)} to populate the highlight list.
 */
public abstract class BlockScanModule extends Module {

    /** A highlighted block position with its outline colour. */
    public record Highlight(int x, int y, int z, float r, float g, float b) {}

    protected int    scanIntervalTicks = 8;     // rescan ~2.5x/second
    protected int    maxRadius         = 64;     // hard cap to bound scan cost
    protected double boxExpand         = 0.01;   // outline inflation (negative = inset)

    private int scanCooldown = 0;
    private volatile List<Highlight> found = Collections.emptyList();

    protected BlockScanModule(String name, String description, Category category) {
        super(name, description, category);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam       = context.worldState().cameraRenderState.pos;
            var matrices  = context.matrices();
            if (matrices == null) return;
            var consumers = context.consumers();
            if (consumers == null) return;

            for (Highlight h : found) {
                double bx = h.x() - cam.x, by = h.y() - cam.y, bz = h.z() - cam.z;
                Box box = new Box(bx, by, bz, bx + 1, by + 1, bz + 1).expand(boxExpand);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, h.r(), h.g(), h.b(), 1f);
            }
        });
    }

    @Override
    public final void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (--scanCooldown > 0) return;
        scanCooldown = scanIntervalTicks;

        List<Highlight> results = new ArrayList<>();
        scan(client, results);
        found = results;   // publish snapshot for the render path
    }

    @Override
    public void onDisable() {
        found = Collections.emptyList();
    }

    /** Effective, clamped scan radius from the "Radius" setting (default {@code def}). */
    protected int radius(int def) {
        return Math.min(maxRadius, parseInt(getSetting("Radius"), def));
    }

    /** Populate {@code out} with every block to highlight this scan. Runs off the render path. */
    protected abstract void scan(MinecraftClient client, List<Highlight> out);

    protected static int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }
}

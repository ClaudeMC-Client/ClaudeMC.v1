package com.claudemc.render;

import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Line-rendering helpers for ESP/tracer modules.
 *
 * 1.21.5+ removed {@code WorldRenderer.drawBox} and moved the line render layer
 * from {@code RenderLayer.LINES} to {@code RenderLayers.LINES}. Boxes are now drawn
 * as twelve explicit edges via the still-supported {@code VertexConsumer.vertex(Entry, …)}
 * / {@code normal(Entry, …)} default overloads.
 *
 * The RenderLayers.LINES render layer is configured to render without depth testing
 * in the 1.21.11 pipeline, allowing lines/boxes to render through walls.
 */
public final class RenderUtils {

    private RenderUtils() {}

    public static void drawOutlinedBox(MatrixStack matrices, VertexConsumerProvider consumers,
                                       Box box, float r, float g, float b, float a) {
        VertexConsumer lines = consumers.getBuffer(RenderLayers.LINES);
        var entry = matrices.peek();

        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        // Bottom rectangle
        edge(lines, entry, x1, y1, z1, x2, y1, z1, r, g, b, a);
        edge(lines, entry, x2, y1, z1, x2, y1, z2, r, g, b, a);
        edge(lines, entry, x2, y1, z2, x1, y1, z2, r, g, b, a);
        edge(lines, entry, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // Top rectangle
        edge(lines, entry, x1, y2, z1, x2, y2, z1, r, g, b, a);
        edge(lines, entry, x2, y2, z1, x2, y2, z2, r, g, b, a);
        edge(lines, entry, x2, y2, z2, x1, y2, z2, r, g, b, a);
        edge(lines, entry, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // Vertical pillars
        edge(lines, entry, x1, y1, z1, x1, y2, z1, r, g, b, a);
        edge(lines, entry, x2, y1, z1, x2, y2, z1, r, g, b, a);
        edge(lines, entry, x2, y1, z2, x2, y2, z2, r, g, b, a);
        edge(lines, entry, x1, y1, z2, x1, y2, z2, r, g, b, a);

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayers.LINES);
        }
    }

    /**
     * Draws a connected poly-line through {@code points} (already in camera-relative space)
     * with a single buffer flush. Used for projectile trajectory arcs.
     */
    public static void drawLineStrip(MatrixStack matrices, VertexConsumerProvider consumers,
                                     java.util.List<Vec3d> points,
                                     float r, float g, float b, float a) {
        if (points.size() < 2) return;
        VertexConsumer lines = consumers.getBuffer(RenderLayers.LINES);
        var entry = matrices.peek();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d p0 = points.get(i);
            Vec3d p1 = points.get(i + 1);
            edge(lines, entry,
                 (float) p0.x, (float) p0.y, (float) p0.z,
                 (float) p1.x, (float) p1.y, (float) p1.z,
                 r, g, b, a);
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayers.LINES);
        }
    }

    public static void drawLine(MatrixStack matrices, VertexConsumerProvider consumers,
                                Vec3d from, Vec3d to, float r, float g, float b, float a) {
        VertexConsumer lines = consumers.getBuffer(RenderLayers.LINES);
        var entry = matrices.peek();
        edge(lines, entry,
             (float) from.x, (float) from.y, (float) from.z,
             (float) to.x,   (float) to.y,   (float) to.z,
             r, g, b, a);

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayers.LINES);
        }
    }

    /** Emits a single line segment with a unit normal pointing along its direction. */
    private static void edge(VertexConsumer lines, MatrixStack.Entry entry,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len == 0) { nx = 0; ny = 1; nz = 0; } else { nx /= len; ny /= len; nz /= len; }

        // MC 1.21.4+ updated VertexFormats.LINES to include a LINE_WIDTH element.
        // Calling lineWidth() is required or the BufferBuilder throws on flush.
        lines.vertex(entry, x1, y1, z1).color(r, g, b, a).normal(entry, nx, ny, nz).lineWidth(2.0f);
        lines.vertex(entry, x2, y2, z2).color(r, g, b, a).normal(entry, nx, ny, nz).lineWidth(2.0f);
    }
}

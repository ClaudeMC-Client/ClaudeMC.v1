package com.claudemc.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class RenderUtils {

    private RenderUtils() {}

    public static void drawOutlinedBox(MatrixStack matrices, VertexConsumerProvider consumers,
                                       Box box, float r, float g, float b, float a) {
        VertexConsumer lines = consumers.getBuffer(RenderLayer.LINES);
        WorldRenderer.drawBox(matrices, lines, box, r, g, b, a);

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayer.LINES);
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
        VertexConsumer lines = consumers.getBuffer(RenderLayer.LINES);
        var entry = matrices.peek();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d p0 = points.get(i);
            Vec3d p1 = points.get(i + 1);
            Vec3d normal = p1.subtract(p0);
            double len = normal.length();
            normal = len == 0 ? new Vec3d(0, 1, 0) : normal.multiply(1.0 / len);

            lines.vertex(entry, (float) p0.x, (float) p0.y, (float) p0.z)
                 .color(r, g, b, a)
                 .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
            lines.vertex(entry, (float) p1.x, (float) p1.y, (float) p1.z)
                 .color(r, g, b, a)
                 .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayer.LINES);
        }
    }

    public static void drawLine(MatrixStack matrices, VertexConsumerProvider consumers,
                                Vec3d from, Vec3d to, float r, float g, float b, float a) {
        VertexConsumer lines = consumers.getBuffer(RenderLayer.LINES);
        var entry = matrices.peek();

        Vec3d normal = to.subtract(from).normalize();

        lines.vertex(entry, (float) from.x, (float) from.y, (float) from.z)
             .color(r, g, b, a)
             .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);

        lines.vertex(entry, (float) to.x, (float) to.y, (float) to.z)
             .color(r, g, b, a)
             .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayer.LINES);
        }
    }
}

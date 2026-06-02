package com.claudemc.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Line-rendering helpers for ESP / tracer modules.
 *
 * Rendering is done in immediate mode with the depth test DISABLED so outlines and
 * tracers are visible through walls (the standard ESP behaviour). The previous
 * implementation drew through {@code RenderLayer.LINES}, whose phase setup re-enables a
 * LEQUAL depth test when the buffer is flushed — meaning a plain {@code RenderSystem}
 * depth toggle around the draw was overridden and lines were occluded by geometry.
 * Drawing with our own GL state via the {@code POSITION_COLOR} program avoids that.
 *
 * The {@link VertexConsumerProvider} parameters are retained for call-site compatibility
 * but are intentionally unused.
 */
public final class RenderUtils {

    private RenderUtils() {}

    private static void begin(float lineWidth) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(lineWidth);
    }

    private static void end() {
        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void drawOutlinedBox(MatrixStack matrices, VertexConsumerProvider consumers,
                                       Box box, float r, float g, float b, float a) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        begin(1.5f);
        BufferBuilder buf = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // Bottom rectangle
        edge(buf, m, x1, y1, z1, x2, y1, z1, r, g, b, a);
        edge(buf, m, x2, y1, z1, x2, y1, z2, r, g, b, a);
        edge(buf, m, x2, y1, z2, x1, y1, z2, r, g, b, a);
        edge(buf, m, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // Top rectangle
        edge(buf, m, x1, y2, z1, x2, y2, z1, r, g, b, a);
        edge(buf, m, x2, y2, z1, x2, y2, z2, r, g, b, a);
        edge(buf, m, x2, y2, z2, x1, y2, z2, r, g, b, a);
        edge(buf, m, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // Vertical pillars
        edge(buf, m, x1, y1, z1, x1, y2, z1, r, g, b, a);
        edge(buf, m, x2, y1, z1, x2, y2, z1, r, g, b, a);
        edge(buf, m, x2, y1, z2, x2, y2, z2, r, g, b, a);
        edge(buf, m, x1, y1, z2, x1, y2, z2, r, g, b, a);

        draw(buf);
        end();
    }

    /**
     * Draws a connected poly-line through {@code points} (already in camera-relative space).
     * Used for projectile trajectory arcs.
     */
    public static void drawLineStrip(MatrixStack matrices, VertexConsumerProvider consumers,
                                     java.util.List<Vec3d> points,
                                     float r, float g, float b, float a) {
        if (points.size() < 2) return;
        Matrix4f m = matrices.peek().getPositionMatrix();

        begin(2f);
        BufferBuilder buf = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d p0 = points.get(i);
            Vec3d p1 = points.get(i + 1);
            edge(buf, m, (float) p0.x, (float) p0.y, (float) p0.z,
                         (float) p1.x, (float) p1.y, (float) p1.z, r, g, b, a);
        }
        draw(buf);
        end();
    }

    public static void drawLine(MatrixStack matrices, VertexConsumerProvider consumers,
                                Vec3d from, Vec3d to, float r, float g, float b, float a) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        begin(1.5f);
        BufferBuilder buf = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        edge(buf, m, (float) from.x, (float) from.y, (float) from.z,
                     (float) to.x,   (float) to.y,   (float) to.z, r, g, b, a);
        draw(buf);
        end();
    }

    private static void draw(BufferBuilder buf) {
        BuiltBuffer built = buf.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    private static void edge(BufferBuilder buf, Matrix4f m,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
    }
}

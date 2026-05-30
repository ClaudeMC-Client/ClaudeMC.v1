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

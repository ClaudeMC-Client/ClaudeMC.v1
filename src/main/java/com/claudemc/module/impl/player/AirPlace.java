package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Allows placing blocks in mid-air by synthesising a fake block-face hit when
 * the crosshair is aimed at air but a block is held.
 */
public class AirPlace extends Module {

    public static AirPlace INSTANCE;

    public AirPlace() {
        super("AirPlace", "Allows placing blocks in mid-air", Category.PLAYER);
        addNumber("Range", 5, 1, 6, 0.5, false);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.options.useKey == null || !client.options.useKey.isPressed()) return;

        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty() || !(held.getItem() instanceof BlockItem)) return;

        HitResult hit = client.crosshairTarget;
        // Only act when the crosshair is in air (no block face)
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) return;

        double range = parseDouble(getSetting("Range"), 5.0);
        Vec3d eyes   = client.player.getEyePos();
        Vec3d look   = client.player.getRotationVec(1.0f);
        Vec3d target = eyes.add(look.multiply(range));

        BlockPos pos = BlockPos.ofFloored(target);
        Direction face = Direction.getFacing(
            (float) -look.x, (float) -look.y, (float) -look.z);

        // Offer the interaction with the bottom face of that air position
        BlockHitResult fakeHit = new BlockHitResult(target, face, pos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, fakeHit);
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}

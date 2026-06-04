package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Allows placing blocks inside liquids by injecting a fake block-face hit result.
 * When the crosshair targets a liquid block, this module right-clicks using the
 * surface face so the server accepts the block placement.
 */
public class Liquids extends Module {

    public static Liquids INSTANCE;

    public Liquids() {
        super("Liquids", "Allows placing blocks inside liquids", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.options.useKey == null || !client.options.useKey.isPressed()) return;

        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos   = ((BlockHitResult) hit).getBlockPos();
        var state = client.world.getBlockState(pos);

        if (!state.getFluidState().isEmpty()) {
            // Synthesise a placement on the top face of the liquid block
            BlockHitResult fakeHit = new BlockHitResult(
                Vec3d.ofCenter(pos).add(0, 0.5, 0),
                Direction.UP,
                pos,
                false
            );
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, fakeHit);
        }
    }
}

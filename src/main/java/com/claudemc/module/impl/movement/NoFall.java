package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Prevents fall damage by sending onGround=true in every movement packet while falling.
 *
 * Approach adapted from Wurst7 (confirmed working on MC 1.21.x):
 * Send a PlayerMoveC2SPacket.OnGroundOnly(true) packet each tick while airborne.
 * The server resets its "last grounded position" every tick, so the calculated fall
 * distance when the player actually lands is always ≤ 1 block (no damage).
 *
 * The ClientPlayerEntityMixin also spoofs onGround=true in the regular movement packet
 * that sendMovementPackets emits, as a second layer of coverage.
 */
public class NoFall extends Module {

    public static NoFall INSTANCE;

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        if (client.player.isGliding()) return;
        if (client.player.getAbilities().flying) return;
        if (client.player.isOnGround()) return;
        if (client.player.fallDistance <= 0.0f) return;

        // Wurst7 technique: send a status-only packet declaring onGround=true every tick.
        // Args: (boolean onGround, boolean horizontalCollision)
        client.getNetworkHandler().sendPacket(
            new PlayerMoveC2SPacket.OnGroundOnly(true, client.player.horizontalCollision));

        // Also clear local fall distance so no damage sound/animation client-side
        client.player.fallDistance = 0;
    }
}

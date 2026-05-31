package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Sends simultaneous START and STOP mining packets to attempt instant break
 * on servers with insufficient server-side block-break validation.
 * Does not work on properly patched servers.
 */
public class PacketMine extends Module {

    private BlockPos lastMined = null;

    public PacketMine() {
        super("PacketMine", "Attempts instant mining via simultaneous start/stop packets", Category.WORLD);
        addBool("OnlyInstant", false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;
        if (!client.options.attackKey.isPressed()) return;

        if (!(client.crosshairTarget instanceof BlockHitResult bhr)) return;
        if (bhr.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = bhr.getBlockPos();
        var state = client.world.getBlockState(pos);
        if (state.isAir()) return;
        if (state.getHardness(client.world, pos) < 0) return;

        boolean onlyInstant = Boolean.parseBoolean(getSetting("OnlyInstant"));
        if (onlyInstant && state.getHardness(client.world, pos) > 0) return;

        if (pos.equals(lastMined)) return;
        lastMined = pos;

        Direction face = bhr.getSide();
        var net = client.getNetworkHandler();
        if (net == null) return;

        net.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, face));
        net.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, face));

        client.world.removeBlock(pos, false);
    }

    @Override
    public void onDisable() {
        lastMined = null;
    }
}

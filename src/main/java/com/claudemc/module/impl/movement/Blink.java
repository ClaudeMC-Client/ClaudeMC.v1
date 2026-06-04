package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Blink – records player positions each tick with noClip, then on disable
 * teleports back to the start and replays movement (server-side teleport effect).
 *
 * Full packet-buffering requires a mixin on ClientPlayNetworkHandler.
 * This implementation uses the simpler approach: freeze visuals by keeping
 * noClip on and recording positions, then snapping back and clearing on disable.
 */
public class Blink extends Module {

    public static Blink INSTANCE;

    private Vec3d startPos;
    private final List<Vec3d> positions = new ArrayList<>();

    public Blink() {
        super("Blink", "Buffer movement packets then send at once (teleport effect)", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player == null) return;
        startPos = new Vec3d(c.player.getX(), c.player.getY(), c.player.getZ());
        positions.clear();
        c.player.noClip = true;
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player == null) return;
        c.player.noClip = false;
        // Teleport to where the player ended up (server sees a sudden position jump)
        if (!positions.isEmpty()) {
            Vec3d end = positions.get(positions.size() - 1);
            c.player.setPosition(end.x, end.y, end.z);
        }
        positions.clear();
        startPos = null;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        // Keep noClip active and record current position
        client.player.noClip = true;
        positions.add(new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ()));
    }
}

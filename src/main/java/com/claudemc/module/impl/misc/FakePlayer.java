package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/**
 * Spawns a fake client-side player entity at the player's current position.
 * Useful as a macro position marker or for testing purposes.
 * The fake player does not interact with the server; it is client-only.
 */
public class FakePlayer extends Module {

    private OtherClientPlayerEntity fakeEntity = null;

    public FakePlayer() {
        super("FakePlayer", "Spawns a fake client-side player entity at your position", Category.MISC);
        addSetting("Name", "FakePlayer");
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || !(client.world instanceof ClientWorld world)) return;
        if (fakeEntity != null) removeFake(world);

        String name = getSetting("Name");
        var profile = new com.mojang.authlib.GameProfile(
            java.util.UUID.randomUUID(), name.isBlank() ? "FakePlayer" : name);

        fakeEntity = new OtherClientPlayerEntity(world, profile);
        fakeEntity.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
        fakeEntity.setYaw(client.player.getYaw());
        fakeEntity.setPitch(client.player.getPitch());
        world.addEntity(fakeEntity);
    }

    @Override
    public void onDisable() {
        var client = MinecraftClient.getInstance();
        if (client.world instanceof ClientWorld world) removeFake(world);
    }

    private void removeFake(ClientWorld world) {
        if (fakeEntity != null) {
            world.removeEntity(fakeEntity.getId(), net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            fakeEntity = null;
        }
    }

    @Override public void onTick(MinecraftClient client) {}
}

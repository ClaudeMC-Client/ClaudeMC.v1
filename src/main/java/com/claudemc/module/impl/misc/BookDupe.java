package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;

import java.util.List;
import java.util.Optional;

/**
 * Book dupe exploit — sends multiple BookUpdateC2SPackets before the server
 * processes the first one, resulting in duplicate signed books.
 *
 * FROM dupedb.net: Works on servers without packet rate-limiting.
 * 1) Hold a written book in hand
 * 2) Enable this module
 * 3) It fires 5 consecutive book-sign packets, each yielding one book
 *
 * Note: Patched on most modern servers with Paper/Purpur.
 */
public class BookDupe extends Module {

    private int triggered = 0;

    public BookDupe() {
        super("BookDupe", "dupedb.net — Duplicates signed books via packet spam", Category.MISC);
        addSetting("Copies", "5");
        addSetting("Title",  "Duped Book");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        var stack = client.player.getMainHandStack();
        if (stack.getItem() != Items.WRITABLE_BOOK && stack.getItem() != Items.WRITTEN_BOOK) return;
        if (triggered <= 0) return;

        int copies = parseInt(getSetting("Copies"), 5);
        String title = getSetting("Title");

        for (int i = 0; i < copies; i++) {
            client.getNetworkHandler().sendPacket(new BookUpdateC2SPacket(
                client.player.getInventory().selectedSlot,
                List.of("Duped page"),
                Optional.of(title)
            ));
        }
        triggered = 0;
    }

    /** Call this from keybind or right-click to trigger a dupe cycle. */
    public void triggerDupe() { triggered = 1; }

    @Override
    public void onEnable() {
        // Trigger once on enable
        triggered = 1;
    }

    @Override
    public void onDisable() { triggered = 0; }

    private int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }
}

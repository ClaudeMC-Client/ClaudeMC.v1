package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

/**
 * AuctionDupe — automates timing-sensitive auction-house dupe exploits
 * documented on dupedb.net.
 *
 * SUPPORTED TECHNIQUES
 * ─────────────────────────────────────────────────────────────────────
 * 1. WindowClose race (Technique=WindowClose)
 *    Works on: servers whose auction-house plugin commits item data on
 *    the container-close packet rather than on a confirmation click.
 *    When you open the "cancel auction" confirmation GUI, rapidly send a
 *    CloseHandledScreenC2SPacket before the server finalises the cancel
 *    — many plugin versions return the item AND leave the listing active.
 *
 * 2. DoubleCancel (Technique=DoubleCancel)
 *    Works on: plugins that decrement listing count asynchronously.
 *    Sends N rapid cancel-confirmation clicks in one tick.  Race
 *    condition causes multiple item returns before the listing is
 *    marked deleted.  Typically effective on thread-unsafe Bukkit
 *    plugins on Paper without async-safe data stores.
 *
 * 3. Reconnect-timing assist (Technique=Reconnect)
 *    Works on: servers without rollback protection (most Spigot/Bukkit).
 *    Automates the disconnect at the exact tick the item is in limbo
 *    (returned to inventory but not yet persisted to the DB).
 *    On reconnect: inventory keeps the returned item, auction DB still
 *    shows the item sold / cancelled with coins paid out.
 *    NOTE: timing is best-effort and server-dependent.  Works reliably
 *    only when server save interval is ≥ 6 seconds and chunk-data is
 *    not sent instantly.
 *
 * HOW TO USE
 * ──────────
 * 1. Open auction house on the server (or whichever AH GUI).
 * 2. Navigate to your listed item's cancel / retrieve screen.
 * 3. Enable AuctionDupe — it fires on enable, then auto-disables.
 * 4. Watch chat / inventory for the result.
 *
 * PATCHED ON: modern Paper + any AH plugin compiled after 2023 with
 * synchronous item commits (AuctionHouse by Kicjow 1.3.6+, GUIShop,
 * Marketplaces with Redis back-ends).
 */
public class AuctionDupe extends Module {

    // Ticks to wait after enabling before firing (gives the AH screen time to open)
    private static final int DELAY_TICKS = 3;

    private int ticker = -1;

    public AuctionDupe() {
        super("AuctionDupe",
              "Automates auction-house dupe exploits (WindowClose / DoubleCancel / Reconnect)",
              Category.EXPLOIT);
        addSetting("Technique", "WindowClose"); // WindowClose | DoubleCancel | Reconnect
        addSetting("Packets", "5");             // how many rapid packets to send (DoubleCancel)
        addSetting("DelayTicks", "3");          // ticks to wait before firing
    }

    @Override
    public void onEnable() {
        ticker = 0;
        ClaudeMCMod.LOGGER.info("[AuctionDupe] Armed — technique: {}", getSetting("Technique"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || ticker < 0) return;

        int delay = parseIntSetting("DelayTicks", DELAY_TICKS);
        if (ticker < delay) { ticker++; return; }

        String tech = getSetting("Technique");
        switch (tech) {
            case "WindowClose"  -> doWindowClose(client);
            case "DoubleCancel" -> doDoubleCancel(client);
            case "Reconnect"    -> doReconnect(client);
            default             -> doWindowClose(client);
        }

        // Auto-disable after one attempt
        ticker = -1;
        setEnabled(false);
    }

    // ── Techniques ────────────────────────────────────────────────────────

    /**
     * Sends a CloseHandledScreenC2SPacket while the AH cancel GUI is open.
     * Race condition: item returned before cancel is finalised.
     */
    private void doWindowClose(MinecraftClient client) {
        try {
            if (client.player.currentScreenHandler == null) {
                ClaudeMCMod.LOGGER.warn("[AuctionDupe] No screen open — open the AH cancel GUI first.");
                return;
            }
            int syncId = client.player.currentScreenHandler.syncId;
            int count  = parseIntSetting("Packets", 5);
            for (int i = 0; i < count; i++) {
                client.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(syncId));
            }
            ClaudeMCMod.LOGGER.info("[AuctionDupe][WindowClose] Sent {} close packets (syncId={})", count, syncId);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AuctionDupe][WindowClose] {}", e.getMessage());
        }
    }

    /**
     * Rapid cancel-confirmation clicks: sends slot-click packets for the
     * confirm button multiple times in the same tick.
     */
    private void doDoubleCancel(MinecraftClient client) {
        try {
            if (client.player.currentScreenHandler == null) {
                ClaudeMCMod.LOGGER.warn("[AuctionDupe] No screen open — open the AH cancel confirm screen first.");
                return;
            }
            int syncId = client.player.currentScreenHandler.syncId;
            int count  = parseIntSetting("Packets", 5);

            // Slot 0 is where most AH plugins place the confirm button.
            // Adjust if the target server uses a different layout.
            for (int i = 0; i < count; i++) {
                client.interactionManager.clickSlot(syncId, 0, 0,
                    net.minecraft.screen.slot.SlotActionType.PICKUP, client.player);
            }
            ClaudeMCMod.LOGGER.info("[AuctionDupe][DoubleCancel] Sent {} cancel clicks", count);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AuctionDupe][DoubleCancel] {}", e.getMessage());
        }
    }

    /**
     * Disconnects from the server at the moment the item is in limbo.
     * Works when the server has no rollback and uses periodic saves.
     */
    private void doReconnect(MinecraftClient client) {
        try {
            // First close the screen to trigger item return server-side
            if (client.player.currentScreenHandler != null) {
                int syncId = client.player.currentScreenHandler.syncId;
                client.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(syncId));
            }
            // Then immediately disconnect before the server persists the change
            client.getNetworkHandler().getConnection().disconnect(
                net.minecraft.text.Text.literal("[AuctionDupe] Reconnect disconnect"));
            ClaudeMCMod.LOGGER.info("[AuctionDupe][Reconnect] Disconnected — reconnect to collect duped item.");
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AuctionDupe][Reconnect] {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private int parseIntSetting(String key, int fallback) {
        try { return Integer.parseInt(getSetting(key)); }
        catch (NumberFormatException e) { return fallback; }
    }
}

package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class ChestStealer extends Module {

    private int delay = 0;

    public ChestStealer() {
        super("ChestStealer", "Automatically steals items from open containers", Category.PLAYER);
        addSetting("Delay", "2");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (!(client.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) return;
        if (delay-- > 0) return;

        int syncId = handler.syncId;
        int playerStart = handler.getRows() * 9;

        for (int i = 0; i < playerStart; i++) {
            Slot slot = handler.slots.get(i);
            if (!slot.hasStack()) continue;
            client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
            delay = parseInt(getSetting("Delay"), 2);
            return;
        }
    }

    private int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }
}

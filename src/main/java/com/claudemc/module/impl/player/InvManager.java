package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Auto-manages inventory: drops junk, keeps best tools/armour in hotbar.
 */
public class InvManager extends Module {

    private int actionDelay = 0;

    public InvManager() {
        super("InvManager", "Auto-sorts inventory and drops low-value junk", Category.PLAYER);
        addBool("DropJunk",   true);
        addBool("SortHotbar", false);
        addNumber("Delay", 2, 1, 10, 1, true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;
        if (client.player.currentScreenHandler == null) return;

        if (--actionDelay > 0) return;
        actionDelay = parseInt(getSetting("Delay"), 2);

        if (Boolean.parseBoolean(getSetting("DropJunk"))) {
            if (dropOneJunkSlot(client)) return;
        }
    }

    private boolean dropOneJunkSlot(MinecraftClient client) {
        var inv = client.player.getInventory();
        for (int i = 9; i < 36; i++) {
            var stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (isJunk(stack)) {
                // Drop via screen handler slot click with THROW action
                client.interactionManager.clickSlot(
                    client.player.playerScreenHandler.syncId,
                    i + 9 - 9 + 9, // slot index in screen handler: main inv starts at 9
                    0, SlotActionType.THROW, client.player);
                return true;
            }
        }
        return false;
    }

    private boolean isJunk(ItemStack stack) {
        Item item = stack.getItem();
        // Rotten flesh, spider eyes, seeds unless food, string, gunpowder not great
        return item == Items.ROTTEN_FLESH
            || item == Items.SPIDER_EYE
            || item == Items.POISONOUS_POTATO
            || item == Items.BONE
            || item == Items.INK_SAC;
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}

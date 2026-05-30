package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends Module {

    private int cooldown = 0;

    public AutoTotem() {
        super("AutoTotem", "Keeps a Totem of Undying in your offhand", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (cooldown-- > 0) return;

        var offhand = client.player.getOffHandStack();
        if (offhand.getItem() == Items.TOTEM_OF_UNDYING) return;

        var inv = client.player.getInventory();
        int totemSlot = -1;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                totemSlot = i; break;
            }
        }
        if (totemSlot < 0) return;

        // Map inventory index to screen-handler slot index
        int screenSlot = totemSlot < 9 ? totemSlot + 36 : totemSlot;
        int syncId = client.player.playerScreenHandler.syncId;

        // Swap to offhand (slot 45 in default player screen handler = offhand)
        client.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, client.player);
        cooldown = 5;
    }
}

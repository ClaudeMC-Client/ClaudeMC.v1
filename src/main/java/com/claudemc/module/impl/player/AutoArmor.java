package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class AutoArmor extends Module {

    private int cooldown = 0;

    public AutoArmor() {
        super("AutoArmor", "Automatically equips the best available armor", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (cooldown-- > 0) return;
        cooldown = 20;

        var inv = client.player.getInventory();
        int syncId = client.player.playerScreenHandler.syncId;

        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack stack = inv.main.get(i);
            if (!(stack.getItem() instanceof ArmorItem armor)) continue;

            EquipmentSlot slot = armor.getType().getEquipmentSlot();
            int armorIndex = switch (slot) {
                case HEAD  -> 3;
                case CHEST -> 2;
                case LEGS  -> 1;
                case FEET  -> 0;
                default    -> -1;
            };
            if (armorIndex < 0) continue;

            int screenSlot = i < 9 ? i + 36 : i;
            ItemStack current = client.player.getInventory().getArmorStack(armorIndex);
            if (current.isEmpty() || getArmorPoints(stack) > getArmorPoints(current)) {
                client.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.QUICK_MOVE, client.player);
                return;
            }
        }
    }

    private int getArmorPoints(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem a)) return 0;
        return a.getProtection();
    }
}

package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
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

        var inv    = client.player.getInventory();
        int syncId = client.player.playerScreenHandler.syncId;
        var main   = inv.getMainStacks();

        for (int i = 0; i < main.size(); i++) {
            ItemStack stack = main.get(i);
            // 1.21.x: armor is identified by the EQUIPPABLE component, not the removed ArmorItem class.
            EquippableComponent eq = stack.get(DataComponentTypes.EQUIPPABLE);
            if (eq == null) continue;
            EquipmentSlot slot = eq.slot();
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;

            int screenSlot = i < 9 ? i + 36 : i;
            ItemStack current = client.player.getEquippedStack(slot);
            if (current.isEmpty() || getArmorPoints(stack) > getArmorPoints(current)) {
                client.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.QUICK_MOVE, client.player);
                return;
            }
        }
    }

    /** Sum of the ARMOR attribute this stack grants (replaces ArmorItem.getProtection). */
    private int getArmorPoints(ItemStack stack) {
        AttributeModifiersComponent comp = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (comp == null) return 0;
        double armor = 0;
        for (AttributeModifiersComponent.Entry e : comp.modifiers()) {
            if (e.attribute() == EntityAttributes.ARMOR) armor += e.modifier().value();
        }
        return (int) Math.round(armor);
    }
}

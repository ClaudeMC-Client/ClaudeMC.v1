package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.lang.reflect.Field;

public class Step extends Module {

    private float oldStepHeight = 0.6f;

    public Step() {
        super("Step", "Step up full blocks instantly", Category.MOVEMENT);
        addSetting("Height", "1.0");
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) {
            oldStepHeight = getStepHeight(c.player);
            setStepHeight(c.player, parseFloat(getSetting("Height"), 1.0f));
        }
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) setStepHeight(c.player, oldStepHeight);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        float h = parseFloat(getSetting("Height"), 1.0f);
        if (getStepHeight(client.player) != h) setStepHeight(client.player, h);
    }

    private float getStepHeight(Entity entity) {
        try {
            Field f = findField(entity.getClass(), "stepHeight");
            f.setAccessible(true);
            return f.getFloat(entity);
        } catch (Exception e) { return 0.6f; }
    }

    private void setStepHeight(Entity entity, float value) {
        try {
            Field f = findField(entity.getClass(), "stepHeight");
            f.setAccessible(true);
            f.setFloat(entity, value);
        } catch (Exception ignored) {}
    }

    private Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        while (clazz != null) {
            try { return clazz.getDeclaredField(name); }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private float parseFloat(String s, float d) {
        try { return Float.parseFloat(s); } catch (Exception e) { return d; }
    }
}

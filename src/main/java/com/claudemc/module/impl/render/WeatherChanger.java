package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import java.lang.reflect.Field;

/**
 * Overrides client-side weather rendering. Purely visual — no packets sent.
 */
public class WeatherChanger extends Module {

    public WeatherChanger() {
        super("WeatherChanger", "Locks client-side weather to a chosen state (visual only)", Category.RENDER);
        addMode("Weather", "Clear", "Clear", "Rain", "Thunder");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null) return;
        ClientWorld world = client.world;
        String mode = getSetting("Weather");

        try {
            setFloat(world, "rainGradient",   "Clear".equals(mode) ? 0f : 1f);
            setFloat(world, "thunderGradient", "Thunder".equals(mode) ? 1f : 0f);
        } catch (Exception ignored) {}
    }

    private void setFloat(Object obj, String fieldName, float value) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.setFloat(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
    }
}

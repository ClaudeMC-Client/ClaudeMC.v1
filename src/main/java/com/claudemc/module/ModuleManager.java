package com.claudemc.module;

import com.claudemc.module.impl.AimAssistModule;
import com.claudemc.module.impl.EspModule;
import com.claudemc.module.impl.FlightModule;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        register(new FlightModule());
        register(new EspModule());
        register(new AimAssistModule());
    }

    private void register(Module m) {
        modules.add(m);
    }

    public void onTick(MinecraftClient client) {
        for (Module m : modules) {
            if (m.isEnabled()) m.onTick(client);
        }
    }

    public List<Module> getModules() {
        return modules;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module m : modules) {
            if (m.getClass() == clazz) return (T) m;
        }
        return null;
    }
}

package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;

public class AutoRespawn extends Module {

    public AutoRespawn() {
        super("AutoRespawn", "Automatically respawns on death", Category.MISC);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.currentScreen instanceof DeathScreen) {
            var nh = client.getNetworkHandler();
            if (nh != null) nh.sendPacket(
                new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
            client.setScreen(null);
        }
    }
}

package com.claudemc;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClaudeMCMod implements ModInitializer {
    public static final String MOD_ID = "claudemc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Common (server+client) init – nothing needed for a client-only mod
    }
}

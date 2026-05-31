package com.claudemc.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for server fingerprint accumulation (no Minecraft runtime). */
class ServerInfoTest {

    @BeforeEach
    void reset() {
        ServerInfo.INSTANCE.reset();
    }

    @Test
    void brandIsTrimmedAndDefaulted() {
        assertEquals("Unknown", ServerInfo.INSTANCE.getBrand());
        ServerInfo.INSTANCE.setBrand("  Paper  ");
        assertEquals("Paper", ServerInfo.INSTANCE.getBrand());
        ServerInfo.INSTANCE.setBrand(null);
        assertEquals("Unknown", ServerInfo.INSTANCE.getBrand());
    }

    @Test
    void systemChannelsAreFilteredOut() {
        ServerInfo.INSTANCE.addChannels(List.of(
            "minecraft:brand",
            "bungeecord:main",
            "worldedit:cui",
            "myplugin:data"));
        List<String> plugins = ServerInfo.INSTANCE.getPlugins();
        assertTrue(plugins.contains("worldedit"));
        assertTrue(plugins.contains("myplugin"));
        assertFalse(plugins.contains("minecraft"));
        assertFalse(plugins.contains("bungeecord"));
    }

    @Test
    void namespacesAreDeduplicated() {
        ServerInfo.INSTANCE.addChannels(List.of("worldedit:cui", "worldedit:other"));
        long count = ServerInfo.INSTANCE.getPlugins().stream()
            .filter("worldedit"::equals).count();
        assertEquals(1, count);
    }

    @Test
    void resetClearsState() {
        ServerInfo.INSTANCE.setBrand("Spigot");
        ServerInfo.INSTANCE.addChannels(List.of("foo:bar"));
        ServerInfo.INSTANCE.reset();
        assertEquals("Unknown", ServerInfo.INSTANCE.getBrand());
        assertTrue(ServerInfo.INSTANCE.getPlugins().isEmpty());
    }
}

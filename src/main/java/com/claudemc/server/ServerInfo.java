package com.claudemc.server;

import java.util.*;

/**
 * Holds all server fingerprint data discovered passively during a session.
 * Reset on each new connection.
 */
public class ServerInfo {

    public static final ServerInfo INSTANCE = new ServerInfo();

    private String brand = "Unknown";
    private final Set<String> pluginChannels = new LinkedHashSet<>();

    private ServerInfo() {}

    public void reset() {
        brand = "Unknown";
        pluginChannels.clear();
    }

    public void setBrand(String raw) {
        this.brand = raw == null ? "Unknown" : raw.trim();
    }

    public void addChannels(Iterable<String> channels) {
        for (String ch : channels) {
            String ns = namespace(ch);
            if (ns != null) pluginChannels.add(ns);
        }
    }

    public String getBrand() { return brand; }

    /** Returns the deduplicated list of detected plugin namespaces/names. */
    public List<String> getPlugins() { return new ArrayList<>(pluginChannels); }

    private static String namespace(String channel) {
        if (channel == null) return null;
        // Strip minecraft: and bungeecord: system channels
        if (channel.startsWith("minecraft:") || channel.equals("bungeecord:main")) return null;
        // Format: "pluginname:channel" — return the namespace portion
        int colon = channel.indexOf(':');
        if (colon > 0) return channel.substring(0, colon);
        return channel;
    }
}

package com.claudemc.server;

import java.util.*;

/**
 * Holds all server fingerprint data discovered passively during a session.
 * Reset on each new connection.
 */
public class ServerInfo {

    public static final ServerInfo INSTANCE = new ServerInfo();

    // Brand/channels are written from the network thread (packet handlers) and read
    // from the render thread (HUD / ServerInfoScreen), so access must be thread-safe.
    private volatile String brand = "Unknown";
    private final Set<String> pluginChannels =
        Collections.synchronizedSet(new LinkedHashSet<>());

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
    public List<String> getPlugins() {
        // Copy under the set's monitor to avoid ConcurrentModificationException
        // if a packet arrives mid-iteration.
        synchronized (pluginChannels) {
            return new ArrayList<>(pluginChannels);
        }
    }

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

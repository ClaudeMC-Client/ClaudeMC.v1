package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Marks entities as bots when they don't appear in the tab list,
 * have zero ping, or share a name format typical of bot farms.
 * Other combat modules can query isBot() before targeting.
 */
public class AntiBot extends Module {

    public static AntiBot INSTANCE;

    private final Set<UUID> botUuids = new HashSet<>();

    public AntiBot() {
        super("AntiBot", "Filters out bot entities from combat module targeting", Category.COMBAT);
        addBool("FilterTablist", true);
        addBool("FilterNoPing",  true);
        addBool("FilterInvalid", true);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (client.getNetworkHandler() == null) return;

        botUuids.clear();

        boolean filterTab     = Boolean.parseBoolean(getSetting("FilterTablist"));
        boolean filterNoPing  = Boolean.parseBoolean(getSetting("FilterNoPing"));
        boolean filterInvalid = Boolean.parseBoolean(getSetting("FilterInvalid"));

        Set<UUID> tabUuids = new HashSet<>();
        for (PlayerListEntry e : client.getNetworkHandler().getPlayerList()) {
            tabUuids.add(e.getProfile().id());
        }

        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof PlayerEntity p) || e == client.player) continue;
            UUID uid = p.getUuid();

            if (filterTab && !tabUuids.contains(uid)) { botUuids.add(uid); continue; }
            if (filterInvalid && isInvalidName(p.getName().getString())) { botUuids.add(uid); continue; }

            if (filterNoPing) {
                PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uid);
                if (entry != null && entry.getLatency() <= 0) { botUuids.add(uid); }
            }
        }
    }

    public boolean isBot(Entity e) {
        if (!isEnabled() || INSTANCE == null) return false;
        return botUuids.contains(e.getUuid());
    }

    private boolean isInvalidName(String name) {
        if (name.length() < 2 || name.length() > 16) return true;
        return name.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    @Override
    public void onDisable() {
        botUuids.clear();
    }
}

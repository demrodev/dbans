package me.demro.dbans.util;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LuckPermsHook {

    private final LuckPerms luckPerms;
    private final Map<UUID, CachedGroup> groupCache = new ConcurrentHashMap<>();
    private final long cacheTtlMillis = TimeUnit.SECONDS.toMillis(10);

    public LuckPermsHook(DBans plugin) {
        this.luckPerms = LuckPermsProvider.get();
        log.debug("LuckPermsHook initialized");
    }

    public String getPrimaryGroup(@NotNull Player player) {
        return getPrimaryGroup(player.getUniqueId());
    }

    public String getPrimaryGroup(UUID playerUuid) {
        CachedGroup cached = groupCache.get(playerUuid);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTtlMillis) {
            return cached.group;
        }
        User user = luckPerms.getUserManager().getUser(playerUuid);
        String group = (user == null) ? "default" : user.getPrimaryGroup();
        groupCache.put(playerUuid, new CachedGroup(group, System.currentTimeMillis()));
        return group;
    }

    public boolean hasPermission(UUID playerUuid, String permission) {
        User user = luckPerms.getUserManager().getUser(playerUuid);
        if (user == null) return false;
        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    public void invalidateCache(UUID playerUuid) {
        groupCache.remove(playerUuid);
    }

    public void clearCache() {
        groupCache.clear();
    }

    private record CachedGroup(String group, long timestamp) {

    }
}
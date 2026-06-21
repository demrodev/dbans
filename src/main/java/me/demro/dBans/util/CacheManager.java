package me.demro.dBans.util;

import me.demro.dBans.DBans;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Управляет кэшами активных наказаний и IP-банов.
 * Использует ConcurrentHashMap с временем жизни записей (TTL).
 */
public class CacheManager {
    private final Map<String, Punishment> activePunishmentCache = new ConcurrentHashMap<>();
    private volatile boolean ipBansCacheValid = false;
    private volatile List<String> cachedIpBans = null;
    private long lastIpBansRefresh = 0;
    private final long ipBansTtlMillis = TimeUnit.SECONDS.toMillis(30); // обновлять раз в 30 сек

    // Время жизни записей в кэше наказаний 5 сек
    private final long punishmentTtlMillis = TimeUnit.SECONDS.toMillis(5);
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    private final DBans plugin;

    public CacheManager(DBans plugin) {
        this.plugin = plugin;
    }

    public Punishment getCachedActivePunishment(UUID uuid, PunishmentType type, String serverName, String mode) {
        String key = buildKey(uuid, type, serverName, mode);
        Punishment cached = activePunishmentCache.get(key);
        Long timestamp = cacheTimestamps.get(key);
        if (cached != null && timestamp != null && (System.currentTimeMillis() - timestamp) < punishmentTtlMillis) {
            return cached;
        }
        if (cached != null) {
            activePunishmentCache.remove(key);
            cacheTimestamps.remove(key);
        }
        return null;
    }

    public void cacheActivePunishment(UUID uuid, PunishmentType type, String serverName, String mode, Punishment punishment) {
        String key = buildKey(uuid, type, serverName, mode);
        activePunishmentCache.put(key, punishment);
        cacheTimestamps.put(key, System.currentTimeMillis());
    }

    public void invalidateActivePunishment(UUID uuid, PunishmentType type, String serverName, String mode) {
        String key = buildKey(uuid, type, serverName, mode);
        activePunishmentCache.remove(key);
        cacheTimestamps.remove(key);
    }

    public void invalidateAllForPlayer(UUID uuid) {
        activePunishmentCache.keySet().removeIf(key -> key.startsWith(uuid.toString()));
        cacheTimestamps.keySet().removeIf(key -> key.startsWith(uuid.toString()));
    }

    // IP-баны
    public boolean isIpBannedCached(String ip) {
        refreshIpBansIfNeeded();
        if (cachedIpBans == null) return false;
        for (String ban : cachedIpBans) {
            if (ban.contains("*")) {
                if (matchesMask(ip, ban)) return true;
            } else if (ban.equals(ip)) return true;
        }
        return false;
    }

    public void invalidateIpBans() {
        ipBansCacheValid = false;
        cachedIpBans = null;
    }

    private void refreshIpBansIfNeeded() {
        if (ipBansCacheValid && (System.currentTimeMillis() - lastIpBansRefresh) < ipBansTtlMillis) {
            return;
        }
        cachedIpBans = plugin.getDatabase().getAllIpBans();
        ipBansCacheValid = true;
        lastIpBansRefresh = System.currentTimeMillis();
    }

    private boolean matchesMask(String ip, String mask) {
        String[] maskParts = mask.split("\\.");
        String[] ipParts = ip.split("\\.");
        for (int i = 0; i < 4; i++) {
            if (!maskParts[i].equals("*") && !maskParts[i].equals(ipParts[i])) {
                return false;
            }
        }
        return true;
    }

    private String buildKey(UUID uuid, PunishmentType type, String serverName, String mode) {
        if ("sync_static".equalsIgnoreCase(mode) && serverName != null) {
            return uuid.toString() + ":" + type.name() + ":" + serverName;
        } else {
            return uuid.toString() + ":" + type.name();
        }
    }
}
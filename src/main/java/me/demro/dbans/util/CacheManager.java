package me.demro.dbans.util;

import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CacheManager {

    private final DBans plugin;
    private final Map<String, Punishment> activePunishmentCache = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> muteCache = new ConcurrentHashMap<>(); // НОВЫЙ КЭШ ДЛЯ МУТА
    private final long ipBansTtlMillis = TimeUnit.SECONDS.toMillis(60);
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private volatile boolean ipBansCacheValid = false;
    private volatile List<String> cachedIpBans = null;
    private long lastIpBansRefresh = 0;
    private final long punishmentTtlMillis;

    public CacheManager(DBans plugin) {
        this.plugin = plugin;
        this.punishmentTtlMillis = TimeUnit.SECONDS.toMillis(
                plugin.getMode().equalsIgnoreCase("sync") ? 60 : 30
        );
    }

    // ===== КЭШ АКТИВНЫХ НАКАЗАНИЙ (существующий) =====
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

    public void cacheActivePunishment(UUID uuid, PunishmentType type, String serverName, String mode,
                                      Punishment punishment
    ) {
        String key = buildKey(uuid, type, serverName, mode);
        activePunishmentCache.put(key, punishment);
        cacheTimestamps.put(key, System.currentTimeMillis());
        // Если это мут – обновляем кэш мута
        if (type == PunishmentType.MUTE) {
            updateMuteCache(uuid, punishment);
        }
    }

    public void invalidateActivePunishment(UUID uuid, PunishmentType type, String serverName, String mode) {
        String key = buildKey(uuid, type, serverName, mode);
        activePunishmentCache.remove(key);
        cacheTimestamps.remove(key);
        // Если это мут – очищаем кэш мута
        if (type == PunishmentType.MUTE) {
            muteCache.remove(uuid);
        }
    }

    public void invalidateAllForPlayer(UUID uuid) {
        activePunishmentCache.keySet().removeIf(key -> key.startsWith(uuid.toString()));
        cacheTimestamps.keySet().removeIf(key -> key.startsWith(uuid.toString()));
        muteCache.remove(uuid); // Очищаем кэш мута
    }

    // ===== КЭШ МУТА =====
    public boolean isMuted(UUID playerUuid) {
        Punishment mute = muteCache.get(playerUuid);
        if (mute != null && mute.isActive() && !mute.isExpired()) {
            return true;
        }
        if (mute != null) {
            muteCache.remove(playerUuid);
        }
        return false;
    }

    public Punishment getMute(UUID playerUuid) {
        Punishment mute = muteCache.get(playerUuid);
        if (mute != null && mute.isActive() && !mute.isExpired()) {
            return mute;
        }
        if (mute != null) {
            muteCache.remove(playerUuid);
        }
        return null;
    }

    public void updateMuteCache(UUID playerUuid, Punishment mute) {
        if (mute != null && mute.isActive() && !mute.isExpired()) {
            muteCache.put(playerUuid, mute);
        } else {
            muteCache.remove(playerUuid);
        }
    }

    public void invalidateMuteCache(UUID playerUuid) {
        muteCache.remove(playerUuid);
    }

    public void clearMuteCache() {
        muteCache.clear();
    }

    // ===== IP-БАНЫ =====
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
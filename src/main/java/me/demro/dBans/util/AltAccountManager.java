package me.demro.dBans.util;

import me.demro.dBans.DBans;
import me.demro.dBans.model.PlayerInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AltAccountManager {
    private final DBans plugin;
    private final Map<String, List<String>> altCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTime = new ConcurrentHashMap<>();
    private final long cacheTtl = TimeUnit.MINUTES.toMillis(5); // 5 минут

    public AltAccountManager(DBans plugin) {
        this.plugin = plugin;
    }

    public List<String> findAltAccounts(String playerName) {
        String lowerName = playerName.toLowerCase();
        // Проверка кэша
        Long time = cacheTime.get(lowerName);
        if (time != null && System.currentTimeMillis() - time < cacheTtl) {
            return altCache.getOrDefault(lowerName, Collections.emptyList());
        }

        List<String> alts = new ArrayList<>();
        PlayerInfo player = plugin.getDatabase().getPlayerByName(playerName);
        if (player == null) return alts;

        String ip = player.getIp();
        if (ip == null || ip.isEmpty()) return alts;

        for (PlayerInfo info : plugin.getDatabase().getAllPlayers()) {
            if (info.getIp().equals(ip) && !info.getName().equalsIgnoreCase(playerName)) {
                alts.add(info.getName());
            }
        }

        altCache.put(lowerName, alts);
        cacheTime.put(lowerName, System.currentTimeMillis());
        return alts;
    }

    public void invalidateCache(String playerName) {
        if (playerName != null) {
            altCache.remove(playerName.toLowerCase());
            cacheTime.remove(playerName.toLowerCase());
        } else {
            altCache.clear();
            cacheTime.clear();
        }
    }

    public void onPlayerLogin(String playerName, String newIp) {
        invalidateCache(null);
    }
}
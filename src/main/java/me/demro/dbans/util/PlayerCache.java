package me.demro.dbans.util;

import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш для быстрого получения OfflinePlayer по имени или UUID.
 * Обновляется при входе игрока и при необходимости.
 */
@Slf4j
public class PlayerCache {

    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();
    private final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();

    /**
     * Получить OfflinePlayer по имени. Сначала проверяет кэш, если нет - запрашивает у Bukkit и кэширует.
     */
    public OfflinePlayer getOfflinePlayer(String name) {
        UUID uuid = nameToUuid.get(name);
        if (uuid != null) {
            return Bukkit.getOfflinePlayer(uuid);
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (player.hasPlayedBefore() || player.isOnline()) {
            nameToUuid.put(name, player.getUniqueId());
            uuidToName.put(player.getUniqueId(), name);
            log.debug("Cached player by name: {}", name);
        }
        return player;
    }

    /**
     * Получить OfflinePlayer по UUID.
     */
    public OfflinePlayer getOfflinePlayer(UUID uuid) {
        String name = uuidToName.get(uuid);
        if (name != null) {
            return Bukkit.getOfflinePlayer(name);
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player.hasPlayedBefore() || player.isOnline()) {
            uuidToName.put(uuid, player.getName());
            nameToUuid.put(player.getName(), uuid);
            log.debug("Cached player by UUID: {}", uuid);
        }
        return player;
    }

    /**
     * Обновить кэш для игрока (при входе).
     */
    public void update(OfflinePlayer player) {
        if (player != null) {
            nameToUuid.put(player.getName(), player.getUniqueId());
            uuidToName.put(player.getUniqueId(), player.getName());
            log.debug("Updated cache for player: {}", player.getName());
        }
    }

    /**
     * Очистить кэш.
     */
    public void clear() {
        nameToUuid.clear();
        uuidToName.clear();
        log.debug("Player cache cleared");
    }

    /**
     * Инвалидировать запись по имени.
     */
    public void invalidate(String name) {
        UUID uuid = nameToUuid.remove(name);
        if (uuid != null) {
            uuidToName.remove(uuid);
            log.debug("Invalidated cache for name: {}", name);
        }
    }

    /**
     * Инвалидировать запись по UUID.
     */
    public void invalidate(UUID uuid) {
        String name = uuidToName.remove(uuid);
        if (name != null) {
            nameToUuid.remove(name);
            log.debug("Invalidated cache for UUID: {}", uuid);
        }
    }
}
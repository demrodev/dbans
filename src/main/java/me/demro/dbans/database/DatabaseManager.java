package me.demro.dbans.database;

import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.PlayerInfo;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.model.Warning;
import me.demro.dbans.util.CacheManager;
import org.bukkit.Location;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface DatabaseManager {
    void init() throws Exception;
    void close();
    void setCacheManager(CacheManager cacheManager);

    // ===== ОСНОВНЫЕ МЕТОДЫ ДЛЯ НАКАЗАНИЙ =====
    void savePunishment(Punishment punishment);
    void updatePunishment(Punishment punishment);
    void updatePunishmentEndTime(String id, long newEndTime);
    Punishment getActivePunishment(UUID playerUuid, PunishmentType type, String currentServer, String mode);
    List<Punishment> getPunishmentHistory(UUID playerUuid, boolean includeInactive);
    List<Punishment> getActivePunishments(UUID playerUuid, String currentServer, String mode);
    List<Punishment> getActivePunishmentsIncludingJail(UUID playerUuid, String currentServer, String mode);
    List<Punishment> getAllPunishments();
    List<Punishment> getAllPunishmentsIncludingJail();
    List<Punishment> getPunishmentHistoryIncludingJail(UUID playerUuid);
    Punishment getPunishmentById(String id);
    void deletePunishment(String id);
    void updatePunishmentReason(String id, String newReason);
    void pardonPunishment(String id, String pardonedBy, String pardonReason);

    // ===== IP-БАНЫ =====
    void saveIpBan(String ip, UUID playerUuid, String playerName, String issuerName, String reason, long startTime, Long endTime);
    boolean isIpBanned(String ip);
    void removeIpBan(String ip);
    void removeIpBanByPlayer(UUID playerUuid);
    String getIpByPlayerName(String playerName);
    List<String> getAllIpBans();

    // ===== ИГРОКИ =====
    void savePlayer(PlayerInfo player);
    PlayerInfo getPlayer(UUID uuid);
    String getPlayerIp(UUID uuid);
    PlayerInfo getPlayerByName(String name);
    List<PlayerInfo> getAllPlayers();

    // ===== JAIL =====
    void saveJail(JailPunishment jail);
    JailPunishment getActiveJail(UUID playerUuid);
    void removeJail(String id);
    void updateJail(JailPunishment jail);
    boolean isJailed(UUID playerUuid);
    List<JailPunishment> getAllActiveJails();
    List<JailPunishment> getAllJailsForPlayer(UUID playerUuid);
    List<JailPunishment> getExpiredActiveJails();
    void setPendingUnjail(UUID playerId, boolean pending);
    boolean hasPendingUnjail(UUID playerId);
    void clearPendingUnjail(UUID playerId);
    JailPunishment getJailById(String id);
    List<JailPunishment> getAllJailsForAllPlayers(); // для очистки платформ

    // ===== WARNINGS =====
    void saveWarning(Warning warning);
    Warning getActiveWarning(UUID playerUuid);
    Warning getWarningById(String id);
    List<Warning> getActiveWarnings(UUID playerUuid);
    List<Warning> getAllWarningsForPlayer(UUID playerUuid);
    List<Warning> getAllWarnings();
    void updateWarning(Warning warning);
    void deleteWarning(String id);
    void pardonWarning(String id, String pardonedBy, String pardonReason);
    void removeWarning(String id);
    boolean isWarned(UUID playerUuid);

    // ===== УДАЛЕНИЕ ВСЕГО =====
    void deleteAllPunishments();
    void deleteAllJails();
    void deleteAllWarnings();

    // ===== СТАТИСТИКА ДЛЯ PLACEHOLDERAPI =====
    int getTotalPunishmentsCount();
    int getPunishmentsCountByType(String type);
    int getPunishmentsCountByTypeAndPlayer(String type, String playerName);
    int getTotalPunishmentsCountByPlayer(String playerName);
    int getPunishmentsIssuedByPlayer(String issuerName);
    int getPunishmentsIssuedByPlayerAndType(String issuerName, String type);

    // ===== ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ =====
    List<Punishment> getAllActivePunishmentsByType(PunishmentType type);

    // ===== МЕТОДЫ ДЛЯ УВЕДОМЛЕНИЙ (ЧЕРЕЗ БД) =====
    void addNotification(UUID playerUuid, String messageKey, Map<String, String> placeholders);
    List<Map<String, String>> getAndClearNotifications(UUID playerUuid); // <-- ЭТОТ МЕТОД БЫЛ ОТСУТСТВОВАЛ
    void clearNotifications(UUID playerUuid);
    long getLastSeen(UUID uuid); // <-- ЭТОТ МЕТОД БЫЛ ОТСУТСТВОВАЛ

    // ===== МЕТОДЫ ДЛЯ ОПТИМИЗАЦИИ =====
    String getPlayerIpByName(String playerName);
    List<String> getPlayerNamesByIp(String playerName);
    Map<UUID, List<Punishment>> getActivePunishmentsForPlayers(Set<UUID> playerUuids, String currentServer, String mode);
}
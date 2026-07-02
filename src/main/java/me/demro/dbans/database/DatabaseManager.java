package me.demro.dbans.database;

import me.demro.dbans.model.*;
import me.demro.dbans.util.CacheManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface DatabaseManager {

    void init() throws Exception;

    void close();

    void setCacheManager(CacheManager cacheManager);

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

    void saveIpBan(String ip, UUID playerUuid, String playerName, String issuerName, String reason, long startTime,
                   Long endTime
    );

    boolean isIpBanned(String ip);

    void removeIpBan(String ip);

    void removeIpBanByPlayer(UUID playerUuid);

    String getIpByPlayerName(String playerName);

    List<String> getAllIpBans();

    void savePlayer(PlayerInfo player);

    PlayerInfo getPlayer(UUID uuid);

    String getPlayerIp(UUID uuid);

    PlayerInfo getPlayerByName(String name);

    List<PlayerInfo> getAllPlayers();

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

    void deleteAllPunishments();

    void deleteAllJails();

    void deleteAllWarnings();

    int getTotalPunishmentsCount();

    int getPunishmentsCountByType(String type);

    int getPunishmentsCountByTypeAndPlayer(String type, String playerName);

    int getTotalPunishmentsCountByPlayer(String playerName);

    int getPunishmentsIssuedByPlayer(String issuerName);

    int getPunishmentsIssuedByPlayerAndType(String issuerName, String type);

    List<Punishment> getAllActivePunishmentsByType(PunishmentType type);

    void addNotification(UUID playerUuid, String messageKey, Map<String, String> placeholders);

    List<Map<String, String>> getAndClearNotifications(UUID playerUuid);

    void clearNotifications(UUID playerUuid);

    long getLastSeen(UUID uuid);

    String getPlayerIpByName(String playerName);

    List<String> getPlayerNamesByIp(String playerName);

    Map<UUID, List<Punishment>> getActivePunishmentsForPlayers(Set<UUID> playerUuids, String currentServer,
                                                               String mode
    );
}
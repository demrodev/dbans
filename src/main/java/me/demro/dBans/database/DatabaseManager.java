package me.demro.dBans.database;

import me.demro.dBans.model.JailPunishment;
import me.demro.dBans.model.PlayerInfo;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.model.Warning;
import me.demro.dBans.util.CacheManager;
import org.bukkit.Location;
import java.util.List;
import java.util.UUID;

public interface DatabaseManager {
    void init() throws Exception;
    void close();
    void setCacheManager(CacheManager cacheManager); // НОВЫЙ МЕТОД

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
    void saveIpBan(String ip, UUID playerUuid, String playerName, String issuerName, String reason, long startTime, Long endTime);
    boolean isIpBanned(String ip);
    void removeIpBan(String ip);
    void removeIpBanByPlayer(UUID playerUuid);
    String getIpByPlayerName(String playerName);
    List<Punishment> getAllActivePunishmentsByType(PunishmentType type);
    void savePlayer(PlayerInfo player);
    PlayerInfo getPlayer(UUID uuid);
    String getPlayerIp(UUID uuid);
    List<String> getAllIpBans();
    PlayerInfo getPlayerByName(String name);
    List<PlayerInfo> getAllPlayers();

    // Jail methods
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

    // Warning methods
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

    //Remove all
    void deleteAllPunishments();
    void deleteAllJails();
    void deleteAllWarnings();

    //PAPI
    int getTotalPunishmentsCount();
    int getPunishmentsCountByType(String type);
    int getPunishmentsCountByTypeAndPlayer(String type, String playerName);
    int getTotalPunishmentsCountByPlayer(String playerName);
    int getPunishmentsIssuedByPlayer(String issuerName);
    int getPunishmentsIssuedByPlayerAndType(String issuerName, String type);
}
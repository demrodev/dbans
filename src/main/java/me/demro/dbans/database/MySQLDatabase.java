package me.demro.dbans.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.demro.dbans.DBans;
import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.PlayerInfo;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.model.Warning;
import me.demro.dbans.util.CacheManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.*;
import java.util.*;

public class MySQLDatabase implements DatabaseManager {
    private final DBans plugin;
    private HikariDataSource dataSource;
    private CacheManager cache;

    public MySQLDatabase(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setCacheManager(CacheManager cacheManager) {
        this.cache = cacheManager;
    }

    @Override
    public void init() throws SQLException {
        String host = plugin.getConfig().getString("database.mysql.host");
        int port = plugin.getConfig().getInt("database.mysql.port");
        String database = plugin.getConfig().getString("database.mysql.database");
        String user = plugin.getConfig().getString("database.mysql.user");
        String password = plugin.getConfig().getString("database.mysql.password");
        int poolSize = plugin.getConfig().getInt("database.mysql.poolSize", 10);

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(poolSize);
        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS punishments (" +
                    "id VARCHAR(10) PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "issuer_uuid VARCHAR(36) NOT NULL," +
                    "issuer_name VARCHAR(16) NOT NULL," +
                    "type VARCHAR(10) NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "start_time BIGINT NOT NULL," +
                    "end_time BIGINT," +
                    "active BOOLEAN DEFAULT TRUE," +
                    "pardoned_by VARCHAR(16)," +
                    "pardoned_at BIGINT," +
                    "pardon_reason TEXT," +
                    "server_name VARCHAR(64) DEFAULT 'unknown')");
            try {
                st.execute("CREATE INDEX idx_player_active ON punishments(player_uuid, type, active)");
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate key name")) throw e;
            }
            st.execute("CREATE TABLE IF NOT EXISTS ip_bans (" +
                    "ip VARCHAR(45) PRIMARY KEY," +
                    "player_uuid VARCHAR(36)," +
                    "player_name VARCHAR(16)," +
                    "issuer_name VARCHAR(16)," +
                    "reason TEXT," +
                    "start_time BIGINT," +
                    "end_time BIGINT)");
            st.execute("CREATE TABLE IF NOT EXISTS players (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "name VARCHAR(16) NOT NULL," +
                    "ip VARCHAR(45) NOT NULL," +
                    "last_seen BIGINT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS jail_punishments (" +
                    "id VARCHAR(10) PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "issuer_uuid VARCHAR(36) NOT NULL," +
                    "issuer_name VARCHAR(16) NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "start_time BIGINT NOT NULL," +
                    "end_time BIGINT," +
                    "active BOOLEAN DEFAULT TRUE," +
                    "server_name VARCHAR(64) DEFAULT 'unknown'," +
                    "pardoned_by VARCHAR(16)," +
                    "pardoned_at BIGINT," +
                    "previous_location VARCHAR(255))");
            try {
                st.execute("ALTER TABLE jail_punishments ADD COLUMN jail_location VARCHAR(255)");
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate column")) throw e;
            }
            try {
                st.execute("ALTER TABLE jail_punishments ADD COLUMN pending_unjail BOOLEAN DEFAULT FALSE");
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate column")) throw e;
            }
            st.execute("CREATE TABLE IF NOT EXISTS warnings (" +
                    "id VARCHAR(10) PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "issuer_uuid VARCHAR(36) NOT NULL," +
                    "issuer_name VARCHAR(16) NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "start_time BIGINT NOT NULL," +
                    "end_time BIGINT," +
                    "active BOOLEAN DEFAULT TRUE," +
                    "server_name VARCHAR(64) DEFAULT 'unknown'," +
                    "pardoned_by VARCHAR(16)," +
                    "pardoned_at BIGINT)");
            st.execute("CREATE TABLE IF NOT EXISTS player_notifications (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "message_key VARCHAR(64) NOT NULL," +
                    "placeholders VARCHAR(255)," +
                    "created BIGINT NOT NULL)");
            try {
                st.execute("CREATE INDEX idx_notif_uuid ON player_notifications(uuid)");
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate key name")) throw e;
            }
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    // ==================== ОСНОВНЫЕ МЕТОДЫ ДЛЯ НАКАЗАНИЙ ====================

    @Override
    public void savePunishment(Punishment punishment) {
        String sql = "INSERT INTO punishments (id, player_uuid, player_name, issuer_uuid, issuer_name, type, reason, start_time, end_time, active, server_name) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, punishment.getId());
                ps.setString(2, punishment.getPlayerUuid().toString());
                ps.setString(3, punishment.getPlayerName());
                ps.setString(4, punishment.getIssuerUuid().toString());
                ps.setString(5, punishment.getIssuerName());
                ps.setString(6, punishment.getType().name());
                ps.setString(7, punishment.getReason());
                ps.setLong(8, punishment.getStartTime());
                if (punishment.getEndTime() == null) ps.setNull(9, Types.BIGINT);
                else ps.setLong(9, punishment.getEndTime());
                ps.setBoolean(10, punishment.isActive());
                ps.setString(11, punishment.getServerName());
                ps.executeUpdate();
                if (cache != null) {
                    cache.invalidateActivePunishment(punishment.getPlayerUuid(), punishment.getType(), punishment.getServerName(), plugin.getMode());
                    cache.invalidateAllForPlayer(punishment.getPlayerUuid());
                }
                return;
            } catch (SQLException e) {
                if (e.getErrorCode() == 1062 || e.getMessage().contains("Duplicate entry")) {
                    if (attempt == maxRetries - 1) {
                        plugin.getLogger().severe("Failed to generate unique punishment ID");
                        throw new RuntimeException("Cannot save punishment", e);
                    }
                    punishment.setId(Punishment.generateId());
                } else {
                    plugin.getLogger().severe("Failed to save punishment: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    @Override
    public void updatePunishment(Punishment punishment) {
        String sql = "UPDATE punishments SET active=?, pardoned_by=?, pardoned_at=?, pardon_reason=? WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, punishment.isActive());
            ps.setString(2, punishment.getPardonedBy());
            if (punishment.getPardonedAt() == null) ps.setNull(3, Types.BIGINT);
            else ps.setLong(3, punishment.getPardonedAt());
            ps.setString(4, punishment.getPardonReason());
            ps.setString(5, punishment.getId());
            ps.executeUpdate();
            if (cache != null) {
                cache.invalidateActivePunishment(punishment.getPlayerUuid(), punishment.getType(), punishment.getServerName(), plugin.getMode());
                cache.invalidateAllForPlayer(punishment.getPlayerUuid());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update punishment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void updatePunishmentEndTime(String id, long newEndTime) {
        String sql = "UPDATE punishments SET end_time=? WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, newEndTime);
            ps.setString(2, id);
            ps.executeUpdate();
            if (cache != null) {
                Punishment p = getPunishmentById(id);
                if (p != null) {
                    cache.invalidateActivePunishment(p.getPlayerUuid(), p.getType(), p.getServerName(), plugin.getMode());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update punishment end time: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public Punishment getActivePunishment(UUID playerUuid, PunishmentType type, String currentServer, String mode) {
        if (cache != null) {
            Punishment cached = cache.getCachedActivePunishment(playerUuid, type, currentServer, mode);
            if (cached != null) return cached;
        }
        String sql;
        if ("sync_static".equalsIgnoreCase(mode)) {
            sql = "SELECT * FROM punishments WHERE player_uuid=? AND type=? AND active=1 AND server_name=? ORDER BY start_time DESC LIMIT 1";
        } else {
            sql = "SELECT * FROM punishments WHERE player_uuid=? AND type=? AND active=1 ORDER BY start_time DESC LIMIT 1";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, type.name());
            if ("sync_static".equalsIgnoreCase(mode)) {
                ps.setString(3, currentServer);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Punishment result = mapResultSet(rs);
                if (cache != null) {
                    cache.cacheActivePunishment(playerUuid, type, currentServer, mode, result);
                }
                return result;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get active punishment: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Punishment> getPunishmentHistory(UUID playerUuid, boolean includeInactive) {
        List<Punishment> list = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE player_uuid=? ORDER BY start_time DESC";
        if (!includeInactive) sql = "SELECT * FROM punishments WHERE player_uuid=? AND active=1 ORDER BY start_time DESC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get history: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Punishment> getActivePunishments(UUID playerUuid, String currentServer, String mode) {
        List<Punishment> list = new ArrayList<>();
        String sql;
        if ("sync_static".equalsIgnoreCase(mode)) {
            sql = "SELECT * FROM punishments WHERE player_uuid=? AND active=1 AND server_name=? ORDER BY start_time DESC";
        } else {
            sql = "SELECT * FROM punishments WHERE player_uuid=? AND active=1 ORDER BY start_time DESC";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            if ("sync_static".equalsIgnoreCase(mode)) {
                ps.setString(2, currentServer);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Punishment p = mapResultSet(rs);
                if (!p.isExpired()) list.add(p);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get active punishments: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Punishment> getAllPunishments() {
        List<Punishment> list = new ArrayList<>();
        String sql = "SELECT * FROM punishments ORDER BY start_time DESC";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all punishments: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Punishment getPunishmentById(String id) {
        String sql = "SELECT * FROM punishments WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get punishment by ID: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void deletePunishment(String id) {
        Punishment p = getPunishmentById(id);
        String sql = "DELETE FROM punishments WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
            if (cache != null && p != null) {
                cache.invalidateActivePunishment(p.getPlayerUuid(), p.getType(), p.getServerName(), plugin.getMode());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete punishment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void updatePunishmentReason(String id, String newReason) {
        String sql = "UPDATE punishments SET reason=? WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newReason);
            ps.setString(2, id);
            ps.executeUpdate();
            if (cache != null) {
                Punishment p = getPunishmentById(id);
                if (p != null) {
                    cache.invalidateActivePunishment(p.getPlayerUuid(), p.getType(), p.getServerName(), plugin.getMode());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update reason: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void pardonPunishment(String id, String pardonedBy, String pardonReason) {
        Punishment p = getPunishmentById(id);
        if (p == null) return;
        p.setActive(false);
        p.setPardonedBy(pardonedBy);
        p.setPardonedAt(System.currentTimeMillis());
        p.setPardonReason(pardonReason);
        updatePunishment(p);
    }

    // ==================== IP-БАНЫ ====================

    @Override
    public void saveIpBan(String ip, UUID playerUuid, String playerName, String issuerName, String reason, long startTime, Long endTime) {
        String sql = "INSERT INTO ip_bans (ip, player_uuid, player_name, issuer_name, reason, start_time, end_time) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setString(2, playerUuid == null ? null : playerUuid.toString());
            ps.setString(3, playerName);
            ps.setString(4, issuerName);
            ps.setString(5, reason);
            ps.setLong(6, startTime);
            if (endTime == null) ps.setNull(7, Types.BIGINT);
            else ps.setLong(7, endTime);
            ps.executeUpdate();
            if (cache != null) cache.invalidateIpBans();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save IP ban: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean isIpBanned(String ip) {
        if (cache != null) return cache.isIpBannedCached(ip);
        List<String> allBans = getAllIpBans();
        for (String ban : allBans) {
            if (ban.contains("*")) {
                if (matchesMask(ip, ban)) return true;
            } else {
                if (ban.equals(ip)) return true;
            }
        }
        return false;
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

    @Override
    public void removeIpBan(String ip) {
        String sql = "DELETE FROM ip_bans WHERE ip=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.executeUpdate();
            if (cache != null) cache.invalidateIpBans();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove IP ban: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void removeIpBanByPlayer(UUID playerUuid) {
        String sql = "DELETE FROM ip_bans WHERE player_uuid=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
            if (cache != null) cache.invalidateIpBans();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove IP ban by player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public String getIpByPlayerName(String playerName) {
        String sql = "SELECT ip FROM ip_bans WHERE player_name=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("ip");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get IP by player name: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<String> getAllIpBans() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT ip FROM ip_bans WHERE end_time IS NULL OR end_time > ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString("ip"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all IP bans: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // ==================== ИГРОКИ ====================

    @Override
    public void savePlayer(PlayerInfo player) {
        String sql = "INSERT INTO players (uuid, name, ip, last_seen) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name), ip=VALUES(ip), last_seen=VALUES(last_seen)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.getUuid().toString());
            ps.setString(2, player.getName());
            ps.setString(3, player.getIp());
            ps.setLong(4, player.getLastSeen());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public PlayerInfo getPlayer(UUID uuid) {
        String sql = "SELECT * FROM players WHERE uuid=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerInfo(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        rs.getString("ip"),
                        rs.getLong("last_seen")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getPlayerIp(UUID uuid) {
        PlayerInfo player = getPlayer(uuid);
        return player != null ? player.getIp() : null;
    }

    @Override
    public PlayerInfo getPlayerByName(String name) {
        String sql = "SELECT * FROM players WHERE name=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerInfo(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        rs.getString("ip"),
                        rs.getLong("last_seen")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player by name: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<PlayerInfo> getAllPlayers() {
        List<PlayerInfo> list = new ArrayList<>();
        String sql = "SELECT * FROM players";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new PlayerInfo(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        rs.getString("ip"),
                        rs.getLong("last_seen")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all players: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // ==================== JAIL ====================

    @Override
    public void saveJail(JailPunishment jail) {
        String sql = "INSERT INTO jail_punishments (id, player_uuid, player_name, issuer_uuid, issuer_name, reason, start_time, end_time, active, server_name, previous_location, jail_location, pending_unjail) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jail.getId());
            ps.setString(2, jail.getPlayerUuid().toString());
            ps.setString(3, jail.getPlayerName());
            ps.setString(4, jail.getIssuerUuid().toString());
            ps.setString(5, jail.getIssuerName());
            ps.setString(6, jail.getReason());
            ps.setLong(7, jail.getStartTime());
            if (jail.getEndTime() == null) ps.setNull(8, Types.BIGINT);
            else ps.setLong(8, jail.getEndTime());
            ps.setBoolean(9, jail.isActive());
            ps.setString(10, jail.getServerName());
            String prevLoc = null;
            if (jail.getPreviousLocation() != null && jail.getPreviousLocation().getWorld() != null) {
                Location loc = jail.getPreviousLocation();
                prevLoc = loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
            }
            ps.setString(11, prevLoc);
            String jailLocStr = null;
            if (jail.getJailLocation() != null && jail.getJailLocation().getWorld() != null) {
                Location loc = jail.getJailLocation();
                jailLocStr = loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
            }
            ps.setString(12, jailLocStr);
            ps.setBoolean(13, false);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save jail: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public JailPunishment getActiveJail(UUID playerUuid) {
        String sql = "SELECT * FROM jail_punishments WHERE player_uuid=? AND active=1 ORDER BY start_time DESC LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapJailResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get active jail: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void removeJail(String id) {
        String sql = "DELETE FROM jail_punishments WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove jail: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void updateJail(JailPunishment jail) {
        String sql = "UPDATE jail_punishments SET active=?, pardoned_by=?, pardoned_at=?, pending_unjail=? WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, jail.isActive());
            ps.setString(2, jail.getPardonedBy());
            if (jail.getPardonedAt() == null) ps.setNull(3, Types.BIGINT);
            else ps.setLong(3, jail.getPardonedAt());
            ps.setBoolean(4, false);
            ps.setString(5, jail.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update jail: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean isJailed(UUID playerUuid) {
        JailPunishment j = getActiveJail(playerUuid);
        return j != null && j.isActive() && (j.getEndTime() == null || j.getEndTime() > System.currentTimeMillis());
    }

    @Override
    public List<JailPunishment> getAllActiveJails() {
        List<JailPunishment> list = new ArrayList<>();
        String sql = "SELECT * FROM jail_punishments WHERE active=1";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapJailResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all active jails: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<JailPunishment> getAllJailsForPlayer(UUID playerUuid) {
        List<JailPunishment> list = new ArrayList<>();
        String sql = "SELECT * FROM jail_punishments WHERE player_uuid=? ORDER BY start_time DESC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapJailResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get jails for player: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<JailPunishment> getExpiredActiveJails() {
        List<JailPunishment> list = new ArrayList<>();
        String sql = "SELECT * FROM jail_punishments WHERE active=1 AND end_time IS NOT NULL AND end_time <= ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapJailResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get expired active jails: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void setPendingUnjail(UUID playerId, boolean pending) {
        String sql = "UPDATE jail_punishments SET pending_unjail = ? WHERE player_uuid = ? AND active = 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, pending);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set pending unjail: " + e.getMessage());
        }
    }

    @Override
    public boolean hasPendingUnjail(UUID playerId) {
        String sql = "SELECT pending_unjail FROM jail_punishments WHERE player_uuid = ? AND active = 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBoolean("pending_unjail");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check pending unjail: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void clearPendingUnjail(UUID playerId) {
        setPendingUnjail(playerId, false);
    }

    @Override
    public JailPunishment getJailById(String id) {
        String sql = "SELECT * FROM jail_punishments WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapJailResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get jail by ID: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<JailPunishment> getAllJailsForAllPlayers() {
        List<JailPunishment> list = new ArrayList<>();
        String sql = "SELECT * FROM jail_punishments";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapJailResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all jails: " + e.getMessage());
        }
        return list;
    }

    // ==================== WARNINGS ====================

    @Override
    public void saveWarning(Warning warning) {
        String sql = "INSERT INTO warnings (id, player_uuid, player_name, issuer_uuid, issuer_name, reason, start_time, end_time, active, server_name) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, warning.getId());
            ps.setString(2, warning.getPlayerUuid().toString());
            ps.setString(3, warning.getPlayerName());
            ps.setString(4, warning.getIssuerUuid().toString());
            ps.setString(5, warning.getIssuerName());
            ps.setString(6, warning.getReason());
            ps.setLong(7, warning.getStartTime());
            if (warning.getEndTime() == null) ps.setNull(8, Types.BIGINT);
            else ps.setLong(8, warning.getEndTime());
            ps.setBoolean(9, warning.isActive());
            ps.setString(10, warning.getServerName());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save warning: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public Warning getActiveWarning(UUID playerUuid) {
        String sql = "SELECT * FROM warnings WHERE player_uuid=? AND active=1 ORDER BY start_time DESC LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapWarningResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get active warning: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Warning getWarningById(String id) {
        String sql = "SELECT * FROM warnings WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapWarningResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get warning by ID: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Warning> getActiveWarnings(UUID playerUuid) {
        List<Warning> list = new ArrayList<>();
        String sql = "SELECT * FROM warnings WHERE player_uuid=? AND active=1 ORDER BY start_time DESC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapWarningResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get active warnings: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Warning> getAllWarningsForPlayer(UUID playerUuid) {
        List<Warning> list = new ArrayList<>();
        String sql = "SELECT * FROM warnings WHERE player_uuid=? ORDER BY start_time DESC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapWarningResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get warnings for player: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Warning> getAllWarnings() {
        List<Warning> list = new ArrayList<>();
        String sql = "SELECT * FROM warnings ORDER BY start_time DESC";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapWarningResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all warnings: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void updateWarning(Warning warning) {
        String sql = "UPDATE warnings SET active=?, pardoned_by=?, pardoned_at=? WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, warning.isActive());
            ps.setString(2, warning.getPardonedBy());
            if (warning.getPardonedAt() == null) ps.setNull(3, Types.BIGINT);
            else ps.setLong(3, warning.getPardonedAt());
            ps.setString(4, warning.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update warning: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void deleteWarning(String id) {
        String sql = "DELETE FROM warnings WHERE id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete warning: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void pardonWarning(String id, String pardonedBy, String pardonReason) {
        Warning w = getWarningById(id);
        if (w != null) {
            w.setActive(false);
            w.setPardonedBy(pardonedBy);
            w.setPardonedAt(System.currentTimeMillis());
            updateWarning(w);
        }
    }

    @Override
    public void removeWarning(String id) {
        deleteWarning(id);
    }

    @Override
    public boolean isWarned(UUID playerUuid) {
        return !getActiveWarnings(playerUuid).isEmpty();
    }

    // ==================== УДАЛЕНИЕ ВСЕГО ====================

    @Override
    public void deleteAllPunishments() {
        String sql = "DELETE FROM punishments";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete all punishments: " + e.getMessage());
        }
    }

    @Override
    public void deleteAllJails() {
        String sql = "DELETE FROM jail_punishments";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete all jails: " + e.getMessage());
        }
    }

    @Override
    public void deleteAllWarnings() {
        String sql = "DELETE FROM warnings";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete all warnings: " + e.getMessage());
        }
    }

    // ==================== СТАТИСТИКА ДЛЯ PLACEHOLDERAPI ====================

    @Override
    public int getTotalPunishmentsCount() {
        String sql = "SELECT COUNT(*) FROM (SELECT 1 FROM punishments UNION ALL SELECT 1 FROM jail_punishments UNION ALL SELECT 1 FROM warnings) AS t";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get total punishments count: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getPunishmentsCountByType(String type) {
        String sql;
        if (type.equals("JAIL")) {
            sql = "SELECT COUNT(*) FROM jail_punishments";
        } else if (type.equals("WARNING")) {
            sql = "SELECT COUNT(*) FROM warnings";
        } else {
            sql = "SELECT COUNT(*) FROM punishments WHERE type = ?";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!type.equals("JAIL") && !type.equals("WARNING")) {
                ps.setString(1, type);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get punishments count by type: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getPunishmentsCountByTypeAndPlayer(String type, String playerName) {
        String sql;
        if (type.equals("JAIL")) {
            sql = "SELECT COUNT(*) FROM jail_punishments WHERE player_name = ?";
        } else if (type.equals("WARNING")) {
            sql = "SELECT COUNT(*) FROM warnings WHERE player_name = ?";
        } else {
            sql = "SELECT COUNT(*) FROM punishments WHERE type = ? AND player_name = ?";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (type.equals("JAIL") || type.equals("WARNING")) {
                ps.setString(1, playerName);
            } else {
                ps.setString(1, type);
                ps.setString(2, playerName);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get punishments count by type and player: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getTotalPunishmentsCountByPlayer(String playerName) {
        String sql = "SELECT COUNT(*) FROM (SELECT 1 FROM punishments WHERE player_name = ? UNION ALL SELECT 1 FROM jail_punishments WHERE player_name = ? UNION ALL SELECT 1 FROM warnings WHERE player_name = ?) AS t";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ps.setString(2, playerName);
            ps.setString(3, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get total punishments count by player: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getPunishmentsIssuedByPlayer(String issuerName) {
        String sql = "SELECT COUNT(*) FROM (SELECT 1 FROM punishments WHERE issuer_name = ? UNION ALL SELECT 1 FROM jail_punishments WHERE issuer_name = ? UNION ALL SELECT 1 FROM warnings WHERE issuer_name = ?) AS t";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, issuerName);
            ps.setString(2, issuerName);
            ps.setString(3, issuerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get punishments issued by player: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getPunishmentsIssuedByPlayerAndType(String issuerName, String type) {
        String sql;
        if (type.equals("JAIL")) {
            sql = "SELECT COUNT(*) FROM jail_punishments WHERE issuer_name = ?";
        } else if (type.equals("WARNING")) {
            sql = "SELECT COUNT(*) FROM warnings WHERE issuer_name = ?";
        } else {
            sql = "SELECT COUNT(*) FROM punishments WHERE issuer_name = ? AND type = ?";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (type.equals("JAIL") || type.equals("WARNING")) {
                ps.setString(1, issuerName);
            } else {
                ps.setString(1, issuerName);
                ps.setString(2, type);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get punishments issued by player and type: " + e.getMessage());
        }
        return 0;
    }

    // ==================== НОВЫЕ МЕТОДЫ ДЛЯ УВЕДОМЛЕНИЙ ====================

    @Override
    public void addNotification(UUID playerUuid, String messageKey, Map<String, String> placeholders) {
        String sql = "INSERT INTO player_notifications (uuid, message_key, placeholders, created) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, messageKey);
            if (placeholders != null && !placeholders.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    sb.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
                }
                ps.setString(3, sb.toString());
            } else {
                ps.setString(3, null);
            }
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add notification: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, String>> getAndClearNotifications(UUID playerUuid) {
        List<Map<String, String>> result = new ArrayList<>();
        String select = "SELECT message_key, placeholders FROM player_notifications WHERE uuid = ? ORDER BY created ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> map = new HashMap<>();
                map.put("key", rs.getString("message_key"));
                String placeholdersStr = rs.getString("placeholders");
                if (placeholdersStr != null && !placeholdersStr.isEmpty()) {
                    String[] pairs = placeholdersStr.split(";");
                    for (String pair : pairs) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2) map.put(kv[0], kv[1]);
                    }
                }
                result.add(map);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get notifications: " + e.getMessage());
        }
        String delete = "DELETE FROM player_notifications WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(delete)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clear notifications: " + e.getMessage());
        }
        return result;
    }

    @Override
    public void clearNotifications(UUID playerUuid) {
        String sql = "DELETE FROM player_notifications WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clear notifications: " + e.getMessage());
        }
    }

    @Override
    public long getLastSeen(UUID uuid) {
        String sql = "SELECT last_seen FROM players WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("last_seen");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get last_seen: " + e.getMessage());
        }
        return 0;
    }

    // ==================== НОВЫЕ МЕТОДЫ ДЛЯ ОПТИМИЗАЦИИ ====================

    @Override
    public String getPlayerIpByName(String playerName) {
        String sql = "SELECT ip FROM players WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("ip");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get IP by name: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<String> getPlayerNamesByIp(String playerName) {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM players WHERE ip = (SELECT ip FROM players WHERE name = ?) AND name != ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ps.setString(2, playerName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player names by IP: " + e.getMessage());
        }
        return names;
    }

    @Override
    public Map<UUID, List<Punishment>> getActivePunishmentsForPlayers(Set<UUID> playerUuids, String currentServer, String mode) {
        Map<UUID, List<Punishment>> result = new HashMap<>();
        if (playerUuids.isEmpty()) return result;

        String placeholders = String.join(",", Collections.nCopies(playerUuids.size(), "?"));
        String sql;
        if ("sync_static".equalsIgnoreCase(mode)) {
            sql = "SELECT * FROM punishments WHERE player_uuid IN (" + placeholders + ") AND active=1 AND server_name=?";
        } else {
            sql = "SELECT * FROM punishments WHERE player_uuid IN (" + placeholders + ") AND active=1";
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (UUID uuid : playerUuids) {
                ps.setString(index++, uuid.toString());
            }
            if ("sync_static".equalsIgnoreCase(mode)) {
                ps.setString(index, currentServer);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Punishment p = mapResultSet(rs);
                result.computeIfAbsent(p.getPlayerUuid(), k -> new ArrayList<>()).add(p);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get active punishments for players: " + e.getMessage());
        }
        return result;
    }

    @Override
    public List<Punishment> getActivePunishmentsIncludingJail(UUID playerUuid, String currentServer, String mode) {
        List<Punishment> list = new ArrayList<>();
        // Получаем обычные наказания
        list.addAll(getActivePunishments(playerUuid, currentServer, mode));
        // Получаем активные джейлы
        JailPunishment jail = getActiveJail(playerUuid);
        if (jail != null && jail.isActive() && !jail.isExpired()) {
            Punishment p = mapJailAsPunishment(jail);
            if (!p.isExpired()) list.add(p);
        }
        // Получаем активные варны
        List<Warning> warnings = getActiveWarnings(playerUuid);
        for (Warning w : warnings) {
            Punishment p = mapWarningAsPunishment(w);
            if (!p.isExpired()) list.add(p);
        }
        list.sort(Comparator.comparingLong(Punishment::getStartTime).reversed());
        return list;
    }

    @Override
    public List<Punishment> getAllPunishmentsIncludingJail() {
        List<Punishment> list = new ArrayList<>();
        String sql = "SELECT id, player_uuid, player_name, issuer_uuid, issuer_name, type, reason, start_time, end_time, active, server_name, pardoned_by, pardoned_at, pardon_reason FROM punishments " +
                "UNION ALL " +
                "SELECT id, player_uuid, player_name, issuer_uuid, issuer_name, 'JAIL' AS type, reason, start_time, end_time, active, server_name, pardoned_by, pardoned_at, NULL AS pardon_reason FROM jail_punishments " +
                "UNION ALL " +
                "SELECT id, player_uuid, player_name, issuer_uuid, issuer_name, 'WARNING' AS type, reason, start_time, end_time, active, server_name, pardoned_by, pardoned_at, NULL AS pardon_reason FROM warnings " +
                "ORDER BY start_time DESC";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapPunishmentWithType(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all punishments including jail: " + e.getMessage());
        }
        return list;
    }

    @Override
    public List<Punishment> getPunishmentHistoryIncludingJail(UUID playerUuid) {
        List<Punishment> list = new ArrayList<>();
        String sql = "SELECT id, player_uuid, player_name, issuer_uuid, issuer_name, type, reason, start_time, end_time, active, server_name, pardoned_by, pardoned_at, pardon_reason FROM punishments WHERE player_uuid = ? " +
                "UNION ALL " +
                "SELECT id, player_uuid, player_name, issuer_uuid, issuer_name, 'JAIL' AS type, reason, start_time, end_time, active, server_name, pardoned_by, pardoned_at, NULL AS pardon_reason FROM jail_punishments WHERE player_uuid = ? " +
                "UNION ALL " +
                "SELECT id, player_uuid, player_name, issuer_uuid, issuer_name, 'WARNING' AS type, reason, start_time, end_time, active, server_name, pardoned_by, pardoned_at, NULL AS pardon_reason FROM warnings WHERE player_uuid = ? " +
                "ORDER BY start_time DESC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerUuid.toString());
            ps.setString(3, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapPunishmentWithType(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get punishment history including jail: " + e.getMessage());
        }
        return list;
    }

    @Override
    public List<Punishment> getAllActivePunishmentsByType(PunishmentType type) {
        List<Punishment> list = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE type=? AND active=1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all active punishments by type: " + e.getMessage());
        }
        return list;
    }

    // ==================== MAP METHODS ====================

    private Punishment mapResultSet(ResultSet rs) throws SQLException {
        Punishment p = new Punishment();
        p.setId(rs.getString("id"));
        p.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
        p.setPlayerName(rs.getString("player_name"));
        p.setIssuerUuid(UUID.fromString(rs.getString("issuer_uuid")));
        p.setIssuerName(rs.getString("issuer_name"));
        p.setType(PunishmentType.valueOf(rs.getString("type")));
        p.setReason(rs.getString("reason"));
        p.setStartTime(rs.getLong("start_time"));
        long end = rs.getLong("end_time");
        p.setEndTime(rs.wasNull() ? null : end);
        p.setActive(rs.getBoolean("active"));
        p.setPardonedBy(rs.getString("pardoned_by"));
        long pardonedAt = rs.getLong("pardoned_at");
        p.setPardonedAt(rs.wasNull() ? null : pardonedAt);
        p.setPardonReason(rs.getString("pardon_reason"));
        p.setServerName(rs.getString("server_name"));
        return p;
    }

    private Punishment mapJailAsPunishment(JailPunishment jail) {
        Punishment p = new Punishment();
        p.setId(jail.getId());
        p.setPlayerUuid(jail.getPlayerUuid());
        p.setPlayerName(jail.getPlayerName());
        p.setIssuerUuid(jail.getIssuerUuid());
        p.setIssuerName(jail.getIssuerName());
        p.setType(PunishmentType.JAIL);
        p.setReason(jail.getReason());
        p.setStartTime(jail.getStartTime());
        p.setEndTime(jail.getEndTime());
        p.setActive(jail.isActive());
        p.setServerName(jail.getServerName());
        p.setPardonedBy(jail.getPardonedBy());
        p.setPardonedAt(jail.getPardonedAt());
        return p;
    }

    private Punishment mapPunishmentWithType(ResultSet rs) throws SQLException {
        Punishment p = new Punishment();
        p.setId(rs.getString("id"));
        p.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
        p.setPlayerName(rs.getString("player_name"));
        p.setIssuerUuid(UUID.fromString(rs.getString("issuer_uuid")));
        p.setIssuerName(rs.getString("issuer_name"));
        p.setType(PunishmentType.valueOf(rs.getString("type")));
        p.setReason(rs.getString("reason"));
        p.setStartTime(rs.getLong("start_time"));
        long end = rs.getLong("end_time");
        p.setEndTime(rs.wasNull() ? null : end);
        p.setActive(rs.getBoolean("active"));
        p.setServerName(rs.getString("server_name"));
        p.setPardonedBy(rs.getString("pardoned_by"));
        long pardonedAt = rs.getLong("pardoned_at");
        p.setPardonedAt(rs.wasNull() ? null : pardonedAt);
        p.setPardonReason(rs.getString("pardon_reason"));
        return p;
    }

    private Punishment mapWarningAsPunishment(Warning warning) {
        Punishment p = new Punishment();
        p.setId(warning.getId());
        p.setPlayerUuid(warning.getPlayerUuid());
        p.setPlayerName(warning.getPlayerName());
        p.setIssuerUuid(warning.getIssuerUuid());
        p.setIssuerName(warning.getIssuerName());
        p.setType(PunishmentType.WARNING);
        p.setReason(warning.getReason());
        p.setStartTime(warning.getStartTime());
        p.setEndTime(warning.getEndTime());
        p.setActive(warning.isActive());
        p.setServerName(warning.getServerName());
        p.setPardonedBy(warning.getPardonedBy());
        p.setPardonedAt(warning.getPardonedAt());
        return p;
    }

    private JailPunishment mapJailResultSet(ResultSet rs) throws SQLException {
        JailPunishment j = new JailPunishment();
        j.setId(rs.getString("id"));
        j.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
        j.setPlayerName(rs.getString("player_name"));
        j.setIssuerUuid(UUID.fromString(rs.getString("issuer_uuid")));
        j.setIssuerName(rs.getString("issuer_name"));
        j.setReason(rs.getString("reason"));
        j.setStartTime(rs.getLong("start_time"));
        long end = rs.getLong("end_time");
        j.setEndTime(rs.wasNull() ? null : end);
        j.setActive(rs.getBoolean("active"));
        j.setServerName(rs.getString("server_name"));
        j.setPardonedBy(rs.getString("pardoned_by"));
        long pardonedAt = rs.getLong("pardoned_at");
        j.setPardonedAt(rs.wasNull() ? null : pardonedAt);
        String prevLoc = rs.getString("previous_location");
        if (prevLoc != null && !prevLoc.isEmpty() && !prevLoc.equals("null")) {
            String[] parts = prevLoc.split(";");
            if (parts.length == 6) {
                org.bukkit.World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    try {
                        j.setPreviousLocation(new Location(world,
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]),
                                Float.parseFloat(parts[4]),
                                Float.parseFloat(parts[5])));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        String jailLocStr = rs.getString("jail_location");
        if (jailLocStr != null && !jailLocStr.isEmpty() && !jailLocStr.equals("null")) {
            String[] parts = jailLocStr.split(";");
            if (parts.length == 6) {
                org.bukkit.World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    try {
                        j.setJailLocation(new Location(world,
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]),
                                Float.parseFloat(parts[4]),
                                Float.parseFloat(parts[5])));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return j;
    }

    private Warning mapWarningResultSet(ResultSet rs) throws SQLException {
        Warning w = new Warning();
        w.setId(rs.getString("id"));
        w.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
        w.setPlayerName(rs.getString("player_name"));
        w.setIssuerUuid(UUID.fromString(rs.getString("issuer_uuid")));
        w.setIssuerName(rs.getString("issuer_name"));
        w.setReason(rs.getString("reason"));
        w.setStartTime(rs.getLong("start_time"));
        long end = rs.getLong("end_time");
        w.setEndTime(rs.wasNull() ? null : end);
        w.setActive(rs.getBoolean("active"));
        w.setServerName(rs.getString("server_name"));
        w.setPardonedBy(rs.getString("pardoned_by"));
        long pardonedAt = rs.getLong("pardoned_at");
        w.setPardonedAt(rs.wasNull() ? null : pardonedAt);
        return w;
    }
}
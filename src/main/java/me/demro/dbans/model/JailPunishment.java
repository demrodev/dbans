package me.demro.dbans.model;

import org.bukkit.Location;
import java.util.UUID;

public class JailPunishment {
    private String id;
    private UUID playerUuid;
    private String playerName;
    private UUID issuerUuid;
    private String issuerName;
    private String reason;
    private long startTime;
    private Long endTime;
    private boolean active;
    private String serverName;
    private String pardonedBy;
    private Long pardonedAt;
    private Location previousLocation;
    private Location jailLocation;  // координаты платформы в мире jail

    public JailPunishment() {}

    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public UUID getIssuerUuid() { return issuerUuid; }
    public void setIssuerUuid(UUID issuerUuid) { this.issuerUuid = issuerUuid; }

    public String getIssuerName() { return issuerName; }
    public void setIssuerName(String issuerName) { this.issuerName = issuerName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    public String getPardonedBy() { return pardonedBy; }
    public void setPardonedBy(String pardonedBy) { this.pardonedBy = pardonedBy; }

    public Long getPardonedAt() { return pardonedAt; }
    public void setPardonedAt(Long pardonedAt) { this.pardonedAt = pardonedAt; }

    public Location getPreviousLocation() { return previousLocation; }
    public void setPreviousLocation(Location previousLocation) { this.previousLocation = previousLocation; }

    public Location getJailLocation() { return jailLocation; }
    public void setJailLocation(Location jailLocation) { this.jailLocation = jailLocation; }

    public boolean isExpired() {
        return endTime != null && System.currentTimeMillis() > endTime;
    }
}
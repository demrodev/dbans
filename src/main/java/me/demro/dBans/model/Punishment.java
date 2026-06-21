package me.demro.dBans.model;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Punishment {
    private String id;
    private UUID playerUuid;
    private String playerName;
    private UUID issuerUuid;
    private String issuerName;
    private PunishmentType type;
    private String reason;
    private long startTime;
    private Long endTime;
    private boolean active;
    private String pardonedBy;
    private Long pardonedAt;
    private String pardonReason;
    private String serverName;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int ID_LENGTH = 5;

    public static String generateId() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            int index = ThreadLocalRandom.current().nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }
        return sb.toString();
    }

    public Punishment() {
        this.id = generateId();
    }

    public Punishment(UUID playerUuid, String playerName, UUID issuerUuid, String issuerName,
                      PunishmentType type, String reason, long startTime, Long endTime, String serverName) {
        this.id = generateId();
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.issuerUuid = issuerUuid;
        this.issuerName = issuerName;
        this.type = type;
        this.reason = reason;
        this.startTime = startTime;
        this.endTime = endTime;
        this.active = true;
        this.serverName = serverName;
    }

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

    public PunishmentType getType() { return type; }
    public void setType(PunishmentType type) { this.type = type; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getPardonedBy() { return pardonedBy; }
    public void setPardonedBy(String pardonedBy) { this.pardonedBy = pardonedBy; }

    public Long getPardonedAt() { return pardonedAt; }
    public void setPardonedAt(Long pardonedAt) { this.pardonedAt = pardonedAt; }

    public String getPardonReason() { return pardonReason; }
    public void setPardonReason(String pardonReason) { this.pardonReason = pardonReason; }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    public boolean isExpired() {
        return endTime != null && System.currentTimeMillis() > endTime;
    }
}
package me.demro.dBans.model;

import java.util.UUID;

public class PlayerInfo {
    private UUID uuid;
    private String name;
    private String ip;
    private long lastSeen;

    public PlayerInfo(UUID uuid, String name, String ip, long lastSeen) {
        this.uuid = uuid;
        this.name = name;
        this.ip = ip;
        this.lastSeen = lastSeen;
    }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
}
package me.demro.dbans.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Location;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class JailPunishment {
    @Builder.Default
    private String id = Punishment.generateId();
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
    private Location jailLocation;

    public boolean isExpired() {
        return endTime != null && System.currentTimeMillis() > endTime;
    }
}
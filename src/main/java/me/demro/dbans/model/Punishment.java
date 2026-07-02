package me.demro.dbans.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Punishment {
    @Builder.Default
    private String id = generateId();
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

    public boolean isExpired() {
        return endTime != null && System.currentTimeMillis() > endTime;
    }
}
package me.demro.dbans.model;

import java.util.UUID;

public interface InternalPunishment {

    String getId();

    UUID getPlayerUuid();

    String getPlayerName();

    UUID getIssuerUuid();

    String getIssuerName();

    String getReason();

    long getStartTime();

    Long getEndTime();

    boolean isActive();

    void setActive(boolean active);

    boolean isExpired();

    String getServerName();

    void setPardonedBy(String pardonedBy);

    void setPardonedAt(Long pardonedAt);
}

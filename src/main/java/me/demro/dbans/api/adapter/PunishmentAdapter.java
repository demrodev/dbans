package me.demro.dbans.api.adapter;

import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.Warning;
import me.demro.dlibs.dbans.api.punishment.PunishmentId;
import me.demro.dlibs.dbans.api.punishment.PunishmentIssuer;
import me.demro.dlibs.dbans.api.punishment.PunishmentReason;
import me.demro.dlibs.dbans.api.punishment.PunishmentStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class PunishmentAdapter implements me.demro.dlibs.dbans.api.punishment.Punishment {

    public static final UUID CONSOLE_UUID = UUID.nameUUIDFromBytes("CONSOLE".getBytes());

    private final String id;
    private final UUID targetUuid;
    private final String targetName;
    private final UUID issuerUuid;
    private final String issuerName;
    private final me.demro.dlibs.dbans.api.punishment.PunishmentType type;
    private final String reason;
    private final long startTime;
    private final Long endTime;
    private final boolean active;
    private final String serverName;

    public PunishmentAdapter(@NotNull Punishment punishment) {
        this.id = punishment.getId();
        this.targetUuid = punishment.getPlayerUuid();
        this.targetName = punishment.getPlayerName();
        this.issuerUuid = punishment.getIssuerUuid();
        this.issuerName = punishment.getIssuerName();
        this.type = PunishmentMapper.toApiType(punishment.getType());
        this.reason = punishment.getReason();
        this.startTime = punishment.getStartTime();
        this.endTime = punishment.getEndTime();
        this.active = punishment.isActive();
        this.serverName = punishment.getServerName();
    }

    public PunishmentAdapter(@NotNull JailPunishment jail) {
        this.id = jail.getId();
        this.targetUuid = jail.getPlayerUuid();
        this.targetName = jail.getPlayerName();
        this.issuerUuid = jail.getIssuerUuid();
        this.issuerName = jail.getIssuerName();
        this.type = me.demro.dlibs.dbans.api.punishment.PunishmentType.JAIL;
        this.reason = jail.getReason();
        this.startTime = jail.getStartTime();
        this.endTime = jail.getEndTime();
        this.active = jail.isActive();
        this.serverName = jail.getServerName();
    }

    public PunishmentAdapter(@NotNull Warning warning) {
        this.id = warning.getId();
        this.targetUuid = warning.getPlayerUuid();
        this.targetName = warning.getPlayerName();
        this.issuerUuid = warning.getIssuerUuid();
        this.issuerName = warning.getIssuerName();
        this.type = me.demro.dlibs.dbans.api.punishment.PunishmentType.WARNING;
        this.reason = warning.getReason();
        this.startTime = warning.getStartTime();
        this.endTime = warning.getEndTime();
        this.active = warning.isActive();
        this.serverName = warning.getServerName();
    }

    @Contract(" -> new")
    @Override
    public @NotNull PunishmentId id() {
        return PunishmentId.of(id);
    }

    @Contract(pure = true)
    @Override
    public @NotNull String shortId() {
        return id;
    }

    @Contract(pure = true)
    @Override
    public @NotNull UUID targetUuid() {
        return targetUuid;
    }

    @Contract(pure = true)
    @Override
    public @NotNull String targetName() {
        return targetName;
    }

    @Override
    public @NotNull PunishmentIssuer issuer() {
        if (CONSOLE_UUID.equals(issuerUuid)) {
            return PunishmentIssuer.console();
        }
        return PunishmentIssuer.player(issuerUuid, issuerName);
    }

    @Contract(pure = true)
    @Override
    public @NotNull me.demro.dlibs.dbans.api.punishment.PunishmentType type() {
        return type;
    }

    @Contract(" -> new")
    @Override
    public @NotNull PunishmentReason reason() {
        return PunishmentReason.of(reason);
    }

    @Contract(pure = true)
    @Override
    public @NotNull Instant createdAt() {
        return Instant.ofEpochMilli(startTime);
    }

    @Override
    public @NotNull Optional<Instant> expiresAt() {
        return endTime == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(endTime));
    }

    @Override
    public @NotNull PunishmentStatus status() {
        if (!active) {
            return PunishmentStatus.REVOKED;
        }
        if (endTime != null && System.currentTimeMillis() > endTime) {
            return PunishmentStatus.EXPIRED;
        }
        return PunishmentStatus.ACTIVE;
    }

    @Contract(pure = true)
    @Override
    public @NotNull String serverName() {
        return serverName != null ? serverName : "unknown";
    }
}
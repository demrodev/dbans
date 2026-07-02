package me.demro.dbans.api.adapter;

import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.Warning;
import me.demro.dlibs.dbans.api.punishment.*;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that implements the new API's {@link me.demro.dlibs.dbans.api.punishment.Punishment} interface
 * for any of the three internal punishment models.
 */
public final class NewPunishmentAdapter implements me.demro.dlibs.dbans.api.punishment.Punishment {

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

    /**
     * Constructs an adapter from a standard {@link Punishment}.
     */
    public NewPunishmentAdapter(Punishment p) {
        this.id = p.getId();
        this.targetUuid = p.getPlayerUuid();
        this.targetName = p.getPlayerName();
        this.issuerUuid = p.getIssuerUuid();
        this.issuerName = p.getIssuerName();
        this.type = mapType(p.getType());
        this.reason = p.getReason();
        this.startTime = p.getStartTime();
        this.endTime = p.getEndTime();
        this.active = p.isActive();
        this.serverName = p.getServerName();
    }

    /**
     * Constructs an adapter from a {@link JailPunishment}.
     */
    public NewPunishmentAdapter(JailPunishment j) {
        this.id = j.getId();
        this.targetUuid = j.getPlayerUuid();
        this.targetName = j.getPlayerName();
        this.issuerUuid = j.getIssuerUuid();
        this.issuerName = j.getIssuerName();
        this.type = me.demro.dlibs.dbans.api.punishment.PunishmentType.JAIL;
        this.reason = j.getReason();
        this.startTime = j.getStartTime();
        this.endTime = j.getEndTime();
        this.active = j.isActive();
        this.serverName = j.getServerName();
    }

    /**
     * Constructs an adapter from a {@link Warning}.
     */
    public NewPunishmentAdapter(Warning w) {
        this.id = w.getId();
        this.targetUuid = w.getPlayerUuid();
        this.targetName = w.getPlayerName();
        this.issuerUuid = w.getIssuerUuid();
        this.issuerName = w.getIssuerName();
        this.type = me.demro.dlibs.dbans.api.punishment.PunishmentType.WARNING;
        this.reason = w.getReason();
        this.startTime = w.getStartTime();
        this.endTime = w.getEndTime();
        this.active = w.isActive();
        this.serverName = w.getServerName();
    }

    private static me.demro.dlibs.dbans.api.punishment.PunishmentType mapType(me.demro.dbans.model.PunishmentType oldType) {
        switch (oldType) {
            case BAN: return me.demro.dlibs.dbans.api.punishment.PunishmentType.BAN;
            case MUTE: return me.demro.dlibs.dbans.api.punishment.PunishmentType.MUTE;
            case KICK: return me.demro.dlibs.dbans.api.punishment.PunishmentType.KICK;
            case IPBAN: return me.demro.dlibs.dbans.api.punishment.PunishmentType.IP_BAN;
            case JAIL: return me.demro.dlibs.dbans.api.punishment.PunishmentType.JAIL;
            case WARNING: return me.demro.dlibs.dbans.api.punishment.PunishmentType.WARNING;
            default: throw new IllegalArgumentException("Unknown type: " + oldType);
        }
    }

    @Override
    public @NotNull PunishmentId id() {
        return PunishmentId.of(id);
    }

    @Override
    public @NotNull String shortId() {
        return id;
    }

    @Override
    public @NotNull UUID targetUuid() {
        return targetUuid;
    }

    @Override
    public @NotNull String targetName() {
        return targetName;
    }

    @Override
    public @NotNull PunishmentIssuer issuer() {
        if (issuerUuid.equals(UUID.nameUUIDFromBytes("CONSOLE".getBytes()))) {
            return PunishmentIssuer.console();
        }
        return PunishmentIssuer.player(issuerUuid, issuerName);
    }

    @Override
    public @NotNull me.demro.dlibs.dbans.api.punishment.PunishmentType type() {
        return type;
    }

    @Override
    public @NotNull PunishmentReason reason() {
        return PunishmentReason.of(reason);
    }

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

    @Override
    public @NotNull String serverName() {
        return serverName != null ? serverName : "unknown";
    }
}
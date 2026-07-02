package me.demro.dbans.service;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dlibs.dbans.api.player.PlayerIdentity;
import me.demro.dlibs.dbans.api.punishment.*;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for applying punishments using the new DBans API.
 * This class delegates all operations to {@link PunishmentService}.
 */
@Slf4j
public class PunishService {

    private final DBans plugin;
    private final PunishmentService punishmentService;

    public PunishService(DBans plugin) {
        this.plugin = plugin;
        this.punishmentService = plugin.getApi().punishments();
    }

    /**
     * Applies a punishment using the new API.
     *
     * @param targetUuid  UUID of the punished player
     * @param targetName  Name of the punished player
     * @param issuerUuid  UUID of the issuer
     * @param issuerName  Name of the issuer
     * @param type        Punishment type (BAN, MUTE, KICK, IPBAN, JAIL, WARNING)
     * @param reason      Punishment reason
     * @param duration    Duration in milliseconds (null or <=0 for permanent/instant)
     * @param server      Server name
     * @param silent      Whether the punishment should be silent
     * @return The created punishment (internal model), or null if failed
     */
    public Punishment applyPunishment(UUID targetUuid, String targetName,
                                      UUID issuerUuid, String issuerName,
                                      PunishmentType type, String reason,
                                      Long duration, String server, boolean silent) {

        // Convert internal PunishmentType to API PunishmentType
        me.demro.dlibs.dbans.api.punishment.PunishmentType apiType;
        switch (type) {
            case BAN:
                apiType = me.demro.dlibs.dbans.api.punishment.PunishmentType.BAN;
                break;
            case MUTE:
                apiType = me.demro.dlibs.dbans.api.punishment.PunishmentType.MUTE;
                break;
            case KICK:
                apiType = me.demro.dlibs.dbans.api.punishment.PunishmentType.KICK;
                break;
            case IPBAN:
                apiType = me.demro.dlibs.dbans.api.punishment.PunishmentType.IP_BAN;
                break;
            case JAIL:
                apiType = me.demro.dlibs.dbans.api.punishment.PunishmentType.JAIL;
                break;
            case WARNING:
                apiType = me.demro.dlibs.dbans.api.punishment.PunishmentType.WARNING;
                break;
            default:
                throw new IllegalArgumentException("Unsupported punishment type: " + type);
        }

        // Build duration
        PunishmentDuration apiDuration;
        if (apiType == me.demro.dlibs.dbans.api.punishment.PunishmentType.KICK) {
            apiDuration = PunishmentDuration.instant();
        } else if (duration == null || duration <= 0) {
            apiDuration = PunishmentDuration.permanent();
        } else {
            apiDuration = PunishmentDuration.temporary(Duration.ofMillis(duration));
        }

        // Build issuer
        PunishmentIssuer issuer;
        if (issuerUuid.equals(UUID.nameUUIDFromBytes("CONSOLE".getBytes()))) {
            issuer = PunishmentIssuer.console();
        } else {
            issuer = PunishmentIssuer.player(issuerUuid, issuerName);
        }

        // Build request
        PunishmentCreateRequest request = PunishmentCreateRequest.builder()
                .target(PlayerIdentity.of(targetUuid, targetName))
                .type(apiType)
                .reason(PunishmentReason.of(reason))
                .duration(apiDuration)
                .issuer(issuer)
                .serverName(server)
                .options(PunishmentOptions.builder()
                        .silent(silent)
                        .broadcast(!silent)
                        .notifyTarget(true)
                        .build())
                .build();

        try {
            // Execute punishment creation via new API
            PunishmentCreateResult result = punishmentService.create(request).join();

            // Retrieve the internal punishment model by ID from the database
            String id = result.punishment().id().value();
            Punishment internalPunishment = plugin.getDatabase().getPunishmentById(id);
            if (internalPunishment == null) {
                // For JAIL or WARNING, the punishment might be in a different table,
                // but we still return a Punishment object; we can create a synthetic one.
                // For simplicity, we return null and log a warning.
                log.warn("Could not find internal punishment with ID {} after creation", id);
                return null;
            }
            return internalPunishment;

        } catch (Exception e) {
            log.error("Failed to apply punishment via new API: {}", e.getMessage(), e);
            // Re-throw or handle as needed; we'll return null to indicate failure.
            return null;
        }
    }

    // Additional helper methods can be added if needed, but the main one is above.
}
package me.demro.dbans.api.adapter;

import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.Warning;
import me.demro.dlibs.dbans.api.punishment.PunishmentCreateRequest;
import me.demro.dlibs.dbans.api.punishment.PunishmentDuration;

import java.util.UUID;

/**
 * Utility class to convert between new API request objects and internal models.
 */
public final class PunishmentMapper {

    private PunishmentMapper() {
    }

    /**
     * Converts a {@link PunishmentCreateRequest} into an internal {@link Punishment} builder.
     * This does NOT save the punishment, only prepares the builder.
     */
    public static Punishment.PunishmentBuilder toInternalPunishment(PunishmentCreateRequest request) {
        Punishment.PunishmentBuilder builder = Punishment.builder()
                                                         .playerUuid(request.target().uuid().orElseThrow(() -> new IllegalArgumentException("Target UUID required")))
                                                         .playerName(request.target().name().orElse("Unknown"))
                                                         .issuerUuid(request.issuer().uuid().orElse(UUID.nameUUIDFromBytes("CONSOLE".getBytes())))
                                                         .issuerName(request.issuer().name())
                                                         .reason(request.reason().value())
                                                         .startTime(System.currentTimeMillis())
                                                         .active(true)
                                                         .serverName(request.serverName());

        // Map type
        me.demro.dbans.model.PunishmentType internalType;
        switch (request.type()) {
            case BAN:
                internalType = me.demro.dbans.model.PunishmentType.BAN;
                break;
            case MUTE:
                internalType = me.demro.dbans.model.PunishmentType.MUTE;
                break;
            case KICK:
                internalType = me.demro.dbans.model.PunishmentType.KICK;
                break;
            case IP_BAN:
                internalType = me.demro.dbans.model.PunishmentType.IPBAN;
                break;
            case JAIL:
                internalType = me.demro.dbans.model.PunishmentType.JAIL;
                break;
            case WARNING:
                internalType = me.demro.dbans.model.PunishmentType.WARNING;
                break;
            default:
                throw new IllegalArgumentException("Unsupported type: " + request.type());
        }
        builder.type(internalType);

        // Duration
        PunishmentDuration duration = request.duration();
        if (duration.isPermanent()) {
            builder.endTime(null);
        } else if (duration.isInstant()) {
            builder.endTime(null); // kicks are instant but we store as permanent? Actually kicks have no end time.
        } else if (duration.temporaryDuration().isPresent()) {
            long durationMillis = duration.temporaryDuration().get().toMillis();
            builder.endTime(System.currentTimeMillis() + durationMillis);
        } else {
            throw new IllegalArgumentException("Invalid duration");
        }

        return builder;
    }

    /**
     * Creates a new API {@link me.demro.dlibs.dbans.api.punishment.Punishment} instance from any internal model.
     */
    public static me.demro.dlibs.dbans.api.punishment.Punishment toApiPunishment(Object internal) {
        if (internal instanceof Punishment) {
            return new NewPunishmentAdapter((Punishment) internal);
        } else if (internal instanceof JailPunishment) {
            return new NewPunishmentAdapter((JailPunishment) internal);
        } else if (internal instanceof Warning) {
            return new NewPunishmentAdapter((Warning) internal);
        } else {
            throw new IllegalArgumentException("Unsupported internal type: " + internal.getClass());
        }
    }
}
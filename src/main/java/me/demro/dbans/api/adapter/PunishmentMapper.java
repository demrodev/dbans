package me.demro.dbans.api.adapter;

import lombok.experimental.UtilityClass;
import me.demro.dbans.model.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public final class PunishmentMapper {

    public static me.demro.dlibs.dbans.api.punishment.@NotNull Punishment toApiPunishment(
            @NotNull InternalPunishment internal
    ) {
        return switch (internal) {
            case Punishment punishment -> new PunishmentAdapter(punishment);
            case JailPunishment jail -> new PunishmentAdapter(jail);
            case Warning warning -> new PunishmentAdapter(warning);
            default -> throw new IllegalArgumentException("Unsupported internal type: " + internal.getClass());
        };
    }

    public static me.demro.dlibs.dbans.api.punishment.@NotNull PunishmentType toApiType(
            @NotNull InternalPunishment internal
    ) {
        return switch (internal) {
            case JailPunishment jail -> me.demro.dlibs.dbans.api.punishment.PunishmentType.JAIL;
            case Warning warning -> me.demro.dlibs.dbans.api.punishment.PunishmentType.WARNING;
            case Punishment punishment -> toApiType(punishment.getType());
            default -> throw new IllegalArgumentException("Unsupported internal type: " + internal.getClass());
        };
    }

    @Contract(pure = true)
    public static me.demro.dlibs.dbans.api.punishment.@NotNull PunishmentType toApiType(
            @NotNull PunishmentType internalType
    ) {
        return switch (internalType) {
            case BAN -> me.demro.dlibs.dbans.api.punishment.PunishmentType.BAN;
            case MUTE -> me.demro.dlibs.dbans.api.punishment.PunishmentType.MUTE;
            case KICK -> me.demro.dlibs.dbans.api.punishment.PunishmentType.KICK;
            case IPBAN -> me.demro.dlibs.dbans.api.punishment.PunishmentType.IP_BAN;
            case JAIL -> me.demro.dlibs.dbans.api.punishment.PunishmentType.JAIL;
            case WARNING -> me.demro.dlibs.dbans.api.punishment.PunishmentType.WARNING;
        };
    }

    @Contract(pure = true)
    public static @NotNull PunishmentType toInternalType(
            me.demro.dlibs.dbans.api.punishment.@NotNull PunishmentType apiType
    ) {
        return switch (apiType) {
            case BAN -> PunishmentType.BAN;
            case MUTE -> PunishmentType.MUTE;
            case KICK -> PunishmentType.KICK;
            case IP_BAN -> PunishmentType.IPBAN;
            case JAIL -> PunishmentType.JAIL;
            case WARNING -> PunishmentType.WARNING;
        };
    }
}

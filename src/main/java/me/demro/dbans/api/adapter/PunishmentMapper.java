package me.demro.dbans.api.adapter;

import lombok.experimental.UtilityClass;
import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.Warning;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public final class PunishmentMapper {

    public static me.demro.dlibs.dbans.api.punishment.@NotNull Punishment toApiPunishment(@NotNull Object internal) {
        return switch (internal) {
            case Punishment punishment -> new PunishmentAdapter(punishment);
            case JailPunishment jailPunishment -> new PunishmentAdapter(jailPunishment);
            case Warning warning -> new PunishmentAdapter(warning);
            default -> throw new IllegalArgumentException("Unsupported internal type: " + internal.getClass());
        };
    }
}
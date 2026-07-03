package me.demro.dbans.api.impl;

import lombok.RequiredArgsConstructor;
import me.demro.dbans.DBans;
import me.demro.dbans.api.adapter.PunishmentAdapter;
import me.demro.dbans.model.*;
import me.demro.dlibs.dbans.api.exception.DBansException;
import me.demro.dlibs.dbans.api.exception.PlayerNotFoundException;
import me.demro.dlibs.dbans.api.punishment.PunishmentCreateRequest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.UUID;

@RequiredArgsConstructor
class PunishmentCreator {

    private final DBans plugin;

    @NotNull
    InternalPunishment create(@NotNull PunishmentCreateRequest request,
                              @NotNull UUID targetUuid, @NotNull String targetName,
                              @NotNull PunishmentType internalType,
                              long startTime, Long endTime
    ) {
        return switch (internalType) {
            case JAIL -> createJail(request, targetUuid);
            case WARNING -> createWarning(request, targetUuid, targetName, startTime, endTime);
            case IPBAN -> createIpBan(request, targetUuid, targetName, startTime, endTime);
            default -> createStandard(request, targetUuid, targetName, internalType, startTime, endTime);
        };
    }

    private @NotNull JailPunishment createJail(@NotNull PunishmentCreateRequest request,
                                               @NotNull UUID targetUuid
    ) {
        Player online = Bukkit.getPlayer(targetUuid);
        if (online == null || !online.isOnline()) {
            throw new PlayerNotFoundException("Player must be online for jail");
        }

        Location previousLocation = online.getLocation().clone();
        long durationMs = request.duration().temporaryDuration().map(Duration::toMillis).orElse(0L);
        String jailId = plugin.getJailManager().sendToJail(
                online,
                durationMs > 0 ? durationMs : null,
                previousLocation,
                request.issuer().name(),
                request.reason().value()
        );
        if (jailId == null) {
            throw new DBansException("Failed to jail player");
        }

        JailPunishment jail = plugin.getDatabase().getActiveJail(targetUuid);
        if (jail == null) {
            throw new DBansException("Jail not found after creation");
        }
        return jail;
    }

    private @NotNull Warning createWarning(@NotNull PunishmentCreateRequest request,
                                           @NotNull UUID targetUuid, @NotNull String targetName,
                                           long startTime, Long endTime
    ) {
        Warning warning = Warning.builder()
                                 .playerUuid(targetUuid)
                                 .playerName(targetName)
                                 .issuerUuid(request.issuer().uuid().orElse(PunishmentAdapter.CONSOLE_UUID))
                                 .issuerName(request.issuer().name())
                                 .reason(request.reason().value())
                                 .startTime(startTime)
                                 .endTime(endTime)
                                 .active(true)
                                 .serverName(request.serverName())
                                 .build();
        plugin.getDatabase().saveWarning(warning);
        return warning;
    }

    private @NotNull Punishment createIpBan(@NotNull PunishmentCreateRequest request,
                                            @NotNull UUID targetUuid, @NotNull String targetName,
                                            long startTime, Long endTime
    ) {
        String ip = plugin.getDatabase().getPlayerIp(targetUuid);
        if (ip == null) {
            ip = request.target().name()
                        .filter(n -> n.matches("(\\d{1,3}\\.){3}(\\d{1,3}|\\*)"))
                        .orElseThrow(() -> new DBansException("No IP found for player"));
        }
        plugin.getDatabase().saveIpBan(ip,
                                       targetUuid, targetName,
                                       request.issuer().name(), request.reason().value(),
                                       startTime, endTime
        );
        Punishment ipBan = Punishment.builder()
                                     .playerUuid(targetUuid)
                                     .playerName(targetName)
                                     .issuerUuid(request.issuer().uuid().orElse(PunishmentAdapter.CONSOLE_UUID))
                                     .issuerName(request.issuer().name())
                                     .type(PunishmentType.IPBAN)
                                     .reason(request.reason().value())
                                     .startTime(startTime)
                                     .endTime(endTime)
                                     .active(true)
                                     .serverName(request.serverName())
                                     .build();
        plugin.getDatabase().savePunishment(ipBan);
        return ipBan;
    }

    private @NotNull Punishment createStandard(@NotNull PunishmentCreateRequest request,
                                               @NotNull UUID targetUuid, @NotNull String targetName,
                                               @NotNull PunishmentType internalType,
                                               long startTime, Long endTime
    ) {
        Punishment punishment = Punishment.builder()
                                          .playerUuid(targetUuid)
                                          .playerName(targetName)
                                          .issuerUuid(request.issuer().uuid().orElse(PunishmentAdapter.CONSOLE_UUID))
                                          .issuerName(request.issuer().name())
                                          .type(internalType)
                                          .reason(request.reason().value())
                                          .startTime(startTime)
                                          .endTime(endTime)
                                          .active(true)
                                          .serverName(request.serverName())
                                          .build();
        plugin.getDatabase().savePunishment(punishment);
        return punishment;
    }
}

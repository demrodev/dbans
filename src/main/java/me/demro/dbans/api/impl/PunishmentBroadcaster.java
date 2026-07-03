package me.demro.dbans.api.impl;

import me.demro.dbans.model.InternalPunishment;
import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.Warning;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import me.demro.dlibs.dbans.api.punishment.PunishmentCreateRequest;
import me.demro.dlibs.dbans.api.punishment.PunishmentRevokeRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PunishmentBroadcaster {

    void broadcastCreate(@NotNull InternalPunishment punishment,
                         @NotNull PunishmentCreateRequest request
    ) {
        BroadcastSpec spec = createSpec(punishment);
        if (spec == null) {
            return;
        }
        String duration = punishment.getEndTime() == null
                ? "навсегда"
                : TimeUtil.formatDuration(punishment.getEndTime() - punishment.getStartTime());
        MessageUtil.broadcast(spec.permission(), spec.key(),
                              "sender", request.issuer().name(),
                              "target", punishment.getPlayerName(),
                              "reason", punishment.getReason(),
                              "duration", duration,
                              "server", punishment.getServerName(),
                              "id", punishment.getId()
        );
    }

    void broadcastRevoke(@NotNull InternalPunishment punishment,
                         @NotNull PunishmentRevokeRequest request
    ) {
        BroadcastSpec spec = revokeSpec(punishment);
        if (spec == null) {
            return;
        }
        MessageUtil.broadcast(spec.permission(), spec.key(),
                              "sender", request.issuer().name(),
                              "target", punishment.getPlayerName(),
                              "id", punishment.getId()
        );
    }

    @Nullable
    private BroadcastSpec createSpec(@NotNull InternalPunishment punishment) {
        return switch (punishment) {
            case Punishment p -> switch (p.getType()) {
                case BAN ->
                        new BroadcastSpec(p.getEndTime() != null ? "tempban_broadcast" : "ban_broadcast", "dbans.notify.ban");
                case MUTE ->
                        new BroadcastSpec(p.getEndTime() != null ? "tempmute_broadcast" : "mute_broadcast", "dbans.notify.mute");
                case KICK -> new BroadcastSpec("kick_broadcast", "dbans.notify.kick");
                case IPBAN -> new BroadcastSpec("banip_broadcast", "dbans.notify.ipban");
                default -> null;
            };
            case JailPunishment jail -> new BroadcastSpec("jail_broadcast", "dbans.notify.jail");
            case Warning warning -> new BroadcastSpec("warn_broadcast", "dbans.notify.warning");
            default -> null;
        };
    }

    @Nullable
    private BroadcastSpec revokeSpec(@NotNull InternalPunishment punishment) {
        return switch (punishment) {
            case Punishment p -> switch (p.getType()) {
                case BAN -> new BroadcastSpec("unban_broadcast", "dbans.notify.unban");
                case MUTE -> new BroadcastSpec("unmute_broadcast", "dbans.notify.unmute");
                case IPBAN -> new BroadcastSpec("unbanip_broadcast", "dbans.notify.unbanip");
                default -> null;
            };
            case JailPunishment jail -> new BroadcastSpec("unjail_broadcast", "dbans.notify.unjail");
            case Warning warning -> new BroadcastSpec("unwarn_broadcast", "dbans.notify.unwarn");
            default -> null;
        };
    }

    private record BroadcastSpec(@NotNull String key, @NotNull String permission) {

    }
}

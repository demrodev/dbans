package me.demro.dbans.api.impl;

import lombok.RequiredArgsConstructor;
import me.demro.dbans.DBans;
import me.demro.dbans.model.InternalPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.Warning;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.UUID;

@RequiredArgsConstructor
class PunishmentEffectApplier {

    private final DBans plugin;

    void apply(@NotNull InternalPunishment punishment, @NotNull UUID targetUuid) {
        Player player = Bukkit.getPlayer(targetUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        switch (punishment) {
            case Punishment punishmentToApply -> applyPunishment(player, punishmentToApply);
            case Warning warning -> applyWarning(player, warning);
            default -> {
            }
        }
    }

    private void applyPunishment(@NotNull Player player, @NotNull Punishment punishment) {
        switch (punishment.getType()) {
            case BAN -> kickForBan(player, punishment);
            case MUTE -> notifyMute(player, punishment);
            case KICK -> kickWithMessage(player, punishment);
            case IPBAN -> kickForIpBan(player, punishment);
            default -> {
            }
        }
    }

    private void kickForBan(@NotNull Player player, @NotNull Punishment punishment) {
        String template = punishment.getEndTime() == null ? "ban_player" : "tempban_player";
        String raw = MessageUtil.getRawMessage(template);
        if (raw == null) {
            raw = punishment.getEndTime() == null
                    ? "&c✖ Вы были забанены навсегда.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%"
                    : "&c✖ Вы были забанены на %duration%.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
        }
        String msg = raw
                .replace("%reason%", punishment.getReason())
                .replace("%sender%", punishment.getIssuerName())
                .replace("%duration%", permanentOrFormatted(punishment))
                .replace("%server%", punishment.getServerName() != null ? punishment.getServerName() : "unknown")
                .replace("%id%", punishment.getId());
        player.kick(MessageUtil.deserializeForKick(msg));
    }

    private void notifyMute(@NotNull Player player, @NotNull Punishment punishment) {
        String key = punishment.getEndTime() == null ? "mute_player" : "tempmute_player";
        MessageUtil.send(player, key,
                         "sender", punishment.getIssuerName(),
                         "reason", punishment.getReason(),
                         "duration", permanentOrFormatted(punishment),
                         "server", punishment.getServerName(),
                         "id", punishment.getId()
        );
    }

    private void kickWithMessage(@NotNull Player player, @NotNull Punishment punishment) {
        String raw = MessageUtil.getRawMessage("kick_player");
        if (raw == null) {
            raw = "&c✖ Вас кикнули.\nПричина: %reason%\nАдминистратор: %sender%";
        }
        String msg = raw.replace("%reason%",
                                 punishment.getReason()
        ).replace("%sender%", punishment.getIssuerName());
        player.kick(MessageUtil.deserializeForKick(msg));
    }

    private void kickForIpBan(@NotNull Player player, @NotNull Punishment punishment) {
        InetSocketAddress address = player.getAddress();
        if (address == null) {
            return;
        }
        if (!plugin.getDatabase().isIpBanned(address.getAddress().getHostAddress())) {
            return;
        }
        String raw = MessageUtil.getRawMessage("banip_player");
        if (raw == null) {
            raw = "&c✖ Ваш IP-адрес заблокирован.\nПричина: %reason%\nАдминистратор: %sender%";
        }
        String msg = raw.replace("%reason%",
                                 punishment.getReason()
        ).replace("%sender%", punishment.getIssuerName());
        player.kick(MessageUtil.deserializeForKick(msg));
    }

    private void applyWarning(@NotNull Player player, @NotNull Warning warning) {
        String duration = warning.getEndTime() == null
                ? plugin.getConfig().getString("permanent_word", "навсегда")
                : TimeUtil.formatDuration(warning.getEndTime() - System.currentTimeMillis());
        MessageUtil.send(player, "warn_player",
                         "sender", warning.getIssuerName(),
                         "reason", warning.getReason(),
                         "duration", duration,
                         "server", warning.getServerName(),
                         "id", warning.getId()
        );
        plugin.getWarnManager().checkAndApplyThresholds(player);
    }

    private @NotNull String permanentOrFormatted(@NotNull Punishment punishment) {
        if (punishment.getEndTime() == null) {
            return plugin.getConfig().getString("permanent_word", "навсегда");
        }
        return TimeUtil.formatDuration(punishment.getEndTime() - System.currentTimeMillis());
    }
}

package me.demro.dBans.service;

import me.demro.dBans.DBans;
import me.demro.dBans.sync.ProxySyncManager;
import me.demro.dBans.api.adapter.PunishmentAdapter;
import me.demro.dBans.model.JailPunishment;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.model.Warning;
import me.demro.dBans.util.MessageUtil;
import me.demro.dBans.util.TimeUtil;
import me.demro.dlibs.api.EventManager;
import me.demro.dlibs.api.events.PunishmentCreateEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PunishService {
    private final DBans plugin;
    private final EventManager eventManager;

    public PunishService(DBans plugin) {
        this.plugin = plugin;
        this.eventManager = plugin.getEventManager();
    }

    public Punishment applyPunishment(UUID targetUuid, String targetName,
                                      UUID issuerUuid, String issuerName,
                                      PunishmentType type, String reason,
                                      Long duration, String server, boolean silent) {

        if (type == PunishmentType.WARNING) {
            Long endTime = (duration != null && duration > 0) ? System.currentTimeMillis() + duration : null;
            Warning warning = new Warning(targetUuid, targetName, issuerUuid, issuerName,
                    reason, System.currentTimeMillis(), endTime, server);
            plugin.getDatabase().saveWarning(warning);
            // Отправка через прокси для синхронизации
            if (plugin.getProxySyncManager() != null) {
                // Преобразуем Warning в Punishment для отправки
                Punishment p = new Punishment();
                p.setId(warning.getId());
                p.setPlayerUuid(warning.getPlayerUuid());
                p.setPlayerName(warning.getPlayerName());
                p.setIssuerUuid(warning.getIssuerUuid());
                p.setIssuerName(warning.getIssuerName());
                p.setType(PunishmentType.WARNING);
                p.setReason(warning.getReason());
                p.setStartTime(warning.getStartTime());
                p.setEndTime(warning.getEndTime());
                p.setActive(warning.isActive());
                p.setServerName(warning.getServerName());
                plugin.getProxySyncManager().sendPunishmentCreate(p);
            }

            Player online = Bukkit.getPlayer(targetUuid);
            if (online != null && online.isOnline()) {
                String durationStr = (duration != null && duration > 0) ? TimeUtil.formatDuration(duration) : "навсегда";
                MessageUtil.send(online, "warn_player",
                        "sender", issuerName,
                        "reason", reason,
                        "duration", durationStr,
                        "server", server,
                        "id", warning.getId());
                plugin.getWarnManager().checkAndApplyThresholds(online);
            }

            if (!silent) {
                String durationStr = (duration != null && duration > 0) ? TimeUtil.formatDuration(duration) : "навсегда";
                MessageUtil.broadcast("dbans.notify.warning", "warn_broadcast",
                        "sender", issuerName,
                        "target", targetName,
                        "reason", reason,
                        "duration", durationStr,
                        "server", server,
                        "id", warning.getId());
            }

            Punishment punishment = new Punishment();
            punishment.setId(warning.getId());
            punishment.setPlayerUuid(warning.getPlayerUuid());
            punishment.setPlayerName(warning.getPlayerName());
            punishment.setIssuerUuid(warning.getIssuerUuid());
            punishment.setIssuerName(warning.getIssuerName());
            punishment.setType(PunishmentType.WARNING);
            punishment.setReason(warning.getReason());
            punishment.setStartTime(warning.getStartTime());
            punishment.setEndTime(warning.getEndTime());
            punishment.setActive(warning.isActive());
            punishment.setServerName(warning.getServerName());

            try {
                if (eventManager != null) {
                    PunishmentCreateEvent event = new PunishmentCreateEvent(new PunishmentAdapter(punishment));
                    eventManager.callEvent(event);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to call PunishmentCreateEvent for warning: " + e.getMessage());
            }

            return punishment;
        }

        if (type == PunishmentType.JAIL) {
            Player online = Bukkit.getPlayer(targetUuid);
            if (online == null || !online.isOnline()) {
                plugin.getLogger().warning("Cannot jail offline player: " + targetName);
                return null;
            }

            Location previousLocation = online.getLocation().clone();
            String jailId = plugin.getJailManager().sendToJail(online, duration, previousLocation, issuerName, reason);
            if (jailId == null) {
                plugin.getLogger().warning("Failed to jail player: " + targetName);
                return null;
            }

            JailPunishment jail = plugin.getDatabase().getActiveJail(targetUuid);
            if (jail == null) {
                plugin.getLogger().warning("Jail not found in DB after creation for " + targetName);
                return null;
            }

            Punishment punishment = new Punishment();
            punishment.setId(jail.getId());
            punishment.setPlayerUuid(jail.getPlayerUuid());
            punishment.setPlayerName(jail.getPlayerName());
            punishment.setIssuerUuid(jail.getIssuerUuid());
            punishment.setIssuerName(jail.getIssuerName());
            punishment.setType(PunishmentType.JAIL);
            punishment.setReason(jail.getReason());
            punishment.setStartTime(jail.getStartTime());
            punishment.setEndTime(jail.getEndTime());
            punishment.setActive(jail.isActive());
            punishment.setServerName(jail.getServerName());

            try {
                if (eventManager != null) {
                    PunishmentCreateEvent event = new PunishmentCreateEvent(new PunishmentAdapter(punishment));
                    eventManager.callEvent(event);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to call PunishmentCreateEvent for jail: " + e.getMessage());
            }

            if (!silent) {
                String durationStr = (duration != null && duration > 0) ? TimeUtil.formatDuration(duration) : "навсегда";
                MessageUtil.broadcast("dbans.notify.jail", "jail_broadcast",
                        "sender", issuerName,
                        "target", targetName,
                        "reason", reason,
                        "duration", durationStr,
                        "server", server,
                        "id", jailId);
            }

            return punishment;
        }

        // ==================== Остальные типы (BAN, MUTE, KICK, IPBAN) ====================
        long start = System.currentTimeMillis();
        Long end = (duration != null && duration > 0) ? start + duration : null;
        Punishment punishment = new Punishment(targetUuid, targetName, issuerUuid, issuerName,
                type, reason, start, end, server);
        plugin.getDatabase().savePunishment(punishment);
        // Отправка через прокси для синхронизации
        if (plugin.getProxySyncManager() != null) {
            plugin.getProxySyncManager().sendPunishmentCreate(punishment);
        }

        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null && online.isOnline()) {
            applyEffects(online, punishment, duration);
        }

        if (!silent) {
            broadcastPunishment(punishment, issuerName, duration);
        }

        if (punishment.isActive()) {
            plugin.getCacheManager().cacheActivePunishment(targetUuid, type, server, plugin.getMode(), punishment);
            if (type == PunishmentType.MUTE && duration != null && duration > 0) {
                plugin.scheduleMuteExpiry(punishment);
            }
        }

        try {
            if (eventManager != null) {
                PunishmentCreateEvent event = new PunishmentCreateEvent(new PunishmentAdapter(punishment));
                eventManager.callEvent(event);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to call PunishmentCreateEvent: " + e.getMessage());
        }

        return punishment;
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    private void applyEffects(Player player, Punishment punishment, Long duration) {
        switch (punishment.getType()) {
            case BAN:
                if (punishment.getEndTime() == null) {
                    String kickMsg = MessageUtil.getRawMessage("ban_player");
                    if (kickMsg == null) kickMsg = "&c✖ Вы были забанены навсегда.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
                    kickMsg = kickMsg.replace("%reason%", punishment.getReason())
                            .replace("%sender%", punishment.getIssuerName())
                            .replace("%server%", punishment.getServerName())
                            .replace("%id%", punishment.getId());
                    player.kick(MessageUtil.deserializeForKick(kickMsg));
                } else {
                    String kickMsg = MessageUtil.getRawMessage("tempban_player");
                    if (kickMsg == null) kickMsg = "&c✖ Вы были забанены на %duration%.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
                    kickMsg = kickMsg.replace("%reason%", punishment.getReason())
                            .replace("%sender%", punishment.getIssuerName())
                            .replace("%duration%", TimeUtil.formatDuration(duration))
                            .replace("%server%", punishment.getServerName())
                            .replace("%id%", punishment.getId());
                    player.kick(MessageUtil.deserializeForKick(kickMsg));
                }
                break;
            case MUTE:
                if (punishment.getEndTime() == null) {
                    MessageUtil.send(player, "mute_player",
                            "sender", punishment.getIssuerName(),
                            "reason", punishment.getReason(),
                            "server", punishment.getServerName(),
                            "id", punishment.getId());
                } else {
                    MessageUtil.send(player, "tempmute_player",
                            "sender", punishment.getIssuerName(),
                            "reason", punishment.getReason(),
                            "duration", TimeUtil.formatDuration(duration),
                            "server", punishment.getServerName(),
                            "id", punishment.getId());
                }
                break;
            case KICK:
                String kickMsg = MessageUtil.getRawMessage("kick_player");
                if (kickMsg == null) kickMsg = "&c✖ Вас кикнули.\nПричина: %reason%\nАдминистратор: %sender%";
                kickMsg = kickMsg.replace("%reason%", punishment.getReason())
                        .replace("%sender%", punishment.getIssuerName());
                player.kick(MessageUtil.deserializeForKick(kickMsg));
                break;
            default:
                break;
        }
    }

    private void broadcastPunishment(Punishment punishment, String issuerName, Long duration) {
        String key = null;
        String permission = null;
        boolean temp = punishment.getEndTime() != null;
        switch (punishment.getType()) {
            case BAN:
                key = temp ? "tempban_broadcast" : "ban_broadcast";
                permission = "dbans.notify.ban";
                break;
            case MUTE:
                key = temp ? "tempmute_broadcast" : "mute_broadcast";
                permission = "dbans.notify.mute";
                break;
            case KICK:
                key = "kick_broadcast";
                permission = "dbans.notify.kick";
                break;
            default:
                return;
        }
        String durationStr = duration != null && duration > 0 ? TimeUtil.formatDuration(duration) : "навсегда";
        MessageUtil.broadcast(permission, key,
                "sender", issuerName,
                "target", punishment.getPlayerName(),
                "reason", punishment.getReason(),
                "duration", durationStr,
                "server", punishment.getServerName(),
                "id", punishment.getId());
    }
}
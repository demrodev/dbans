package me.demro.dbans.util;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.model.Warning;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WarnManager {
    private final DBans plugin;
    private final ConcurrentHashMap<UUID, Integer> warnCountCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> warnCountTimeCache = new ConcurrentHashMap<>();
    private final long cacheTtl = TimeUnit.SECONDS.toMillis(5);

    public WarnManager(DBans plugin) {
        this.plugin = plugin;
    }

    public void checkAndApplyThresholds(Player player) {
        int count = getActiveWarningCount(player.getUniqueId());
        String action = plugin.getConfig().getString("warn_thresholds." + count);
        if (action == null) return;

        String[] parts = action.split(" ", 2);
        String punishmentType = parts[0];
        String durationStr = parts.length > 1 ? parts[1] : null;
        long duration = 0;
        if (durationStr != null) {
            try {
                duration = TimeUtil.parseDuration(durationStr);
            } catch (IllegalArgumentException ignored) {}
        }

        String reasonRaw = MessageUtil.getRawMessage("auto_punishment_reason");
        if (reasonRaw == null) reasonRaw = "Автоматическое наказание за %count% предупреждений";
        String reason = reasonRaw.replace("%count%", String.valueOf(count));

        String issuerName = MessageUtil.getRawMessage("console_name");
        if (issuerName == null) issuerName = "Консоль";

        if (punishmentType.equalsIgnoreCase("kick") ||
                punishmentType.equalsIgnoreCase("ban") ||
                punishmentType.equalsIgnoreCase("tempban")) {
            applyPunishment(player, punishmentType, duration, reason, issuerName);
        } else {
            final Player finalPlayer = player;
            final String finalType = punishmentType;
            final long finalDuration = duration;
            final String finalReason = reason;
            final String finalIssuer = issuerName;
            Bukkit.getScheduler().runTask(plugin, () ->
                    applyPunishment(finalPlayer, finalType, finalDuration, finalReason, finalIssuer));
        }
    }

    public void applyPunishment(Player target, String punishmentType, long duration, String reason, String issuerName) {
        UUID issuerUuid = UUID.nameUUIDFromBytes("CONSOLE".getBytes());
        String serverName = plugin.getServerName();
        long startTime = System.currentTimeMillis();
        Long endTime = duration > 0 ? startTime + duration : null;
        Punishment punishment = null;

        switch (punishmentType.toLowerCase()) {
            case "ban":
                punishment = Punishment.builder()
                        .playerUuid(target.getUniqueId())
                        .playerName(target.getName())
                        .issuerUuid(issuerUuid)
                        .issuerName(issuerName)
                        .type(PunishmentType.BAN)
                        .reason(reason)
                        .startTime(startTime)
                        .endTime(null)
                        .active(true)
                        .serverName(serverName)
                        .build();
                plugin.getDatabase().savePunishment(punishment);
                if (target.isOnline()) {
                    String kickMsg = MessageUtil.getRawMessage("ban_player");
                    if (kickMsg == null) kickMsg = "&c✖ Вы были забанены навсегда.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%";
                    kickMsg = kickMsg.replace("%reason%", reason).replace("%sender%", issuerName).replace("%server%", serverName);
                    target.kickPlayer(MessageUtil.serializeForKick(kickMsg));
                }
                MessageUtil.broadcast("dbans.notify.ban", "ban_broadcast",
                        "sender", issuerName, "target", target.getName(), "reason", reason, "server", serverName);
                break;
            case "tempban":
                punishment = Punishment.builder()
                        .playerUuid(target.getUniqueId())
                        .playerName(target.getName())
                        .issuerUuid(issuerUuid)
                        .issuerName(issuerName)
                        .type(PunishmentType.BAN)
                        .reason(reason)
                        .startTime(startTime)
                        .endTime(endTime)
                        .active(true)
                        .serverName(serverName)
                        .build();
                plugin.getDatabase().savePunishment(punishment);
                if (target.isOnline()) {
                    String kickMsg = MessageUtil.getRawMessage("tempban_player");
                    if (kickMsg == null) kickMsg = "&c✖ Вы были забанены на %duration%.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%";
                    kickMsg = kickMsg.replace("%reason%", reason).replace("%sender%", issuerName)
                            .replace("%duration%", TimeUtil.formatDuration(duration)).replace("%server%", serverName);
                    target.kickPlayer(MessageUtil.serializeForKick(kickMsg));
                }
                MessageUtil.broadcast("dbans.notify.ban", "tempban_broadcast",
                        "sender", issuerName, "target", target.getName(), "reason", reason,
                        "duration", TimeUtil.formatDuration(duration), "server", serverName);
                break;
            case "mute":
                punishment = Punishment.builder()
                        .playerUuid(target.getUniqueId())
                        .playerName(target.getName())
                        .issuerUuid(issuerUuid)
                        .issuerName(issuerName)
                        .type(PunishmentType.MUTE)
                        .reason(reason)
                        .startTime(startTime)
                        .endTime(null)
                        .active(true)
                        .serverName(serverName)
                        .build();
                plugin.getDatabase().savePunishment(punishment);
                if (target.isOnline()) {
                    MessageUtil.send(target, "mute_player", "sender", issuerName, "reason", reason, "server", serverName);
                }
                MessageUtil.broadcast("dbans.notify.mute", "mute_broadcast",
                        "sender", issuerName, "target", target.getName(), "reason", reason, "server", serverName);
                break;
            case "tempmute":
                punishment = Punishment.builder()
                        .playerUuid(target.getUniqueId())
                        .playerName(target.getName())
                        .issuerUuid(issuerUuid)
                        .issuerName(issuerName)
                        .type(PunishmentType.MUTE)
                        .reason(reason)
                        .startTime(startTime)
                        .endTime(endTime)
                        .active(true)
                        .serverName(serverName)
                        .build();
                plugin.getDatabase().savePunishment(punishment);
                plugin.scheduleMuteExpiry(punishment);
                if (target.isOnline()) {
                    MessageUtil.send(target, "tempmute_player",
                            "sender", issuerName, "reason", reason,
                            "duration", TimeUtil.formatDuration(duration), "server", serverName);
                }
                MessageUtil.broadcast("dbans.notify.mute", "tempmute_broadcast",
                        "sender", issuerName, "target", target.getName(), "reason", reason,
                        "duration", TimeUtil.formatDuration(duration), "server", serverName);
                break;
            case "kick":
                punishment = Punishment.builder()
                        .playerUuid(target.getUniqueId())
                        .playerName(target.getName())
                        .issuerUuid(issuerUuid)
                        .issuerName(issuerName)
                        .type(PunishmentType.KICK)
                        .reason(reason)
                        .startTime(startTime)
                        .endTime(null)
                        .active(true)
                        .serverName(serverName)
                        .build();
                plugin.getDatabase().savePunishment(punishment);
                if (target.isOnline()) {
                    String kickMsg = MessageUtil.getRawMessage("kick_player");
                    if (kickMsg == null) kickMsg = "&c✖ Вас кикнули.\nПричина: %reason%\nАдминистратор: %sender%";
                    kickMsg = kickMsg.replace("%reason%", reason).replace("%sender%", issuerName);
                    target.kickPlayer(MessageUtil.serializeForKick(kickMsg));
                }
                MessageUtil.broadcast("dbans.notify.kick", "kick_broadcast",
                        "sender", issuerName, "target", target.getName(), "reason", reason, "server", serverName);
                break;
            case "jail":
                plugin.getJailManager().teleportToJail(target,
                        duration > 0 ? duration : null,
                        target.getLocation(),
                        issuerName,
                        reason);
                break;
            default:
                break;
        }
        invalidateCache(target.getUniqueId());
    }

    public int getActiveWarningCount(UUID playerUuid) {
        Long cacheTime = warnCountTimeCache.get(playerUuid);
        if (cacheTime != null && System.currentTimeMillis() - cacheTime < cacheTtl) {
            Integer count = warnCountCache.get(playerUuid);
            if (count != null) return count;
        }
        List<Warning> warnings = plugin.getDatabase().getActiveWarnings(playerUuid);
        int count = warnings.size();
        warnCountCache.put(playerUuid, count);
        warnCountTimeCache.put(playerUuid, System.currentTimeMillis());
        return count;
    }

    public void invalidateCache(UUID playerUuid) {
        warnCountCache.remove(playerUuid);
        warnCountTimeCache.remove(playerUuid);
    }

    public void clearCache() {
        warnCountCache.clear();
        warnCountTimeCache.clear();
    }
}
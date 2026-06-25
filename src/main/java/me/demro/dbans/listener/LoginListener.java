package me.demro.dbans.listener;

import me.demro.dbans.DBans;
import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.PlayerInfo;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.model.Warning;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LoginListener implements Listener {
    private final DBans plugin;

    public LoginListener(DBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = player.getAddress().getAddress().getHostAddress();
        PlayerInfo playerInfo = new PlayerInfo(player.getUniqueId(), player.getName(), ip, System.currentTimeMillis());

        // Асинхронное сохранение игрока
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabase().savePlayer(playerInfo));

        // Уведомление о глобальном муте (с задержкой, чтобы игрок успел залогиниться)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Punishment globalMute = getGlobalActiveMute(player.getUniqueId());
                if (globalMute != null && !globalMute.isExpired()) {
                    sendMuteNotification(player, globalMute);
                    plugin.scheduleMuteExpiry(globalMute);
                    if (plugin.getPunishmentSyncManager() != null) {
                        plugin.getPunishmentSyncManager().markMuteNotified(player.getUniqueId(), globalMute.getId());
                    }
                }
            }
        }, 20L);

        // Проверка истекших наказаний во время оффлайн (с задержкой)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                checkAndExpirePunishments(player);
            }
        }, 40L);

        // Отправка уведомлений, накопленных в БД (с задержкой)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                List<Map<String, String>> notifications = plugin.getDatabase().getAndClearNotifications(player.getUniqueId());
                for (Map<String, String> notif : notifications) {
                    String key = notif.remove("key");
                    if (key != null) {
                        MessageUtil.send(player, key, notif);
                    }
                }
            }
        }, 60L);
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String ip = event.getAddress().getHostAddress();
        UUID uuid = player.getUniqueId();

        // ===== IP-бан (синхронно, но через кэш) =====
        if (plugin.getCacheManager().isIpBannedCached(ip)) {
            String rawMessage = MessageUtil.getRawMessage("banip_player");
            if (rawMessage == null) rawMessage = "&c✖ Ваш IP-адрес заблокирован.\nПричина: %reason%\nАдминистратор: %sender%";
            rawMessage = rawMessage.replace("%reason%", "IP-бан").replace("%sender%", "Консоль");
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, MessageUtil.deserializeForKick(rawMessage));
            return;
        }

        // ===== GeoIP фильтрация (можно выполнять синхронно, т.к. это fast) =====
        if (plugin.getGeoIpManager().isReady()) {
            String countryCode = plugin.getGeoIpManager().getCountryCode(ip);
            String countryName = plugin.getGeoIpManager().getCountryName(ip);
            if (countryCode != null || countryName != null) {
                List<String> whitelist = plugin.getConfig().getStringList("geoip.whitelist");
                List<String> blacklist = plugin.getConfig().getStringList("geoip.blacklist");
                boolean allowed = true;
                if (!whitelist.isEmpty()) {
                    allowed = whitelist.stream().anyMatch(codeOrName ->
                            (countryCode != null && codeOrName.equalsIgnoreCase(countryCode)) ||
                                    (countryName != null && codeOrName.equalsIgnoreCase(countryName)));
                } else if (!blacklist.isEmpty()) {
                    allowed = !blacklist.stream().anyMatch(codeOrName ->
                            (countryCode != null && codeOrName.equalsIgnoreCase(countryCode)) ||
                                    (countryName != null && codeOrName.equalsIgnoreCase(countryName)));
                }
                if (!allowed) {
                    String display = (countryCode != null) ? countryCode : (countryName != null ? countryName : "Unknown");
                    String msg = plugin.getConfig().getString("geoip.block_message", "{prefix}&cДоступ с вашей страны (%country%) запрещён.");
                    msg = msg.replace("%country%", display);
                    event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, MessageUtil.deserializeForKick(msg));
                    return;
                }
            }
        }

        // ===== Бан (синхронно, но через кэш) =====
        Punishment ban = plugin.getCacheManager().getCachedActivePunishment(
                uuid, PunishmentType.BAN, plugin.getServerName(), plugin.getMode()
        );
        if (ban == null) {
            // Если в кэше нет, делаем синхронный запрос к БД (но это происходит редко, т.к. кэш живёт 30-60 секунд)
            ban = plugin.getDatabase().getActivePunishment(uuid, PunishmentType.BAN, plugin.getServerName(), plugin.getMode());
            if (ban != null) {
                plugin.getCacheManager().cacheActivePunishment(uuid, PunishmentType.BAN, plugin.getServerName(), plugin.getMode(), ban);
            }
        }
        if (ban != null && !ban.isExpired()) {
            String msgKey = ban.getEndTime() == null ? "ban_player" : "tempban_player";
            String rawMessage = MessageUtil.getRawMessage(msgKey);
            if (rawMessage == null) {
                rawMessage = ban.getEndTime() == null ?
                        "&c✖ Вы были забанены навсегда.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%" :
                        "&c✖ Вы были забанены на %duration%.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
            }
            String duration = (ban.getEndTime() == null) ? plugin.getConfig().getString("permanent_word", "навсегда") : TimeUtil.formatDuration(ban.getEndTime() - System.currentTimeMillis());
            rawMessage = rawMessage.replace("%reason%", ban.getReason())
                    .replace("%sender%", ban.getIssuerName())
                    .replace("%duration%", duration)
                    .replace("%server%", ban.getServerName() != null ? ban.getServerName() : "unknown")
                    .replace("%id%", ban.getId());
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, MessageUtil.deserializeForKick(rawMessage));
        }
    }

    private Punishment getGlobalActiveMute(UUID uuid) {
        return plugin.getDatabase().getActivePunishment(uuid, PunishmentType.MUTE, null, "sync");
    }

    private void sendMuteNotification(Player player, Punishment mute) {
        boolean isTemp = mute.getEndTime() != null;
        String key = isTemp ? "tempmute_player" : "mute_player";
        String durationStr = mute.getEndTime() == null
                ? plugin.getConfig().getString("permanent_word", "навсегда")
                : TimeUtil.formatDuration(mute.getEndTime() - System.currentTimeMillis());
        MessageUtil.send(player, key,
                "sender", mute.getIssuerName(),
                "reason", mute.getReason(),
                "duration", durationStr,
                "server", mute.getServerName(),
                "id", mute.getId());
    }

    private void checkAndExpirePunishments(Player player) {
        UUID uuid = player.getUniqueId();
        long lastSeen = plugin.getDatabase().getLastSeen(uuid);
        if (lastSeen <= 0) return;

        long now = System.currentTimeMillis();

        // Обычные наказания
        List<Punishment> allPunishments = plugin.getDatabase().getPunishmentHistory(uuid, true);
        for (Punishment p : allPunishments) {
            if (p.getEndTime() != null && p.getEndTime() > lastSeen && p.getEndTime() <= now) {
                if (p.isActive()) {
                    p.setActive(false);
                    plugin.getDatabase().updatePunishment(p);
                    plugin.getLogger().info("🔄 [Expire] Deactivated expired punishment " + p.getId() + " for " + player.getName());
                }
                sendOfflineExpireNotification(player, p);
            }
        }

        // Jail
        List<JailPunishment> allJails = plugin.getDatabase().getAllJailsForPlayer(uuid);
        for (JailPunishment j : allJails) {
            if (j.getEndTime() != null && j.getEndTime() > lastSeen && j.getEndTime() <= now) {
                if (j.isActive()) {
                    j.setActive(false);
                    plugin.getDatabase().updateJail(j);
                    plugin.getLogger().info("🔄 [Expire] Deactivated expired jail " + j.getId() + " for " + player.getName());
                }
                sendOfflineExpireNotification(player, j);
            }
        }

        // Warnings
        List<Warning> allWarnings = plugin.getDatabase().getAllWarningsForPlayer(uuid);
        for (Warning w : allWarnings) {
            if (w.getEndTime() != null && w.getEndTime() > lastSeen && w.getEndTime() <= now) {
                if (w.isActive()) {
                    w.setActive(false);
                    plugin.getDatabase().updateWarning(w);
                    plugin.getLogger().info("🔄 [Expire] Deactivated expired warning " + w.getId() + " for " + player.getName());
                }
                sendOfflineExpireNotification(player, w);
            }
        }
    }

    private void sendOfflineExpireNotification(Player player, Punishment p) {
        String typeKey = p.getType().name().toLowerCase();
        MessageUtil.send(player, "expire_offline_" + typeKey,
                "id", p.getId(),
                "duration", TimeUtil.formatDuration(p.getEndTime() - p.getStartTime()));
    }

    private void sendOfflineExpireNotification(Player player, JailPunishment j) {
        MessageUtil.send(player, "expire_offline_jail",
                "id", j.getId(),
                "duration", TimeUtil.formatDuration(j.getEndTime() - j.getStartTime()));
    }

    private void sendOfflineExpireNotification(Player player, Warning w) {
        MessageUtil.send(player, "expire_offline_warning",
                "id", w.getId(),
                "duration", TimeUtil.formatDuration(w.getEndTime() - w.getStartTime()));
    }
}
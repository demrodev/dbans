package me.demro.dbans.listener;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.PlayerInfo;
import me.demro.dbans.model.Punishment;
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
import java.util.concurrent.CompletableFuture;

@Slf4j
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

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabase().savePlayer(playerInfo));
        plugin.getPlayerCache().update(player);

        // Уведомление о глобальном муте – через новый API
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                UUID uuid = player.getUniqueId();
                // Проверяем активный мут через API
                CompletableFuture<Boolean> hasMuteFuture = plugin.getApi().punishments().hasActive(uuid, me.demro.dlibs.dbans.api.punishment.PunishmentType.MUTE);
                if (hasMuteFuture.join()) {
                    // Найдём сам мут, чтобы отправить детали
                    CompletableFuture<List<me.demro.dlibs.dbans.api.punishment.Punishment>> muteListFuture = plugin.getApi().punishments().findActiveByTarget(uuid);
                    List<me.demro.dlibs.dbans.api.punishment.Punishment> mutes = muteListFuture.join();
                    if (!mutes.isEmpty()) {
                        me.demro.dlibs.dbans.api.punishment.Punishment mute = mutes.get(0);
                        sendMuteNotification(player, mute);
                        // Планируем истечение, если временный
                        if (!mute.isPermanent()) {
                            // Планируем через внутренний менеджер
                            Punishment internalMute = plugin.getDatabase().getPunishmentById(mute.id().value());
                            if (internalMute != null && internalMute.getEndTime() != null) {
                                plugin.scheduleMuteExpiry(internalMute);
                            }
                        }
                    }
                }
            }
        }, 20L);

        // Проверка истекших наказаний во время оффлайн
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                checkAndExpirePunishments(player);
            }
        }, 40L);

        // Получение уведомлений
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                List<Map<String, String>> notifications = plugin.getAndClearNotifications(player.getUniqueId());
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

        // IP-бан – через старый метод, т.к. в API нет прямого метода для IP-бана
        if (plugin.getDatabase().isIpBanned(ip)) {
            String rawMessage = MessageUtil.getRawMessage("banip_player");
            if (rawMessage == null)
                rawMessage = "&c✖ Ваш IP-адрес заблокирован.\nПричина: %reason%\nАдминистратор: %sender%";
            rawMessage = rawMessage.replace("%reason%", "IP-бан").replace("%sender%", "Консоль");
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, MessageUtil.deserializeForKick(rawMessage));
            return;
        }

        // GeoIP фильтрация (без изменений)
        if (plugin.getGeoIpManager().isReady()) {
            String countryCode = plugin.getGeoIpManager().getCountryCode(ip);
            String countryName = plugin.getGeoIpManager().getCountryName(ip);
            if (countryCode != null || countryName != null) {
                List<String> whitelist = plugin.getConfig().getStringList("geoip.whitelist");
                List<String> blacklist = plugin.getConfig().getStringList("geoip.blacklist");
                boolean allowed = true;
                if (!whitelist.isEmpty()) {
                    allowed = whitelist.stream().anyMatch(codeOrName ->
                                                                  (codeOrName.equalsIgnoreCase(countryCode)) ||
                                                                  (codeOrName.equalsIgnoreCase(countryName)));
                } else if (!blacklist.isEmpty()) {
                    allowed = !blacklist.stream().anyMatch(codeOrName ->
                                                                   (codeOrName.equalsIgnoreCase(countryCode)) ||
                                                                   (codeOrName.equalsIgnoreCase(countryName)));
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

        // Бан – через новый API
        CompletableFuture<Boolean> hasBanFuture = plugin.getApi().punishments().hasActive(uuid, me.demro.dlibs.dbans.api.punishment.PunishmentType.BAN);
        if (hasBanFuture.join()) {
            // Найдём сам бан
            CompletableFuture<List<me.demro.dlibs.dbans.api.punishment.Punishment>> banListFuture = plugin.getApi().punishments().findActiveByTarget(uuid);
            List<me.demro.dlibs.dbans.api.punishment.Punishment> bans = banListFuture.join();
            if (!bans.isEmpty()) {
                me.demro.dlibs.dbans.api.punishment.Punishment ban = bans.get(0);
                String msgKey = ban.isPermanent() ? "ban_player" : "tempban_player";
                String rawMessage = MessageUtil.getRawMessage(msgKey);
                if (rawMessage == null) {
                    rawMessage = ban.isPermanent() ?
                            "&c✖ Вы были забанены навсегда.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%" :
                            "&c✖ Вы были забанены на %duration%.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
                }
                String duration = ban.isPermanent() ?
                        plugin.getConfig().getString("permanent_word", "навсегда") :
                        TimeUtil.formatDuration(ban.expiresAt().get().toEpochMilli() - System.currentTimeMillis());
                rawMessage = rawMessage.replace("%reason%", ban.reason().value())
                                       .replace("%sender%", ban.issuer().name())
                                       .replace("%duration%", duration)
                                       .replace("%server%", ban.serverName())
                                       .replace("%id%", ban.shortId());
                event.disallow(PlayerLoginEvent.Result.KICK_BANNED, MessageUtil.deserializeForKick(rawMessage));
            }
        }
    }

    private void sendMuteNotification(Player player, me.demro.dlibs.dbans.api.punishment.Punishment mute) {
        boolean isTemp = !mute.isPermanent();
        String key = isTemp ? "tempmute_player" : "mute_player";
        String durationStr = mute.isPermanent()
                ? plugin.getConfig().getString("permanent_word", "навсегда")
                : TimeUtil.formatDuration(mute.expiresAt().get().toEpochMilli() - System.currentTimeMillis());
        MessageUtil.send(player, key,
                         "sender", mute.issuer().name(),
                         "reason", mute.reason().value(),
                         "duration", durationStr,
                         "server", mute.serverName(),
                         "id", mute.shortId());
    }

    private void checkAndExpirePunishments(Player player) {
        UUID uuid = player.getUniqueId();
        long lastSeen = plugin.getDatabase().getLastSeen(uuid);
        if (lastSeen <= 0) return;

        long now = System.currentTimeMillis();

        // Используем новый API для поиска всех активных наказаний
        CompletableFuture<List<me.demro.dlibs.dbans.api.punishment.Punishment>> activeFuture = plugin.getApi().punishments().findActiveByTarget(uuid);
        List<me.demro.dlibs.dbans.api.punishment.Punishment> active = activeFuture.join();
        for (me.demro.dlibs.dbans.api.punishment.Punishment p : active) {
            if (p.expiresAt().isPresent() && p.expiresAt().get().toEpochMilli() > lastSeen && p.expiresAt().get().toEpochMilli() <= now) {
                sendOfflineExpireNotification(player, p);
            }
        }

        // Варны – через API
        CompletableFuture<List<me.demro.dlibs.dbans.api.punishment.Punishment>> warningsFuture = plugin.getApi().punishments().find(
                me.demro.dlibs.dbans.api.punishment.PunishmentQuery.builder()
                                                                   .targetUuid(uuid)
                                                                   .type(me.demro.dlibs.dbans.api.punishment.PunishmentType.WARNING)
                                                                   .status(me.demro.dlibs.dbans.api.punishment.PunishmentStatus.ACTIVE)
                                                                   .build()
        );
        List<me.demro.dlibs.dbans.api.punishment.Punishment> warnings = warningsFuture.join();
        for (me.demro.dlibs.dbans.api.punishment.Punishment w : warnings) {
            if (w.expiresAt().isPresent() && w.expiresAt().get().toEpochMilli() > lastSeen && w.expiresAt().get().toEpochMilli() <= now) {
                sendOfflineExpireNotification(player, w);
            }
        }

        // Джейлы – через API
        CompletableFuture<List<me.demro.dlibs.dbans.api.punishment.Punishment>> jailsFuture = plugin.getApi().punishments().find(
                me.demro.dlibs.dbans.api.punishment.PunishmentQuery.builder()
                                                                   .targetUuid(uuid)
                                                                   .type(me.demro.dlibs.dbans.api.punishment.PunishmentType.JAIL)
                                                                   .status(me.demro.dlibs.dbans.api.punishment.PunishmentStatus.ACTIVE)
                                                                   .build()
        );
        List<me.demro.dlibs.dbans.api.punishment.Punishment> jails = jailsFuture.join();
        for (me.demro.dlibs.dbans.api.punishment.Punishment j : jails) {
            if (j.expiresAt().isPresent() && j.expiresAt().get().toEpochMilli() > lastSeen && j.expiresAt().get().toEpochMilli() <= now) {
                sendOfflineExpireNotification(player, j);
            }
        }
    }

    private void sendOfflineExpireNotification(Player player, me.demro.dlibs.dbans.api.punishment.Punishment p) {
        String typeKey = p.type().name().toLowerCase();
        String duration = p.expiresAt().isPresent() ?
                TimeUtil.formatDuration(p.expiresAt().get().toEpochMilli() - p.createdAt().toEpochMilli()) :
                "навсегда";
        MessageUtil.send(player, "expire_offline_" + typeKey,
                         "id", p.shortId(),
                         "duration", duration);
    }
}
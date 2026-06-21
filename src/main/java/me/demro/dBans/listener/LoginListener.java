package me.demro.dBans.listener;

import me.demro.dBans.DBans;
import me.demro.dBans.model.PlayerInfo;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.util.MessageUtil;
import me.demro.dBans.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.List;
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

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabase().savePlayer(playerInfo));

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
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        String ip = event.getAddress().getHostAddress();

        if (plugin.getDatabase().isIpBanned(ip)) {
            String rawMessage = MessageUtil.getRawMessage("banip_player");
            if (rawMessage == null) rawMessage = "&c✖ Ваш IP-адрес заблокирован.\nПричина: %reason%\nАдминистратор: %sender%";
            rawMessage = rawMessage.replace("%reason%", "IP-бан").replace("%sender%", "Консоль");
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, MessageUtil.deserializeForKick(rawMessage));
            return;
        }

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

        Punishment ban = plugin.getDatabase().getActivePunishment(event.getPlayer().getUniqueId(), PunishmentType.BAN,
                plugin.getServerName(), plugin.getMode());
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
}
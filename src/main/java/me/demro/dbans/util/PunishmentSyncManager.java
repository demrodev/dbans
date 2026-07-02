package me.demro.dbans.util;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class PunishmentSyncManager {

    private final DBans plugin;
    private final Map<UUID, String> lastBanId = new HashMap<>();
    private final Map<UUID, String> lastMuteId = new HashMap<>();
    private final Map<UUID, String> lastGlobalMuteId = new HashMap<>();

    public PunishmentSyncManager(DBans plugin) {
        this.plugin = plugin;
        startSync();
    }

    private void startSync() {
        int interval = plugin.getConfig().getInt("sync.punishment_sync_interval_seconds", 2);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkBan(player);
                    checkMuteForChat(player);
                    checkGlobalMuteForNotification(player);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, interval * 20L);
    }

    private void checkBan(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        Punishment ban = plugin.getDatabase().getActivePunishment(uuid, PunishmentType.BAN,
                                                                  plugin.getServerName(), plugin.getMode());
        if (ban != null && !ban.isExpired()) {
            String banId = ban.getId();
            if (!banId.equals(lastBanId.get(uuid))) {
                lastBanId.put(uuid, banId);
                Bukkit.getScheduler().runTask(plugin, () -> kickForBan(player, ban));
            }
        } else {
            lastBanId.remove(uuid);
        }
    }

    private void kickForBan(@NotNull Player player, Punishment ban) {
        if (!player.isOnline()) return;
        if (plugin.getMode().equalsIgnoreCase("sync_static") &&
            !ban.getServerName().equals(plugin.getServerName())) {
            return;
        }
        boolean isTemp = ban.getEndTime() != null;
        String key = isTemp ? "tempban_player" : "ban_player";
        String kickMsg = MessageUtil.getRawMessage(key);
        if (kickMsg == null) {
            kickMsg = isTemp
                    ? "&c✖ Вы были забанены на %duration%.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%"
                    : "&c✖ Вы были забанены навсегда.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
        }
        String duration = ban.getEndTime() == null
                ? plugin.getConfig().getString("permanent_word", "навсегда")
                : TimeUtil.formatDuration(ban.getEndTime() - System.currentTimeMillis());
        kickMsg = kickMsg.replace("%reason%", ban.getReason())
                         .replace("%sender%", ban.getIssuerName())
                         .replace("%duration%", duration)
                         .replace("%server%", ban.getServerName() != null ? ban.getServerName() : "unknown")
                         .replace("%id%", ban.getId());
        Component kickComponent = MessageUtil.deserializeForKick(kickMsg);
        player.kick(kickComponent);
    }

    private void checkMuteForChat(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        Punishment mute = plugin.getDatabase().getActivePunishment(uuid, PunishmentType.MUTE,
                                                                   plugin.getServerName(), plugin.getMode());
        if (mute != null && !mute.isExpired()) {
            String muteId = mute.getId();
            if (!muteId.equals(lastMuteId.get(uuid))) {
                lastMuteId.put(uuid, muteId);
                plugin.scheduleMuteExpiry(mute);
            }
        } else {
            lastMuteId.remove(uuid);
        }
    }

    private void checkGlobalMuteForNotification(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        Punishment globalMute = getGlobalActiveMute(uuid);
        boolean hasGlobalMute = (globalMute != null && !globalMute.isExpired());
        String globalMuteId = hasGlobalMute ? globalMute.getId() : null;
        String lastGlobalId = lastGlobalMuteId.get(uuid);

        if (hasGlobalMute && !globalMuteId.equals(lastGlobalId)) {
            lastGlobalMuteId.put(uuid, globalMuteId);
            Bukkit.getScheduler().runTask(plugin, () -> notifyMute(player, globalMute));
        } else if (!hasGlobalMute && lastGlobalId != null) {
            lastGlobalMuteId.remove(uuid);
        }
    }

    private Punishment getGlobalActiveMute(UUID uuid) {
        return plugin.getDatabase().getActivePunishment(uuid, PunishmentType.MUTE, null, "sync");
    }

    private void notifyMute(@NotNull Player player, Punishment mute) {
        if (!player.isOnline()) return;
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

    public void markMuteNotified(UUID playerUuid, String muteId) {
        lastGlobalMuteId.put(playerUuid, muteId);
    }
}
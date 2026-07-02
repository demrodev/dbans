package me.demro.dbans.sync;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.model.Warning;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class ProxySyncManager implements PluginMessageListener {

    private final DBans plugin;
    private final Gson gson = new Gson();
    private boolean processingIncoming = false;

    public ProxySyncManager(DBans plugin) {
        this.plugin = plugin;
    }

    public boolean isSyncEnabled() {
        String mode = plugin.getMode();
        return mode.equalsIgnoreCase("sync") || mode.equalsIgnoreCase("sync_static");
    }

    private void sendMessage(SyncMessage msg) {
        if (!isSyncEnabled()) {
            log.info("⏭️ Sync disabled, not sending {}", msg.getType());
            return;
        }
        String json = gson.toJson(msg);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        log.info("📤 [Sync] Sending {} | ID: {} | JSON: {}", msg.getType(), msg.getData().get("id"), json);
        Bukkit.getServer().sendPluginMessage(plugin, Constants.CHANNEL_NAME, data);
    }

    public void sendPunishmentCreate(Punishment punishment) {
        Map<String, Object> data = punishmentToMap(punishment);
        sendMessage(new SyncMessage("punishment_create", data));
    }

    public void sendPunishmentRevoke(Punishment punishment) {
        Map<String, Object> data = punishmentToMap(punishment);
        sendMessage(new SyncMessage("punishment_revoke", data));
    }

    public void sendPunishmentModify(Punishment punishment, String oldReason, Long oldEnd) {
        Map<String, Object> data = punishmentToMap(punishment);
        if (oldReason != null) data.put("oldReason", oldReason);
        if (oldEnd != null) data.put("oldEnd", oldEnd);
        sendMessage(new SyncMessage("punishment_modify", data));
    }

    public void sendPunishmentExpire(Punishment punishment) {
        Map<String, Object> data = punishmentToMap(punishment);
        sendMessage(new SyncMessage("punishment_expire", data));
    }

    public void sendPunishmentCreate(JailPunishment jail) {
        sendPunishmentCreate(jailToPunishment(jail));
    }

    public void sendPunishmentRevoke(JailPunishment jail) {
        sendPunishmentRevoke(jailToPunishment(jail));
    }

    public void sendPunishmentCreate(Warning warning) {
        sendPunishmentCreate(warningToPunishment(warning));
    }

    public void sendPunishmentRevoke(Warning warning) {
        sendPunishmentRevoke(warningToPunishment(warning));
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!channel.equals(Constants.CHANNEL_NAME)) return;
        if (processingIncoming) return;

        try {
            String json = new String(message, StandardCharsets.UTF_8);
            SyncMessage msg = gson.fromJson(json, SyncMessage.class);
            log.info("📥 [Sync] Received {} | ID: {}", msg.getType(), msg.getData().get("id"));

            processingIncoming = true;
            handleIncomingMessage(msg);
            processingIncoming = false;
        } catch (Exception e) {
            log.warn("❌ [Sync] Failed to process incoming message: {}", e.getMessage(), e);
        }
    }

    private void handleIncomingMessage(@NotNull SyncMessage msg) {
        String type = msg.getType();
        Map<String, Object> data = msg.getData();

        String id = (String) data.get("id");
        UUID playerUuid = UUID.fromString((String) data.get("playerUuid"));
        String playerName = (String) data.get("playerName");
        UUID issuerUuid = UUID.fromString((String) data.get("issuerUuid"));
        String issuerName = (String) data.get("issuerName");
        PunishmentType pType = PunishmentType.valueOf((String) data.get("type"));
        String reason = (String) data.get("reason");
        long startTime = ((Number) data.get("startTime")).longValue();
        Long endTime = data.get("endTime") != null ? ((Number) data.get("endTime")).longValue() : null;
        boolean active = (Boolean) data.get("active");
        String serverName = (String) data.get("serverName");
        String pardonReason = (String) data.get("pardonReason");
        String pardonedBy = (String) data.get("pardonedBy");
        Long pardonedAt = data.get("pardonedAt") != null ? ((Number) data.get("pardonedAt")).longValue() : null;

        Punishment punishment = Punishment.builder()
                                          .id(id)
                                          .playerUuid(playerUuid)
                                          .playerName(playerName)
                                          .issuerUuid(issuerUuid)
                                          .issuerName(issuerName)
                                          .type(pType)
                                          .reason(reason)
                                          .startTime(startTime)
                                          .endTime(endTime)
                                          .active(active)
                                          .serverName(serverName)
                                          .pardonReason(pardonReason)
                                          .pardonedBy(pardonedBy)
                                          .pardonedAt(pardonedAt)
                                          .build();

        if (pType == PunishmentType.WARNING) {
            handleWarning(type, punishment);
        } else if (pType == PunishmentType.JAIL) {
            handleJail(type, punishment);
        } else {
            handleDefault(type, punishment);
        }
    }

    private void handleDefault(String type, Punishment punishment) {
        if (!shouldApplyLocally(punishment)) {
            log.info("⏭️ [Sync] Skipping apply for server {}", plugin.getServerName());
            return;
        }

        if ("punishment_create".equals(type) || "punishment_modify".equals(type) || "punishment_expire".equals(type)) {
            switch (type) {
                case "punishment_create" -> {
                    Punishment existing = plugin.getDatabase().getPunishmentById(punishment.getId());
                    if (existing == null) {
                        plugin.getDatabase().savePunishment(punishment);
                    } else {
                        plugin.getDatabase().updatePunishment(punishment);
                    }
                }
                case "punishment_modify" -> {
                    plugin.getDatabase().updatePunishment(punishment);
                    if (punishment.getType() == PunishmentType.MUTE) {
                        plugin.cancelMuteExpiry(punishment.getId());
                        if (punishment.isActive() && punishment.getEndTime() != null && punishment.getEndTime() > System.currentTimeMillis()) {
                            plugin.scheduleMuteExpiry(punishment);
                        }
                    }
                }
                case "punishment_expire" -> {
                    punishment.setActive(false);
                    plugin.getDatabase().updatePunishment(punishment);
                    if (punishment.getType() == PunishmentType.MUTE) {
                        plugin.cancelMuteExpiry(punishment.getId());
                    }
                }
            }
        } else if ("punishment_revoke".equals(type)) {
            punishment.setActive(false);
            plugin.getDatabase().updatePunishment(punishment);
            if (punishment.getType() == PunishmentType.MUTE) {
                plugin.cancelMuteExpiry(punishment.getId());
            }
        }

        if (punishment.isActive() && !punishment.isExpired()) {
            applyLocalEffects(punishment);
        } else {
            removeLocalEffects(punishment);
        }
    }

    private void handleWarning(String type, Punishment punishment) {
        if (!shouldApplyLocally(punishment)) {
            log.info("⏭️ [Sync] Skipping warning apply for server {}", plugin.getServerName());
            return;
        }

        Warning warning = Warning.builder()
                                 .id(punishment.getId())
                                 .playerUuid(punishment.getPlayerUuid())
                                 .playerName(punishment.getPlayerName())
                                 .issuerUuid(punishment.getIssuerUuid())
                                 .issuerName(punishment.getIssuerName())
                                 .reason(punishment.getReason())
                                 .startTime(punishment.getStartTime())
                                 .endTime(punishment.getEndTime())
                                 .active(punishment.isActive())
                                 .serverName(punishment.getServerName())
                                 .pardonedBy(punishment.getPardonedBy())
                                 .pardonedAt(punishment.getPardonedAt())
                                 .build();

        if ("punishment_create".equals(type)) {
            Warning existing = plugin.getDatabase().getWarningById(warning.getId());
            if (existing == null) {
                plugin.getDatabase().saveWarning(warning);
            } else {
                plugin.getDatabase().updateWarning(warning);
            }
        } else if ("punishment_modify".equals(type) || "punishment_expire".equals(type)) {
            plugin.getDatabase().updateWarning(warning);
        } else if ("punishment_revoke".equals(type)) {
            warning.setActive(false);
            plugin.getDatabase().updateWarning(warning);
        }

        Player player = Bukkit.getPlayer(warning.getPlayerUuid());
        if (player != null && player.isOnline()) {
            if (warning.isActive() && !warning.isExpired()) {
                String duration = warning.getEndTime() == null ? "навсегда" : TimeUtil.formatDuration(warning.getEndTime() - System.currentTimeMillis());
                MessageUtil.send(player, "warn_player",
                                 "sender", warning.getIssuerName(),
                                 "reason", warning.getReason(),
                                 "duration", duration,
                                 "server", warning.getServerName(),
                                 "id", warning.getId());
                plugin.getWarnManager().checkAndApplyThresholds(player);
            } else {
                MessageUtil.send(player, "unwarn_notify", "issuer", warning.getPardonedBy(), "id", warning.getId());
            }
        }
    }

    private void handleJail(String type, Punishment punishment) {
        if (!shouldApplyLocally(punishment)) {
            log.info("⏭️ [Sync] Skipping jail apply for server {}", plugin.getServerName());
            return;
        }

        JailPunishment jail = JailPunishment.builder()
                                            .id(punishment.getId())
                                            .playerUuid(punishment.getPlayerUuid())
                                            .playerName(punishment.getPlayerName())
                                            .issuerUuid(punishment.getIssuerUuid())
                                            .issuerName(punishment.getIssuerName())
                                            .reason(punishment.getReason())
                                            .startTime(punishment.getStartTime())
                                            .endTime(punishment.getEndTime())
                                            .active(punishment.isActive())
                                            .serverName(punishment.getServerName())
                                            .pardonedBy(punishment.getPardonedBy())
                                            .pardonedAt(punishment.getPardonedAt())
                                            .build();

        if ("punishment_create".equals(type)) {
            plugin.getDatabase().saveJail(jail);
        } else if ("punishment_modify".equals(type) || "punishment_expire".equals(type)) {
            plugin.getDatabase().updateJail(jail);
        } else if ("punishment_revoke".equals(type)) {
            jail.setActive(false);
            plugin.getDatabase().updateJail(jail);
        }

        Player player = Bukkit.getPlayer(jail.getPlayerUuid());
        if (player != null && player.isOnline()) {
            if (jail.isActive() && !jail.isExpired()) {
                if (!player.getWorld().getName().equals("jail")) {
                    JailPunishment current = plugin.getDatabase().getActiveJail(jail.getPlayerUuid());
                    if (current != null && current.getJailLocation() != null) {
                        player.teleport(current.getJailLocation());
                    } else {
                        org.bukkit.World w = Bukkit.getWorld("jail");
                        if (w != null) player.teleport(w.getSpawnLocation());
                    }
                    plugin.getJailManager().handlePlayerJoin(player);
                }
                MessageUtil.send(player, "jail_sync_notify", "reason", jail.getReason(), "sender", jail.getIssuerName());
            } else {
                if (player.getWorld().getName().equals("jail")) {
                    JailPunishment current = plugin.getDatabase().getActiveJail(jail.getPlayerUuid());
                    if (current != null && current.getId().equals(jail.getId())) {
                        plugin.getJailManager().releaseFromJail(player, current);
                    }
                }
            }
        }
    }

    private boolean shouldApplyLocally(Punishment punishment) {
        String mode = plugin.getMode();
        if ("sync_static".equalsIgnoreCase(mode)) {
            String server = punishment.getServerName();
            return server == null || server.equals(plugin.getServerName());
        }
        return true;
    }

    private void notifyOrStore(UUID playerUuid, String messageKey, Map<String, String> placeholders) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            MessageUtil.send(player, messageKey, placeholders);
            log.info("📨 [Sync] Sent notification to {} (online)", player.getName());
        } else {
            plugin.addNotification(playerUuid, messageKey, placeholders);
            log.info("💾 [Sync] Stored notification for {} (offline)", playerUuid);
        }
    }

    private void applyLocalEffects(@NotNull Punishment punishment) {
        Player player = Bukkit.getPlayer(punishment.getPlayerUuid());
        if (player == null || !player.isOnline()) return;

        switch (punishment.getType()) {
            case BAN:
                if (punishment.isActive() && !punishment.isExpired()) {
                    String key = punishment.getEndTime() == null ? "ban_player" : "tempban_player";
                    String kickMsg = MessageUtil.getRawMessage(key);
                    if (kickMsg == null) {
                        kickMsg = punishment.getEndTime() == null ?
                                "&c✖ Вы были забанены навсегда.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%" :
                                "&c✖ Вы были забанены на %duration%.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
                    }
                    String duration = punishment.getEndTime() == null ?
                            plugin.getConfig().getString("permanent_word", "навсегда") :
                            TimeUtil.formatDuration(punishment.getEndTime() - System.currentTimeMillis());
                    kickMsg = kickMsg.replace("%reason%", punishment.getReason())
                                     .replace("%sender%", punishment.getIssuerName())
                                     .replace("%duration%", duration)
                                     .replace("%server%", punishment.getServerName() != null ? punishment.getServerName() : "unknown")
                                     .replace("%id%", punishment.getId());
                    player.kick(MessageUtil.deserializeForKick(kickMsg));
                }
                break;
            case MUTE:
                if (punishment.isActive() && !punishment.isExpired()) {
                    String key = punishment.getEndTime() == null ? "mute_player" : "tempmute_player";
                    String duration = punishment.getEndTime() == null ?
                            plugin.getConfig().getString("permanent_word", "навсегда") :
                            TimeUtil.formatDuration(punishment.getEndTime() - System.currentTimeMillis());
                    MessageUtil.send(player, key,
                                     "sender", punishment.getIssuerName(),
                                     "reason", punishment.getReason(),
                                     "duration", duration,
                                     "server", punishment.getServerName(),
                                     "id", punishment.getId());
                    if (punishment.getEndTime() != null) {
                        plugin.scheduleMuteExpiry(punishment);
                    }
                }
                break;
            case KICK:
                if (punishment.isActive()) {
                    String kickMsg = MessageUtil.getRawMessage("kick_player");
                    if (kickMsg == null) kickMsg = "&c✖ Вас кикнули.\nПричина: %reason%\nАдминистратор: %sender%";
                    kickMsg = kickMsg.replace("%reason%", punishment.getReason())
                                     .replace("%sender%", punishment.getIssuerName());
                    player.kick(MessageUtil.deserializeForKick(kickMsg));
                }
                break;
            case IPBAN:
                String ip = player.getAddress().getAddress().getHostAddress();
                if (plugin.getDatabase().isIpBanned(ip)) {
                    String kickMsg = MessageUtil.getRawMessage("banip_player");
                    if (kickMsg == null)
                        kickMsg = "&c✖ Ваш IP-адрес заблокирован.\nПричина: %reason%\nАдминистратор: %sender%";
                    kickMsg = kickMsg.replace("%reason%", punishment.getReason())
                                     .replace("%sender%", punishment.getIssuerName());
                    player.kick(MessageUtil.deserializeForKick(kickMsg));
                }
                break;
            default:
                break;
        }
    }

    private void removeLocalEffects(@NotNull Punishment punishment) {
        Player player = Bukkit.getPlayer(punishment.getPlayerUuid());
        if (player == null || !player.isOnline()) return;

        switch (punishment.getType()) {
            case MUTE:
                plugin.cancelMuteExpiry(punishment.getId());
                if ("punishment_revoke".equals(punishment.getType().name())) {
                    MessageUtil.send(player, "unmute_notify", "sender", punishment.getPardonedBy());
                }
                break;
            case JAIL:
                break;
            default:
                break;
        }
    }

    private @NotNull Map<String, Object> punishmentToMap(@NotNull Punishment p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("playerUuid", p.getPlayerUuid().toString());
        map.put("playerName", p.getPlayerName());
        map.put("issuerUuid", p.getIssuerUuid().toString());
        map.put("issuerName", p.getIssuerName());
        map.put("type", p.getType().name());
        map.put("reason", p.getReason());
        map.put("startTime", p.getStartTime());
        map.put("endTime", p.getEndTime());
        map.put("active", p.isActive());
        map.put("serverName", p.getServerName());
        map.put("pardonedBy", p.getPardonedBy());
        map.put("pardonedAt", p.getPardonedAt());
        map.put("pardonReason", p.getPardonReason());
        return map;
    }

    private Punishment jailToPunishment(@NotNull JailPunishment jail) {
        return Punishment.builder()
                         .id(jail.getId())
                         .playerUuid(jail.getPlayerUuid())
                         .playerName(jail.getPlayerName())
                         .issuerUuid(jail.getIssuerUuid())
                         .issuerName(jail.getIssuerName())
                         .type(PunishmentType.JAIL)
                         .reason(jail.getReason())
                         .startTime(jail.getStartTime())
                         .endTime(jail.getEndTime())
                         .active(jail.isActive())
                         .serverName(jail.getServerName())
                         .pardonedBy(jail.getPardonedBy())
                         .pardonedAt(jail.getPardonedAt())
                         .build();
    }

    private Punishment warningToPunishment(@NotNull Warning warning) {
        return Punishment.builder()
                         .id(warning.getId())
                         .playerUuid(warning.getPlayerUuid())
                         .playerName(warning.getPlayerName())
                         .issuerUuid(warning.getIssuerUuid())
                         .issuerName(warning.getIssuerName())
                         .type(PunishmentType.WARNING)
                         .reason(warning.getReason())
                         .startTime(warning.getStartTime())
                         .endTime(warning.getEndTime())
                         .active(warning.isActive())
                         .serverName(warning.getServerName())
                         .pardonedBy(warning.getPardonedBy())
                         .pardonedAt(warning.getPardonedAt())
                         .build();
    }
}
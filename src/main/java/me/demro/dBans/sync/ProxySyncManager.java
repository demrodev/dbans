package me.demro.dBans.sync;

import com.google.gson.Gson;
import me.demro.dBans.DBans;
import me.demro.dBans.model.JailPunishment;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.model.Warning;
import me.demro.dBans.util.MessageUtil;
import me.demro.dBans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    // ==================== ОТПРАВКА СООБЩЕНИЙ ====================

    private void sendMessage(SyncMessage msg) {
        if (!isSyncEnabled()) {
            plugin.getLogger().info("⏭️ Sync disabled, not sending " + msg.getType());
            return;
        }
        String json = gson.toJson(msg);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        plugin.getLogger().info("📤 [Sync] Sending " + msg.getType() + " | ID: " + msg.getData().get("id") + " | JSON: " + json);
        Bukkit.getServer().sendPluginMessage(plugin, Constants.CHANNEL_NAME, data);
    }

    // Отправка создания наказания (Punishment)
    public void sendPunishmentCreate(Punishment punishment) {
        Map<String, Object> data = punishmentToMap(punishment);
        sendMessage(new SyncMessage("punishment_create", data));
    }

    // Отправка отмены наказания (Punishment)
    public void sendPunishmentRevoke(Punishment punishment) {
        Map<String, Object> data = punishmentToMap(punishment);
        sendMessage(new SyncMessage("punishment_revoke", data));
    }

    // Отправка изменения (причина или длительность)
    public void sendPunishmentModify(Punishment punishment, String oldReason, Long oldEnd) {
        Map<String, Object> data = punishmentToMap(punishment);
        if (oldReason != null) data.put("oldReason", oldReason);
        if (oldEnd != null) data.put("oldEnd", oldEnd);
        sendMessage(new SyncMessage("punishment_modify", data));
    }

    // Отправка истечения наказания
    public void sendPunishmentExpire(Punishment punishment) {
        Map<String, Object> data = punishmentToMap(punishment);
        sendMessage(new SyncMessage("punishment_expire", data));
    }

    // Удобные методы для Jail и Warning (преобразуют в Punishment)
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

    // ==================== ПРИЁМ СООБЩЕНИЙ ====================

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(Constants.CHANNEL_NAME)) return;
        if (processingIncoming) return; // защита от рекурсии

        try {
            String json = new String(message, StandardCharsets.UTF_8);
            SyncMessage msg = gson.fromJson(json, SyncMessage.class);
            plugin.getLogger().info("📥 [Sync] Received " + msg.getType() + " | ID: " + msg.getData().get("id"));

            processingIncoming = true;
            handleIncomingMessage(msg);
            processingIncoming = false;
        } catch (Exception e) {
            plugin.getLogger().warning("❌ [Sync] Failed to process incoming message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleIncomingMessage(SyncMessage msg) {
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

        // Создаём универсальный Punishment
        Punishment punishment = new Punishment();
        punishment.setId(id);
        punishment.setPlayerUuid(playerUuid);
        punishment.setPlayerName(playerName);
        punishment.setIssuerUuid(issuerUuid);
        punishment.setIssuerName(issuerName);
        punishment.setType(pType);
        punishment.setReason(reason);
        punishment.setStartTime(startTime);
        punishment.setEndTime(endTime);
        punishment.setActive(active);
        punishment.setServerName(serverName);
        punishment.setPardonReason(pardonReason);
        punishment.setPardonedBy(pardonedBy);
        punishment.setPardonedAt(pardonedAt);

        // В зависимости от типа обрабатываем в соответствующей таблице
        if (pType == PunishmentType.WARNING) {
            handleWarning(type, punishment);
        } else if (pType == PunishmentType.JAIL) {
            handleJail(type, punishment);
        } else {
            handleDefault(type, punishment);
        }
    }

    // ===== Обработка обычных наказаний (BAN, MUTE, KICK, IPBAN) =====

    private void handleDefault(String type, Punishment punishment) {
        // Проверяем, нужно ли применять на этом сервере (для sync_static)
        if (!shouldApplyLocally(punishment)) {
            plugin.getLogger().info("⏭️ [Sync] Skipping apply for server " + plugin.getServerName());
            return;
        }

        if ("punishment_create".equals(type) || "punishment_modify".equals(type) || "punishment_expire".equals(type)) {
            // Сохраняем/обновляем в БД
            if ("punishment_create".equals(type)) {
                Punishment existing = plugin.getDatabase().getPunishmentById(punishment.getId());
                if (existing == null) {
                    plugin.getDatabase().savePunishment(punishment);
                } else {
                    plugin.getDatabase().updatePunishment(punishment);
                }
            } else if ("punishment_modify".equals(type)) {
                plugin.getDatabase().updatePunishment(punishment);
                if (punishment.getType() == PunishmentType.MUTE) {
                    plugin.cancelMuteExpiry(punishment.getId());
                    if (punishment.isActive() && punishment.getEndTime() != null && punishment.getEndTime() > System.currentTimeMillis()) {
                        plugin.scheduleMuteExpiry(punishment);
                    }
                }
            } else if ("punishment_expire".equals(type)) {
                punishment.setActive(false);
                plugin.getDatabase().updatePunishment(punishment);
                if (punishment.getType() == PunishmentType.MUTE) {
                    plugin.cancelMuteExpiry(punishment.getId());
                }
            }
        } else if ("punishment_revoke".equals(type)) {
            punishment.setActive(false);
            plugin.getDatabase().updatePunishment(punishment);
            if (punishment.getType() == PunishmentType.MUTE) {
                plugin.cancelMuteExpiry(punishment.getId());
            }
        }

        // Применяем эффекты к онлайн-игроку (если активно)
        if (punishment.isActive() && !punishment.isExpired()) {
            applyLocalEffects(punishment);
        } else {
            removeLocalEffects(punishment);
        }
    }

    // ===== Обработка предупреждений =====

    private void handleWarning(String type, Punishment punishment) {
        if (!shouldApplyLocally(punishment)) {
            plugin.getLogger().info("⏭️ [Sync] Skipping warning apply for server " + plugin.getServerName());
            return;
        }

        Warning warning = new Warning();
        warning.setId(punishment.getId());
        warning.setPlayerUuid(punishment.getPlayerUuid());
        warning.setPlayerName(punishment.getPlayerName());
        warning.setIssuerUuid(punishment.getIssuerUuid());
        warning.setIssuerName(punishment.getIssuerName());
        warning.setReason(punishment.getReason());
        warning.setStartTime(punishment.getStartTime());
        warning.setEndTime(punishment.getEndTime());
        warning.setActive(punishment.isActive());
        warning.setServerName(punishment.getServerName());
        warning.setPardonedBy(punishment.getPardonedBy());
        warning.setPardonedAt(punishment.getPardonedAt());

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

        // Эффекты для игрока
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
                // Если отменено – можно уведомить
                MessageUtil.send(player, "unwarn_notify", "issuer", warning.getPardonedBy(), "id", warning.getId());
            }
        }
    }

    // ===== Обработка тюрьмы =====

    private void handleJail(String type, Punishment punishment) {
        if (!shouldApplyLocally(punishment)) {
            plugin.getLogger().info("⏭️ [Sync] Skipping jail apply for server " + plugin.getServerName());
            return;
        }

        JailPunishment jail = new JailPunishment();
        jail.setId(punishment.getId());
        jail.setPlayerUuid(punishment.getPlayerUuid());
        jail.setPlayerName(punishment.getPlayerName());
        jail.setIssuerUuid(punishment.getIssuerUuid());
        jail.setIssuerName(punishment.getIssuerName());
        jail.setReason(punishment.getReason());
        jail.setStartTime(punishment.getStartTime());
        jail.setEndTime(punishment.getEndTime());
        jail.setActive(punishment.isActive());
        jail.setServerName(punishment.getServerName());
        jail.setPardonedBy(punishment.getPardonedBy());
        jail.setPardonedAt(punishment.getPardonedAt());

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
                // Если игрок не в jail мире, телепортируем в спавн jail мира (или используем сохранённую локацию, если есть)
                // В сообщении координаты не передаются, поэтому используем дефолт
                if (!player.getWorld().getName().equals("jail")) {
                    // Получаем jailLocation из БД (может быть null)
                    JailPunishment current = plugin.getDatabase().getActiveJail(jail.getPlayerUuid());
                    if (current != null && current.getJailLocation() != null) {
                        player.teleport(current.getJailLocation());
                    } else {
                        // Или спавн мира jail
                        org.bukkit.World w = Bukkit.getWorld("jail");
                        if (w != null) player.teleport(w.getSpawnLocation());
                    }
                    // Применяем эффекты (невидимость и т.п.)
                    plugin.getJailManager().handlePlayerJoin(player); // это применит эффекты
                }
                MessageUtil.send(player, "jail_sync_notify", "reason", jail.getReason(), "sender", jail.getIssuerName());
            } else {
                // Освобождение
                if (player.getWorld().getName().equals("jail")) {
                    JailPunishment current = plugin.getDatabase().getActiveJail(jail.getPlayerUuid());
                    if (current != null && current.getId().equals(jail.getId())) {
                        plugin.getJailManager().releaseFromJail(player, current);
                    }
                }
            }
        }
    }

    // ===== Вспомогательные методы =====

    private boolean shouldApplyLocally(Punishment punishment) {
        String mode = plugin.getMode();
        if ("sync_static".equalsIgnoreCase(mode)) {
            String server = punishment.getServerName();
            if (server != null && !server.equals(plugin.getServerName())) {
                return false;
            }
        }
        return true;
    }

    // Применение локальных эффектов (без широковещания)
    private void applyLocalEffects(Punishment punishment) {
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
                // IP-бан не требует немедленного действия на других серверах, кроме кика при входе
                // Можно кикнуть, если IP совпадает
                String ip = player.getAddress().getAddress().getHostAddress();
                if (plugin.getDatabase().isIpBanned(ip)) {
                    String kickMsg = MessageUtil.getRawMessage("banip_player");
                    if (kickMsg == null) kickMsg = "&c✖ Ваш IP-адрес заблокирован.\nПричина: %reason%\nАдминистратор: %sender%";
                    kickMsg = kickMsg.replace("%reason%", punishment.getReason())
                            .replace("%sender%", punishment.getIssuerName());
                    player.kick(MessageUtil.deserializeForKick(kickMsg));
                }
                break;
            default:
                break;
        }
    }

    private void removeLocalEffects(Punishment punishment) {
        // Только для мута и jail есть обратные эффекты
        Player player = Bukkit.getPlayer(punishment.getPlayerUuid());
        if (player == null || !player.isOnline()) return;

        switch (punishment.getType()) {
            case MUTE:
                plugin.cancelMuteExpiry(punishment.getId());
                // Если это отмена, можно уведомить
                if ("punishment_revoke".equals(punishment.getType().name())) {
                    MessageUtil.send(player, "unmute_notify", "sender", punishment.getPardonedBy());
                }
                break;
            case JAIL:
                // Освобождение обрабатывается в handleJail
                break;
            default:
                break;
        }
    }

    // ===== Преобразования объектов в Map и обратно =====

    private Map<String, Object> punishmentToMap(Punishment p) {
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

    private Punishment jailToPunishment(JailPunishment jail) {
        Punishment p = new Punishment();
        p.setId(jail.getId());
        p.setPlayerUuid(jail.getPlayerUuid());
        p.setPlayerName(jail.getPlayerName());
        p.setIssuerUuid(jail.getIssuerUuid());
        p.setIssuerName(jail.getIssuerName());
        p.setType(PunishmentType.JAIL);
        p.setReason(jail.getReason());
        p.setStartTime(jail.getStartTime());
        p.setEndTime(jail.getEndTime());
        p.setActive(jail.isActive());
        p.setServerName(jail.getServerName());
        p.setPardonedBy(jail.getPardonedBy());
        p.setPardonedAt(jail.getPardonedAt());
        return p;
    }

    private Punishment warningToPunishment(Warning warning) {
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
        p.setPardonedBy(warning.getPardonedBy());
        p.setPardonedAt(warning.getPardonedAt());
        return p;
    }
}
package me.demro.dbans.api.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.api.adapter.NewPunishmentAdapter;
import me.demro.dbans.api.adapter.PunishmentMapper;
import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.model.Warning;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import me.demro.dlibs.dbans.api.event.*;
import me.demro.dlibs.dbans.api.exception.*;
import me.demro.dlibs.dbans.api.punishment.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public class PunishmentServiceImpl implements PunishmentService {

    private final DBans plugin;

    @Override
    public @NotNull CompletableFuture<PunishmentCreateResult> create(@NotNull PunishmentCreateRequest request) {
        // Валидация
        if (request.target().uuid().isEmpty()) {
            return CompletableFuture.failedFuture(new InvalidPunishmentRequestException("Target UUID is required"));
        }
        UUID targetUuid = request.target().uuid().get();
        String targetName = request.target().name().orElseGet(() -> {
            // Попробуем найти имя через БД
            return plugin.getDatabase().getPlayer(targetUuid) != null
                    ? plugin.getDatabase().getPlayer(targetUuid).getName()
                    : "Unknown";
        });

        // Проверка существования игрока
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            return CompletableFuture.failedFuture(new PlayerNotFoundException(targetUuid));
        }

        // Проверка иммунитета и прав (через PermissionService)
        me.demro.dlibs.dbans.api.punishment.PunishmentType apiType = request.type();
        // Для KICK иммунитет не проверяем, можно кикнуть любого
        if (apiType != me.demro.dlibs.dbans.api.punishment.PunishmentType.KICK) {
            CompletableFuture<Boolean> immuneFuture = plugin.getApi().permissions().hasImmunity(targetUuid, apiType);
            if (immuneFuture.join()) {
                return CompletableFuture.failedFuture(new InvalidPunishmentRequestException("Target has immunity for " + apiType));
            }
        }

        // Проверка активного наказания того же типа (кроме KICK)
        if (apiType != me.demro.dlibs.dbans.api.punishment.PunishmentType.KICK) {
            CompletableFuture<Boolean> hasActiveFuture = hasActive(targetUuid, apiType);
            if (hasActiveFuture.join()) {
                return CompletableFuture.failedFuture(new InvalidPunishmentRequestException("Target already has active " + apiType));
            }
        }

        // Маппинг во внутреннюю модель
        PunishmentType internalType;
        switch (apiType) {
            case BAN: internalType = PunishmentType.BAN; break;
            case MUTE: internalType = PunishmentType.MUTE; break;
            case KICK: internalType = PunishmentType.KICK; break;
            case IP_BAN: internalType = PunishmentType.IPBAN; break;
            case JAIL: internalType = PunishmentType.JAIL; break;
            case WARNING: internalType = PunishmentType.WARNING; break;
            default: return CompletableFuture.failedFuture(new InvalidPunishmentRequestException("Unsupported type: " + apiType));
        }

        long startTime = System.currentTimeMillis();
        Long endTime = null;
        if (request.duration().temporaryDuration().isPresent()) {
            endTime = startTime + request.duration().temporaryDuration().get().toMillis();
        } else if (request.duration().isPermanent()) {
            endTime = null;
        } else if (request.duration().isInstant()) {
            endTime = null; // для кика
        }

        // Создание объекта наказания в зависимости от типа
        Object internalPunishment = null;
        try {
            if (internalType == PunishmentType.JAIL) {
                // Джейл требует онлайн-игрока
                Player online = Bukkit.getPlayer(targetUuid);
                if (online == null || !online.isOnline()) {
                    return CompletableFuture.failedFuture(new PlayerNotFoundException("Player must be online for jail"));
                }
                Location previousLocation = online.getLocation().clone();
                long duration = request.duration().temporaryDuration().map(d -> d.toMillis()).orElse(0L);
                String jailId = plugin.getJailManager().sendToJail(
                        online,
                        duration > 0 ? duration : null,
                        previousLocation,
                        request.issuer().name(),
                        request.reason().value()
                );
                if (jailId == null) {
                    return CompletableFuture.failedFuture(new DBansException("Failed to jail player"));
                }
                JailPunishment jail = plugin.getDatabase().getActiveJail(targetUuid);
                if (jail == null) {
                    return CompletableFuture.failedFuture(new DBansException("Jail not found after creation"));
                }
                internalPunishment = jail;
            } else if (internalType == PunishmentType.WARNING) {
                Warning warning = Warning.builder()
                        .playerUuid(targetUuid)
                        .playerName(targetName)
                        .issuerUuid(request.issuer().uuid().orElse(UUID.nameUUIDFromBytes("CONSOLE".getBytes())))
                        .issuerName(request.issuer().name())
                        .reason(request.reason().value())
                        .startTime(startTime)
                        .endTime(endTime)
                        .active(true)
                        .serverName(request.serverName())
                        .build();
                plugin.getDatabase().saveWarning(warning);
                internalPunishment = warning;
            } else if (internalType == PunishmentType.IPBAN) {
                // IP-бан: нужно получить IP игрока
                String ip = plugin.getDatabase().getPlayerIp(targetUuid);
                if (ip == null) {
                    return CompletableFuture.failedFuture(new DBansException("No IP found for player"));
                }
                plugin.getDatabase().saveIpBan(
                        ip,
                        targetUuid,
                        targetName,
                        request.issuer().name(),
                        request.reason().value(),
                        startTime,
                        endTime
                );
                // Создаём запись в punishments для истории
                Punishment ipBan = Punishment.builder()
                        .playerUuid(targetUuid)
                        .playerName(targetName)
                        .issuerUuid(request.issuer().uuid().orElse(UUID.nameUUIDFromBytes("CONSOLE".getBytes())))
                        .issuerName(request.issuer().name())
                        .type(PunishmentType.IPBAN)
                        .reason(request.reason().value())
                        .startTime(startTime)
                        .endTime(endTime)
                        .active(true)
                        .serverName(request.serverName())
                        .build();
                plugin.getDatabase().savePunishment(ipBan);
                internalPunishment = ipBan;
            } else {
                // Обычные наказания: BAN, MUTE, KICK
                Punishment punishment = Punishment.builder()
                        .playerUuid(targetUuid)
                        .playerName(targetName)
                        .issuerUuid(request.issuer().uuid().orElse(UUID.nameUUIDFromBytes("CONSOLE".getBytes())))
                        .issuerName(request.issuer().name())
                        .type(internalType)
                        .reason(request.reason().value())
                        .startTime(startTime)
                        .endTime(endTime)
                        .active(true)
                        .serverName(request.serverName())
                        .build();
                plugin.getDatabase().savePunishment(punishment);
                internalPunishment = punishment;
            }
        } catch (Exception e) {
            log.error("Failed to create punishment: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(new DBansException("Failed to create punishment", e));
        }

        // Применение эффектов (кик, бан, мут и т.д.) и уведомлений
        applyEffects(internalPunishment, request);

        // Синхронизация через прокси
        if (plugin.getProxySyncManager() != null) {
            if (internalPunishment instanceof Punishment) {
                plugin.getProxySyncManager().sendPunishmentCreate((Punishment) internalPunishment);
            } else if (internalPunishment instanceof JailPunishment) {
                plugin.getProxySyncManager().sendPunishmentCreate((JailPunishment) internalPunishment);
            } else if (internalPunishment instanceof Warning) {
                plugin.getProxySyncManager().sendPunishmentCreate((Warning) internalPunishment);
            }
        }

        // Вызов события
        me.demro.dlibs.dbans.api.punishment.Punishment apiPunishment = PunishmentMapper.toApiPunishment(internalPunishment);
        EventOrigin origin = request.options().silent() ? EventOrigin.API : EventOrigin.COMMAND;
        PunishmentCreateEvent event = new PunishmentCreateEvent(
                apiPunishment,
                origin,
                Instant.now(),
                false
        );
        Bukkit.getPluginManager().callEvent(event);

        // Кэширование (если нужно)
        if (internalPunishment instanceof Punishment) {
            plugin.getCacheManager().cacheActivePunishment(
                    targetUuid,
                    ((Punishment) internalPunishment).getType(),
                    plugin.getServerName(),
                    plugin.getMode(),
                    (Punishment) internalPunishment
            );
            // Если мут – планируем истечение
            if (internalType == PunishmentType.MUTE && endTime != null) {
                plugin.scheduleMuteExpiry((Punishment) internalPunishment);
            }
        }

        return CompletableFuture.completedFuture(new PunishmentCreateResult(apiPunishment));
    }

    private void applyEffects(Object punishment, PunishmentCreateRequest request) {
        Player player = Bukkit.getPlayer(request.target().uuid().orElse(null));
        if (player == null || !player.isOnline()) return;

        if (punishment instanceof Punishment) {
            Punishment p = (Punishment) punishment;
            switch (p.getType()) {
                case BAN:
                    String kickMsg = MessageUtil.getRawMessage(p.getEndTime() == null ? "ban_player" : "tempban_player");
                    if (kickMsg == null) {
                        kickMsg = p.getEndTime() == null
                                ? "&c✖ Вы были забанены навсегда.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%"
                                : "&c✖ Вы были забанены на %duration%.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
                    }
                    String duration = p.getEndTime() == null
                            ? plugin.getConfig().getString("permanent_word", "навсегда")
                            : TimeUtil.formatDuration(p.getEndTime() - System.currentTimeMillis());
                    kickMsg = kickMsg.replace("%reason%", p.getReason())
                            .replace("%sender%", p.getIssuerName())
                            .replace("%duration%", duration)
                            .replace("%server%", p.getServerName() != null ? p.getServerName() : "unknown")
                            .replace("%id%", p.getId());
                    player.kick(MessageUtil.deserializeForKick(kickMsg));
                    break;
                case MUTE:
                    String key = p.getEndTime() == null ? "mute_player" : "tempmute_player";
                    String dur = p.getEndTime() == null
                            ? plugin.getConfig().getString("permanent_word", "навсегда")
                            : TimeUtil.formatDuration(p.getEndTime() - System.currentTimeMillis());
                    MessageUtil.send(player, key,
                            "sender", p.getIssuerName(),
                            "reason", p.getReason(),
                            "duration", dur,
                            "server", p.getServerName(),
                            "id", p.getId());
                    break;
                case KICK:
                    String kickMsg2 = MessageUtil.getRawMessage("kick_player");
                    if (kickMsg2 == null) kickMsg2 = "&c✖ Вас кикнули.\nПричина: %reason%\nАдминистратор: %sender%";
                    kickMsg2 = kickMsg2.replace("%reason%", p.getReason())
                            .replace("%sender%", p.getIssuerName());
                    player.kick(MessageUtil.deserializeForKick(kickMsg2));
                    break;
                case IPBAN:
                    String ip = player.getAddress().getAddress().getHostAddress();
                    if (plugin.getDatabase().isIpBanned(ip)) {
                        String kickMsg3 = MessageUtil.getRawMessage("banip_player");
                        if (kickMsg3 == null) kickMsg3 = "&c✖ Ваш IP-адрес заблокирован.\nПричина: %reason%\nАдминистратор: %sender%";
                        kickMsg3 = kickMsg3.replace("%reason%", p.getReason())
                                .replace("%sender%", p.getIssuerName());
                        player.kick(MessageUtil.deserializeForKick(kickMsg3));
                    }
                    break;
                default:
                    break;
            }
        } else if (punishment instanceof JailPunishment) {
            // Уже обработано в JailManager
        } else if (punishment instanceof Warning) {
            Warning w = (Warning) punishment;
            String durationStr = w.getEndTime() == null
                    ? plugin.getConfig().getString("permanent_word", "навсегда")
                    : TimeUtil.formatDuration(w.getEndTime() - System.currentTimeMillis());
            MessageUtil.send(player, "warn_player",
                    "sender", w.getIssuerName(),
                    "reason", w.getReason(),
                    "duration", durationStr,
                    "server", w.getServerName(),
                    "id", w.getId());
            // Проверка порогов варнов
            plugin.getWarnManager().checkAndApplyThresholds(player);
        }

        // Бродкаст, если не silent
        if (!request.options().silent() && request.options().broadcast()) {
            broadcastPunishment(punishment, request);
        }
    }

    private void broadcastPunishment(Object punishment, PunishmentCreateRequest request) {
        String key = null;
        String permission = null;
        boolean temp = false;
        String typeName = "";
        String targetName = request.target().name().orElse("Unknown");

        if (punishment instanceof Punishment) {
            Punishment p = (Punishment) punishment;
            temp = p.getEndTime() != null;
            switch (p.getType()) {
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
                case IPBAN:
                    key = "banip_broadcast";
                    permission = "dbans.notify.ipban";
                    break;
                default:
                    return;
            }
            String durationStr = temp ? TimeUtil.formatDuration(p.getEndTime() - p.getStartTime()) : "навсегда";
            MessageUtil.broadcast(permission, key,
                    "sender", request.issuer().name(),
                    "target", targetName,
                    "reason", p.getReason(),
                    "duration", durationStr,
                    "server", p.getServerName(),
                    "id", p.getId());
        } else if (punishment instanceof JailPunishment) {
            JailPunishment j = (JailPunishment) punishment;
            temp = j.getEndTime() != null;
            String durationStr = temp ? TimeUtil.formatDuration(j.getEndTime() - j.getStartTime()) : "навсегда";
            MessageUtil.broadcast("dbans.notify.jail", "jail_broadcast",
                    "sender", request.issuer().name(),
                    "target", targetName,
                    "reason", j.getReason(),
                    "duration", durationStr,
                    "server", j.getServerName(),
                    "id", j.getId());
        } else if (punishment instanceof Warning) {
            Warning w = (Warning) punishment;
            temp = w.getEndTime() != null;
            String durationStr = temp ? TimeUtil.formatDuration(w.getEndTime() - w.getStartTime()) : "навсегда";
            MessageUtil.broadcast("dbans.notify.warning", "warn_broadcast",
                    "sender", request.issuer().name(),
                    "target", targetName,
                    "reason", w.getReason(),
                    "duration", durationStr,
                    "server", w.getServerName(),
                    "id", w.getId());
        }
    }

    @Override
    public @NotNull CompletableFuture<Void> revoke(@NotNull PunishmentRevokeRequest request) {
        String id = request.punishmentId().value();

        // Ищем наказание в трёх таблицах
        Punishment p = plugin.getDatabase().getPunishmentById(id);
        if (p != null) {
            p.setActive(false);
            p.setPardonedBy(request.issuer().name());
            p.setPardonedAt(System.currentTimeMillis());
            p.setPardonReason(request.reason().value());
            plugin.getDatabase().updatePunishment(p);
            plugin.getCacheManager().invalidateAllForPlayer(p.getPlayerUuid());
            if (p.getType() == PunishmentType.MUTE) {
                plugin.cancelMuteExpiry(id);
            }
            if (plugin.getProxySyncManager() != null) {
                plugin.getProxySyncManager().sendPunishmentRevoke(p);
            }
            // Событие
            PunishmentRevocation revocation = new PunishmentRevocation(
                    request.issuer(),
                    request.reason(),
                    request.serverName(),
                    Instant.now()
            );
            PunishmentRevokeEvent event = new PunishmentRevokeEvent(
                    new NewPunishmentAdapter(p),
                    revocation,
                    EventOrigin.API,
                    Instant.now(),
                    false
            );
            Bukkit.getPluginManager().callEvent(event);
            // Уведомление
            Player player = Bukkit.getPlayer(p.getPlayerUuid());
            if (player != null && player.isOnline()) {
                MessageUtil.send(player, "unpunish_notify",
                        "issuer", request.issuer().name(),
                        "id", id,
                        "type", p.getType().name().toLowerCase());
            }
            broadcastRevoke(p, request);
            return CompletableFuture.completedFuture(null);
        }

        JailPunishment j = plugin.getDatabase().getJailById(id);
        if (j != null) {
            j.setActive(false);
            j.setPardonedBy(request.issuer().name());
            j.setPardonedAt(System.currentTimeMillis());
            plugin.getDatabase().updateJail(j);
            Player player = Bukkit.getPlayer(j.getPlayerUuid());
            if (player != null && player.isOnline()) {
                plugin.getJailManager().releaseFromJail(player, j);
                MessageUtil.send(player, "unjail_notify", "issuer", request.issuer().name());
            }
            if (plugin.getProxySyncManager() != null) {
                plugin.getProxySyncManager().sendPunishmentRevoke(j);
            }
            PunishmentRevocation revocation = new PunishmentRevocation(
                    request.issuer(),
                    request.reason(),
                    request.serverName(),
                    Instant.now()
            );
            PunishmentRevokeEvent event = new PunishmentRevokeEvent(
                    new NewPunishmentAdapter(j),
                    revocation,
                    EventOrigin.API,
                    Instant.now(),
                    false
            );
            Bukkit.getPluginManager().callEvent(event);
            broadcastRevoke(j, request);
            return CompletableFuture.completedFuture(null);
        }

        Warning w = plugin.getDatabase().getWarningById(id);
        if (w != null) {
            w.setActive(false);
            w.setPardonedBy(request.issuer().name());
            w.setPardonedAt(System.currentTimeMillis());
            plugin.getDatabase().updateWarning(w);
            Player player = Bukkit.getPlayer(w.getPlayerUuid());
            if (player != null && player.isOnline()) {
                MessageUtil.send(player, "unwarn_notify", "issuer", request.issuer().name(), "id", id);
            }
            if (plugin.getProxySyncManager() != null) {
                plugin.getProxySyncManager().sendPunishmentRevoke(w);
            }
            PunishmentRevocation revocation = new PunishmentRevocation(
                    request.issuer(),
                    request.reason(),
                    request.serverName(),
                    Instant.now()
            );
            PunishmentRevokeEvent event = new PunishmentRevokeEvent(
                    new NewPunishmentAdapter(w),
                    revocation,
                    EventOrigin.API,
                    Instant.now(),
                    false
            );
            Bukkit.getPluginManager().callEvent(event);
            broadcastRevoke(w, request);
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.failedFuture(new PunishmentNotFoundException(request.punishmentId()));
    }

    private void broadcastRevoke(Object punishment, PunishmentRevokeRequest request) {
        String key = null;
        String permission = null;
        String targetName = "";
        String id = "";

        if (punishment instanceof Punishment) {
            Punishment p = (Punishment) punishment;
            targetName = p.getPlayerName();
            id = p.getId();
            switch (p.getType()) {
                case BAN:
                    key = "unban_broadcast";
                    permission = "dbans.notify.unban";
                    break;
                case MUTE:
                    key = "unmute_broadcast";
                    permission = "dbans.notify.unmute";
                    break;
                case IPBAN:
                    key = "unbanip_broadcast";
                    permission = "dbans.notify.unbanip";
                    break;
                default:
                    return;
            }
        } else if (punishment instanceof JailPunishment) {
            JailPunishment j = (JailPunishment) punishment;
            targetName = j.getPlayerName();
            id = j.getId();
            key = "unjail_broadcast";
            permission = "dbans.notify.unjail";
        } else if (punishment instanceof Warning) {
            Warning w = (Warning) punishment;
            targetName = w.getPlayerName();
            id = w.getId();
            key = "unwarn_broadcast";
            permission = "dbans.notify.unwarn";
        } else {
            return;
        }

        if (key != null) {
            MessageUtil.broadcast(permission, key,
                    "sender", request.issuer().name(),
                    "target", targetName,
                    "id", id);
        }
    }

    @Override
    public @NotNull CompletableFuture<Optional<me.demro.dlibs.dbans.api.punishment.Punishment>> findById(@NotNull PunishmentId id) {
        String value = id.value();
        Punishment p = plugin.getDatabase().getPunishmentById(value);
        if (p != null) {
            return CompletableFuture.completedFuture(Optional.of(new NewPunishmentAdapter(p)));
        }
        JailPunishment j = plugin.getDatabase().getJailById(value);
        if (j != null) {
            return CompletableFuture.completedFuture(Optional.of(new NewPunishmentAdapter(j)));
        }
        Warning w = plugin.getDatabase().getWarningById(value);
        if (w != null) {
            return CompletableFuture.completedFuture(Optional.of(new NewPunishmentAdapter(w)));
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public @NotNull CompletableFuture<List<me.demro.dlibs.dbans.api.punishment.Punishment>> find(@NotNull PunishmentQuery query) {
        List<me.demro.dlibs.dbans.api.punishment.Punishment> result = new ArrayList<>();

        // Если указан targetUuid
        if (query.targetUuid().isPresent()) {
            UUID uuid = query.targetUuid().get();
            List<Punishment> punishments = plugin.getDatabase().getPunishmentHistory(uuid, true);
            List<JailPunishment> jails = plugin.getDatabase().getAllJailsForPlayer(uuid);
            List<Warning> warnings = plugin.getDatabase().getAllWarningsForPlayer(uuid);

            for (Punishment p : punishments) {
                if (matchesQuery(p, query)) {
                    result.add(new NewPunishmentAdapter(p));
                }
            }
            for (JailPunishment j : jails) {
                if (matchesQuery(j, query)) {
                    result.add(new NewPunishmentAdapter(j));
                }
            }
            for (Warning w : warnings) {
                if (matchesQuery(w, query)) {
                    result.add(new NewPunishmentAdapter(w));
                }
            }
        } else {
            // Без targetUuid – ищем все
            List<Punishment> allP = plugin.getDatabase().getAllPunishments();
            List<JailPunishment> allJ = plugin.getDatabase().getAllJailsForAllPlayers();
            List<Warning> allW = plugin.getDatabase().getAllWarnings();

            for (Punishment p : allP) {
                if (matchesQuery(p, query)) {
                    result.add(new NewPunishmentAdapter(p));
                }
            }
            for (JailPunishment j : allJ) {
                if (matchesQuery(j, query)) {
                    result.add(new NewPunishmentAdapter(j));
                }
            }
            for (Warning w : allW) {
                if (matchesQuery(w, query)) {
                    result.add(new NewPunishmentAdapter(w));
                }
            }
        }

        // Сортировка по времени создания (новые сверху)
        result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));

        // Лимит
        if (query.limit().isPresent() && result.size() > query.limit().get()) {
            result = result.subList(0, query.limit().get());
        }

        return CompletableFuture.completedFuture(result);
    }

    private boolean matchesQuery(Object obj, PunishmentQuery query) {
        if (obj instanceof Punishment) {
            Punishment p = (Punishment) obj;
            if (query.type().isPresent() && !mapType(p.getType()).equals(query.type().get())) return false;
            if (query.status().isPresent()) {
                PunishmentStatus status = getStatus(p);
                if (status != query.status().get()) return false;
            }
            return true;
        } else if (obj instanceof JailPunishment) {
            JailPunishment j = (JailPunishment) obj;
            if (query.type().isPresent() && query.type().get() != me.demro.dlibs.dbans.api.punishment.PunishmentType.JAIL) return false;
            if (query.status().isPresent()) {
                PunishmentStatus status = getStatus(j);
                if (status != query.status().get()) return false;
            }
            return true;
        } else if (obj instanceof Warning) {
            Warning w = (Warning) obj;
            if (query.type().isPresent() && query.type().get() != me.demro.dlibs.dbans.api.punishment.PunishmentType.WARNING) return false;
            if (query.status().isPresent()) {
                PunishmentStatus status = getStatus(w);
                if (status != query.status().get()) return false;
            }
            return true;
        }
        return false;
    }

    private PunishmentStatus getStatus(Punishment p) {
        if (!p.isActive()) return PunishmentStatus.REVOKED;
        if (p.isExpired()) return PunishmentStatus.EXPIRED;
        return PunishmentStatus.ACTIVE;
    }

    private PunishmentStatus getStatus(JailPunishment j) {
        if (!j.isActive()) return PunishmentStatus.REVOKED;
        if (j.isExpired()) return PunishmentStatus.EXPIRED;
        return PunishmentStatus.ACTIVE;
    }

    private PunishmentStatus getStatus(Warning w) {
        if (!w.isActive()) return PunishmentStatus.REVOKED;
        if (w.isExpired()) return PunishmentStatus.EXPIRED;
        return PunishmentStatus.ACTIVE;
    }

    private me.demro.dlibs.dbans.api.punishment.PunishmentType mapType(PunishmentType internalType) {
        switch (internalType) {
            case BAN: return me.demro.dlibs.dbans.api.punishment.PunishmentType.BAN;
            case MUTE: return me.demro.dlibs.dbans.api.punishment.PunishmentType.MUTE;
            case KICK: return me.demro.dlibs.dbans.api.punishment.PunishmentType.KICK;
            case IPBAN: return me.demro.dlibs.dbans.api.punishment.PunishmentType.IP_BAN;
            case JAIL: return me.demro.dlibs.dbans.api.punishment.PunishmentType.JAIL;
            case WARNING: return me.demro.dlibs.dbans.api.punishment.PunishmentType.WARNING;
            default: throw new IllegalArgumentException("Unknown type: " + internalType);
        }
    }

    @Override
    public @NotNull CompletableFuture<Boolean> hasActive(@NotNull UUID targetUuid, @NotNull me.demro.dlibs.dbans.api.punishment.PunishmentType type) {
        // Проверка в зависимости от типа
        switch (type) {
            case BAN:
            case MUTE:
            case IP_BAN:
                Punishment p = plugin.getDatabase().getActivePunishment(
                        targetUuid,
                        mapTypeToInternal(type),
                        plugin.getServerName(),
                        plugin.getMode()
                );
                return CompletableFuture.completedFuture(p != null && p.isActive() && !p.isExpired());
            case JAIL:
                JailPunishment j = plugin.getDatabase().getActiveJail(targetUuid);
                return CompletableFuture.completedFuture(j != null && j.isActive() && !j.isExpired());
            case WARNING:
                List<Warning> warnings = plugin.getDatabase().getActiveWarnings(targetUuid);
                return CompletableFuture.completedFuture(!warnings.isEmpty());
            case KICK:
                return CompletableFuture.completedFuture(false);
            default:
                return CompletableFuture.completedFuture(false);
        }
    }

    private PunishmentType mapTypeToInternal(me.demro.dlibs.dbans.api.punishment.PunishmentType apiType) {
        switch (apiType) {
            case BAN: return PunishmentType.BAN;
            case MUTE: return PunishmentType.MUTE;
            case KICK: return PunishmentType.KICK;
            case IP_BAN: return PunishmentType.IPBAN;
            case JAIL: return PunishmentType.JAIL;
            case WARNING: return PunishmentType.WARNING;
            default: throw new IllegalArgumentException("Unsupported type: " + apiType);
        }
    }
}
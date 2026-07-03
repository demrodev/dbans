package me.demro.dbans.api.impl;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.api.adapter.PunishmentMapper;
import me.demro.dbans.model.*;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.util.MessageUtil;
import me.demro.dlibs.dbans.api.event.EventOrigin;
import me.demro.dlibs.dbans.api.event.PunishmentCreateEvent;
import me.demro.dlibs.dbans.api.event.PunishmentRevocation;
import me.demro.dlibs.dbans.api.event.PunishmentRevokeEvent;
import me.demro.dlibs.dbans.api.exception.DBansException;
import me.demro.dlibs.dbans.api.exception.InvalidPunishmentRequestException;
import me.demro.dlibs.dbans.api.exception.PlayerNotFoundException;
import me.demro.dlibs.dbans.api.exception.PunishmentNotFoundException;
import me.demro.dlibs.dbans.api.punishment.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class PunishmentServiceImpl implements PunishmentService {

    private final DBans plugin;
    private final PunishmentCreator creator;
    private final PunishmentEffectApplier effectApplier;
    private final PunishmentBroadcaster broadcaster;

    PunishmentServiceImpl(@NotNull DBans plugin) {
        this.plugin = plugin;
        this.creator = new PunishmentCreator(plugin);
        this.effectApplier = new PunishmentEffectApplier(plugin);
        this.broadcaster = new PunishmentBroadcaster();
    }

    @Override
    public @NotNull CompletableFuture<PunishmentCreateResult> create(@NotNull PunishmentCreateRequest request) {
        if (request.target().uuid().isEmpty()) {
            return CompletableFuture.failedFuture(new InvalidPunishmentRequestException("Target UUID is required"));
        }

        UUID targetUuid = request.target().uuid().get();
        me.demro.dlibs.dbans.api.punishment.PunishmentType apiType = request.type();

        if (apiType != me.demro.dlibs.dbans.api.punishment.PunishmentType.IP_BAN) {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetUuid);
            if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
                return CompletableFuture.failedFuture(new PlayerNotFoundException(targetUuid));
            }
        }

        String targetName = resolveTargetName(request, targetUuid);
        PunishmentType internalType = PunishmentMapper.toInternalType(apiType);
        long startTime = System.currentTimeMillis();
        Long endTime = request.duration().temporaryDuration().map(d -> startTime + d.toMillis()).orElse(null);

        InternalPunishment punishment;
        try {
            punishment = creator.create(request, targetUuid, targetName, internalType, startTime, endTime);
        } catch (DBansException e) {
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            log.error("Failed to create punishment: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(new DBansException("Failed to create punishment", e));
        }

        effectApplier.apply(punishment, targetUuid);

        if (!request.options().silent() && request.options().broadcast()) {
            broadcaster.broadcastCreate(punishment, request);
        }

        syncCreate(punishment);

        me.demro.dlibs.dbans.api.punishment.Punishment apiPunishment = PunishmentMapper.toApiPunishment(punishment);
        EventOrigin origin = request.options().silent() ? EventOrigin.API : EventOrigin.COMMAND;
        Bukkit.getPluginManager().callEvent(new PunishmentCreateEvent(apiPunishment, origin, Instant.now(), false));

        postCreate(punishment, targetUuid, internalType, endTime);

        return CompletableFuture.completedFuture(new PunishmentCreateResult(apiPunishment));
    }

    @Override
    public @NotNull CompletableFuture<Void> revoke(@NotNull PunishmentRevokeRequest request) {
        String id = request.punishmentId().value();
        InternalPunishment punishment = findInternalById(id);
        if (punishment == null) {
            return CompletableFuture.failedFuture(new PunishmentNotFoundException(request.punishmentId()));
        }
        revokeInternal(punishment, request);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public @NotNull CompletableFuture<Optional<me.demro.dlibs.dbans.api.punishment.Punishment>> findById(
            @NotNull PunishmentId id
    ) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(findInternalById(id.value())).map(PunishmentMapper::toApiPunishment));
    }

    @Override
    public @NotNull CompletableFuture<List<me.demro.dlibs.dbans.api.punishment.Punishment>> find(
            @NotNull PunishmentQuery query
    ) {
        List<InternalPunishment> candidates = query.targetUuid()
                                                   .map(this::fetchForPlayer)
                                                   .orElseGet(this::fetchAll);

        List<me.demro.dlibs.dbans.api.punishment.Punishment> result = candidates.stream()
                                                                                .filter(p ->
                                                                                                matchesQuery(p, query)
                                                                                )
                                                                                .map(PunishmentMapper::toApiPunishment)
                                                                                .sorted((a, b) ->
                                                                                                b.createdAt().compareTo(a.createdAt())
                                                                                )
                                                                                .collect(Collectors.toCollection(
                                                                                        ArrayList::new)
                                                                                );

        query.limit().ifPresent(limit -> {
            if (result.size() > limit) {
                result.subList(limit, result.size()).clear();
            }
        });

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> hasActive(@NotNull UUID targetUuid,
                                                         @NotNull me.demro.dlibs.dbans.api.punishment.PunishmentType type
    ) {
        return CompletableFuture.completedFuture(switch (type) {
            case BAN, MUTE, IP_BAN -> isActiveAndValid(plugin.getDatabase().getActivePunishment(
                    targetUuid, PunishmentMapper.toInternalType(type), plugin.getServerName(), plugin.getMode()));
            case JAIL -> isActiveAndValid(plugin.getDatabase().getActiveJail(targetUuid));
            case WARNING -> !plugin.getDatabase().getActiveWarnings(targetUuid).isEmpty();
            case KICK -> false;
        });
    }

    private @NotNull String resolveTargetName(@NotNull PunishmentCreateRequest request, @NotNull UUID targetUuid) {
        return request.target().name().orElseGet(() -> {
            PlayerInfo info = plugin.getDatabase().getPlayer(targetUuid);
            return (info != null && info.getName() != null) ? info.getName() : "Unknown";
        });
    }

    private void postCreate(@NotNull InternalPunishment punishment, @NotNull UUID targetUuid,
                            @NotNull PunishmentType internalType, @Nullable Long endTime
    ) {
        if (punishment instanceof Punishment p) {
            plugin.getCacheManager().cacheActivePunishment(targetUuid,
                                                           p.getType(),
                                                           plugin.getServerName(),
                                                           plugin.getMode(),
                                                           p
            );
            if (internalType == PunishmentType.MUTE && endTime != null) {
                plugin.scheduleMuteExpiry(p);
            }
        }
    }

    private void syncCreate(@NotNull InternalPunishment punishment) {
        if (plugin.getProxySyncManager() == null) {
            return;
        }
        switch (punishment) {
            case Punishment p -> plugin.getProxySyncManager().sendPunishmentCreate(p);
            case JailPunishment j -> plugin.getProxySyncManager().sendPunishmentCreate(j);
            case Warning w -> plugin.getProxySyncManager().sendPunishmentCreate(w);
            default -> {
            }
        }
    }

    @Nullable
    private InternalPunishment findInternalById(@NotNull String id) {
        Punishment punishment = plugin.getDatabase().getPunishmentById(id);
        if (punishment != null) {
            return punishment;
        }
        JailPunishment j = plugin.getDatabase().getJailById(id);
        if (j != null) {
            return j;
        }
        return plugin.getDatabase().getWarningById(id);
    }

    private void revokeInternal(@NotNull InternalPunishment punishment, @NotNull PunishmentRevokeRequest request) {
        markRevoked(punishment, request);
        saveRevoked(punishment);
        invalidateCaches(punishment);
        notifyPlayer(punishment, request);
        syncRevoke(punishment);
        fireRevokeEvent(punishment, request);
        broadcaster.broadcastRevoke(punishment, request);
    }

    private void markRevoked(@NotNull InternalPunishment punishment, @NotNull PunishmentRevokeRequest request) {
        punishment.setActive(false);
        punishment.setPardonedBy(request.issuer().name());
        punishment.setPardonedAt(System.currentTimeMillis());
        if (punishment instanceof Punishment p) {
            p.setPardonReason(request.reason().value());
        }
    }

    private void saveRevoked(@NotNull InternalPunishment punishment) {
        switch (punishment) {
            case Punishment p -> plugin.getDatabase().updatePunishment(p);
            case JailPunishment j -> plugin.getDatabase().updateJail(j);
            case Warning w -> plugin.getDatabase().updateWarning(w);
            default -> {
            }
        }
    }

    private void invalidateCaches(@NotNull InternalPunishment punishment) {
        if (punishment instanceof Punishment p) {
            plugin.getCacheManager().invalidateAllForPlayer(p.getPlayerUuid());
            if (p.getType() == PunishmentType.MUTE) {
                plugin.cancelMuteExpiry(p.getId());
            }
        }
    }

    private void notifyPlayer(@NotNull InternalPunishment punishment, @NotNull PunishmentRevokeRequest request) {
        Player player = Bukkit.getPlayer(punishment.getPlayerUuid());
        if (player == null || !player.isOnline()) {
            return;
        }
        switch (punishment) {
            case Punishment p -> MessageUtil.send(player, "unpunish_notify",
                                                  "issuer", request.issuer().name(),
                                                  "id", p.getId(),
                                                  "type", p.getType().name().toLowerCase());
            case JailPunishment j -> {
                plugin.getJailManager().releaseFromJail(player, j);
                MessageUtil.send(player, "unjail_notify", "issuer", request.issuer().name());
            }
            case Warning w -> MessageUtil.send(player, "unwarn_notify",
                                               "issuer", request.issuer().name(),
                                               "id", w.getId());
            default -> {
            }
        }
    }

    private void syncRevoke(@NotNull InternalPunishment punishment) {
        if (plugin.getProxySyncManager() == null) {
            return;
        }
        switch (punishment) {
            case Punishment p -> plugin.getProxySyncManager().sendPunishmentRevoke(p);
            case JailPunishment j -> plugin.getProxySyncManager().sendPunishmentRevoke(j);
            case Warning w -> plugin.getProxySyncManager().sendPunishmentRevoke(w);
            default -> {
            }
        }
    }

    private void fireRevokeEvent(@NotNull InternalPunishment punishment, @NotNull PunishmentRevokeRequest request) {
        PunishmentRevocation revocation = new PunishmentRevocation(
                request.issuer(), request.reason(), request.serverName(), Instant.now());
        Bukkit.getPluginManager().callEvent(new PunishmentRevokeEvent(
                PunishmentMapper.toApiPunishment(punishment),
                revocation, EventOrigin.API, Instant.now(), false));
    }

    private @NotNull List<InternalPunishment> fetchForPlayer(@NotNull UUID uuid) {
        List<InternalPunishment> all = new ArrayList<>();
        all.addAll(plugin.getDatabase().getPunishmentHistory(uuid, true));
        all.addAll(plugin.getDatabase().getAllJailsForPlayer(uuid));
        all.addAll(plugin.getDatabase().getAllWarningsForPlayer(uuid));
        return all;
    }

    private @NotNull List<InternalPunishment> fetchAll() {
        List<InternalPunishment> all = new ArrayList<>();
        all.addAll(plugin.getDatabase().getAllPunishments());
        all.addAll(plugin.getDatabase().getAllJailsForAllPlayers());
        all.addAll(plugin.getDatabase().getAllWarnings());
        return all;
    }

    private boolean matchesQuery(@NotNull InternalPunishment p, @NotNull PunishmentQuery query) {
        if (query.type().isPresent() && PunishmentMapper.toApiType(p) != query.type().get()) {
            return false;
        }
        return query.status().isEmpty() || getStatus(p) == query.status().get();
    }

    private @NotNull PunishmentStatus getStatus(@NotNull InternalPunishment p) {
        if (!p.isActive()) {
            return PunishmentStatus.REVOKED;
        }
        if (p.isExpired()) {
            return PunishmentStatus.EXPIRED;
        }
        return PunishmentStatus.ACTIVE;
    }

    @Contract("null -> false")
    private boolean isActiveAndValid(@Nullable InternalPunishment p) {
        return p != null && p.isActive() && !p.isExpired();
    }
}

package me.demro.dBans.api;

import me.demro.dBans.DBans;
import me.demro.dBans.api.adapter.PunishmentAdapter;
import me.demro.dBans.model.JailPunishment;
import me.demro.dBans.model.PlayerInfo;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.model.Warning;
import me.demro.dBans.service.PunishService;
import me.demro.dBans.util.CacheManager;
import me.demro.dBans.util.MessageUtil;
import me.demro.dlibs.api.EventManager;
import me.demro.dlibs.api.PunishmentManager;
import me.demro.dlibs.api.events.PunishmentRevokeEvent;
import me.demro.dlibs.api.exceptions.PlayerNotFoundException;
import me.demro.dlibs.api.exceptions.PunishmentNotFoundException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PunishmentManagerImpl implements PunishmentManager {
    private final DBans plugin;
    private final CacheManager cacheManager;
    private final PunishService punishService;
    private final EventManager eventManager;

    public PunishmentManagerImpl(DBans plugin) {
        this.plugin = plugin;
        this.cacheManager = plugin.getCacheManager();
        this.punishService = new PunishService(plugin);
        this.eventManager = plugin.getEventManager();
    }

    @Override
    public String punish(UUID targetUuid, me.demro.dlibs.api.PunishmentType type, String reason,
                         Long duration, UUID issuerUuid, String issuerName, String serverName)
            throws PlayerNotFoundException {
        PunishmentType internalType = PunishmentType.valueOf(type.name());
        PlayerInfo info = plugin.getDatabase().getPlayer(targetUuid);
        String targetName;
        if (info != null) {
            targetName = info.getName();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetUuid);
            if (off.hasPlayedBefore() || off.isOnline()) {
                targetName = off.getName();
            } else {
                throw new PlayerNotFoundException(targetUuid.toString());
            }
        }

        Punishment punishment = punishService.applyPunishment(
                targetUuid, targetName, issuerUuid, issuerName,
                internalType, reason, duration, serverName, false
        );
        if (punishment == null) {
            throw new RuntimeException("Failed to apply punishment");
        }
        return punishment.getId();
    }

    @Override
    public void revoke(String punishmentId, String issuerName, String reason)
            throws PunishmentNotFoundException {

        // ==================== 1. Таблица punishments ====================
        Punishment p = plugin.getDatabase().getPunishmentById(punishmentId);
        if (p != null) {
            plugin.getDatabase().pardonPunishment(punishmentId, issuerName, reason);
            cacheManager.invalidateAllForPlayer(p.getPlayerUuid());
            if (p.getType() == PunishmentType.MUTE) {
                plugin.cancelMuteExpiry(punishmentId);
            }

            String broadcastKey;
            switch (p.getType()) {
                case BAN:
                    broadcastKey = "unban_broadcast";
                    break;
                case MUTE:
                    broadcastKey = "unmute_broadcast";
                    break;
                case IPBAN:
                    broadcastKey = "unbanip_broadcast";
                    break;
                default:
                    broadcastKey = null;
            }
            if (broadcastKey != null) {
                MessageUtil.broadcast(null, broadcastKey,
                        "sender", issuerName,
                        "target", p.getPlayerName(),
                        "id", p.getId());
            }

            try {
                if (eventManager != null) {
                    PunishmentRevokeEvent event = new PunishmentRevokeEvent(new PunishmentAdapter(p));
                    eventManager.callEvent(event);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to call PunishmentRevokeEvent: " + e.getMessage());
            }
            return;
        }

        Warning w = plugin.getDatabase().getWarningById(punishmentId);
        if (w != null) {
            plugin.getDatabase().pardonWarning(punishmentId, issuerName, reason);
            MessageUtil.broadcast(null, "unwarn_broadcast",
                    "sender", issuerName,
                    "target", w.getPlayerName(),
                    "id", w.getId());

            Player online = Bukkit.getPlayer(w.getPlayerUuid());
            if (online != null && online.isOnline()) {
                MessageUtil.send(online, "unwarn_notify",
                        "issuer", issuerName,
                        "id", w.getId());
            }

            try {
                if (eventManager != null) {
                    Punishment pAdapter = new Punishment();
                    pAdapter.setId(w.getId());
                    pAdapter.setPlayerUuid(w.getPlayerUuid());
                    pAdapter.setPlayerName(w.getPlayerName());
                    pAdapter.setIssuerUuid(w.getIssuerUuid());
                    pAdapter.setIssuerName(w.getIssuerName());
                    pAdapter.setType(PunishmentType.WARNING);
                    pAdapter.setReason(w.getReason());
                    pAdapter.setStartTime(w.getStartTime());
                    pAdapter.setEndTime(w.getEndTime());
                    pAdapter.setActive(w.isActive());
                    pAdapter.setServerName(w.getServerName());
                    PunishmentRevokeEvent event = new PunishmentRevokeEvent(new PunishmentAdapter(pAdapter));
                    eventManager.callEvent(event);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to call PunishmentRevokeEvent for warning: " + e.getMessage());
            }
            return;
        }

        JailPunishment j = plugin.getDatabase().getJailById(punishmentId);
        if (j != null) {
            j.setActive(false);
            plugin.getDatabase().updateJail(j);

            Player online = Bukkit.getPlayer(j.getPlayerUuid());
            if (online != null && online.isOnline()) {
                plugin.getJailManager().releaseFromJail(online, j);
                MessageUtil.send(online, "unjail_notify",
                        "issuer", issuerName);
            }

            MessageUtil.broadcast(null, "unjail_broadcast",
                    "sender", issuerName,
                    "target", j.getPlayerName(),
                    "id", j.getId());

            try {
                if (eventManager != null) {
                    Punishment pAdapter = new Punishment();
                    pAdapter.setId(j.getId());
                    pAdapter.setPlayerUuid(j.getPlayerUuid());
                    pAdapter.setPlayerName(j.getPlayerName());
                    pAdapter.setIssuerUuid(j.getIssuerUuid());
                    pAdapter.setIssuerName(j.getIssuerName());
                    pAdapter.setType(PunishmentType.JAIL);
                    pAdapter.setReason(j.getReason());
                    pAdapter.setStartTime(j.getStartTime());
                    pAdapter.setEndTime(j.getEndTime());
                    pAdapter.setActive(j.isActive());
                    pAdapter.setServerName(j.getServerName());
                    PunishmentRevokeEvent event = new PunishmentRevokeEvent(new PunishmentAdapter(pAdapter));
                    eventManager.callEvent(event);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to call PunishmentRevokeEvent for jail: " + e.getMessage());
            }
            return;
        }

        throw new PunishmentNotFoundException(punishmentId);
    }

    @Override
    public me.demro.dlibs.api.Punishment getPunishment(String id) {
        Punishment internal = plugin.getDatabase().getPunishmentById(id);
        if (internal != null) return new PunishmentAdapter(internal);
        Warning w = plugin.getDatabase().getWarningById(id);
        if (w != null) {
            Punishment p = new Punishment();
            p.setId(w.getId());
            p.setPlayerUuid(w.getPlayerUuid());
            p.setPlayerName(w.getPlayerName());
            p.setIssuerUuid(w.getIssuerUuid());
            p.setIssuerName(w.getIssuerName());
            p.setType(PunishmentType.WARNING);
            p.setReason(w.getReason());
            p.setStartTime(w.getStartTime());
            p.setEndTime(w.getEndTime());
            p.setActive(w.isActive());
            p.setServerName(w.getServerName());
            return new PunishmentAdapter(p);
        }
        JailPunishment j = plugin.getDatabase().getJailById(id);
        if (j != null) {
            Punishment p = new Punishment();
            p.setId(j.getId());
            p.setPlayerUuid(j.getPlayerUuid());
            p.setPlayerName(j.getPlayerName());
            p.setIssuerUuid(j.getIssuerUuid());
            p.setIssuerName(j.getIssuerName());
            p.setType(PunishmentType.JAIL);
            p.setReason(j.getReason());
            p.setStartTime(j.getStartTime());
            p.setEndTime(j.getEndTime());
            p.setActive(j.isActive());
            p.setServerName(j.getServerName());
            return new PunishmentAdapter(p);
        }
        return null;
    }

    @Override
    public List<me.demro.dlibs.api.Punishment> getActivePunishments(UUID targetUuid) {
        List<Punishment> internalList = plugin.getDatabase().getActivePunishmentsIncludingJail(targetUuid, plugin.getServerName(), plugin.getMode());
        return internalList.stream().map(PunishmentAdapter::new).collect(Collectors.toList());
    }

    @Override
    public List<me.demro.dlibs.api.Punishment> getHistory(UUID targetUuid) {
        List<Punishment> internalList = plugin.getDatabase().getPunishmentHistory(targetUuid, false);
        return internalList.stream().map(PunishmentAdapter::new).collect(Collectors.toList());
    }

    @Override
    public boolean isBanned(UUID targetUuid) {
        Punishment ban = plugin.getDatabase().getActivePunishment(
                targetUuid, PunishmentType.BAN, plugin.getServerName(), plugin.getMode()
        );
        return ban != null && ban.isActive() && !ban.isExpired();
    }

    @Override
    public boolean isMuted(UUID targetUuid) {
        Punishment mute = plugin.getDatabase().getActivePunishment(
                targetUuid, PunishmentType.MUTE, plugin.getServerName(), plugin.getMode()
        );
        return mute != null && mute.isActive() && !mute.isExpired();
    }

    @Override
    public CompletableFuture<String> punishAsync(UUID targetUuid, me.demro.dlibs.api.PunishmentType type, String reason,
                                                 Long duration, UUID issuerUuid, String issuerName,
                                                 String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return punish(targetUuid, type, reason, duration, issuerUuid, issuerName, serverName);
            } catch (PlayerNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
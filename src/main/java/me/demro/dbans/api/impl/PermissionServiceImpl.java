package me.demro.dbans.api.impl;

import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.permission.PermissionService;
import me.demro.dlibs.dbans.api.punishment.PunishmentType;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.*;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PermissionServiceImpl implements PermissionService {

    private final DBans plugin;

    public PermissionServiceImpl(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull CompletableFuture<Boolean> hasImmunity(@NotNull UUID playerUuid, @NotNull PunishmentType type) {
        // Map to internal type name for LimitsManager
        String typeName = type.name().toLowerCase();
        OfflinePlayer target = plugin.getPlayerCache().getOfflinePlayer(playerUuid);
        boolean immune = plugin.getLimitsManager().isImmune(target, typeName);
        return CompletableFuture.completedFuture(immune);
    }

    @Override
    public @NotNull CompletableFuture<Integer> priority(@NotNull UUID playerUuid) {
        OfflinePlayer target = plugin.getPlayerCache().getOfflinePlayer(playerUuid);
        int priority = plugin.getLimitsManager().getPriority(target);
        return CompletableFuture.completedFuture(priority);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canPunish(@NotNull UUID issuerUuid, @NotNull UUID targetUuid) {
        // Check if issuer is console
        if (issuerUuid.equals(UUID.nameUUIDFromBytes("CONSOLE".getBytes()))) {
            return CompletableFuture.completedFuture(true);
        }
        Player issuer = Bukkit.getPlayer(issuerUuid);
        if (issuer == null) return CompletableFuture.completedFuture(false);
        OfflinePlayer target = plugin.getPlayerCache().getOfflinePlayer(targetUuid);
        boolean can = plugin.getLimitsManager().canPunish(issuer, target);
        return CompletableFuture.completedFuture(can);
    }
}
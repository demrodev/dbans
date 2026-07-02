package me.demro.dbans.api.impl;

import me.demro.dbans.DBans;
import me.demro.dbans.model.PlayerInfo;
import me.demro.dlibs.dbans.api.player.PlayerAddress;
import me.demro.dlibs.dbans.api.player.PlayerService;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerServiceImpl implements PlayerService {

    private final DBans plugin;

    public PlayerServiceImpl(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull CompletableFuture<Optional<String>> findName(@NotNull UUID playerUuid) {
        // Try to get from DB
        PlayerInfo info = plugin.getDatabase().getPlayer(playerUuid);
        if (info != null && info.getName() != null) {
            return CompletableFuture.completedFuture(Optional.of(info.getName()));
        }
        // Fallback to Bukkit offline player
        var off = Bukkit.getOfflinePlayer(playerUuid);
        if (off.hasPlayedBefore() || off.isOnline()) {
            return CompletableFuture.completedFuture(Optional.of(off.getName()));
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> findUuid(@NotNull String playerName) {
        // Try from DB
        PlayerInfo info = plugin.getDatabase().getPlayerByName(playerName);
        if (info != null) {
            return CompletableFuture.completedFuture(Optional.of(info.getUuid()));
        }
        // Fallback to Bukkit
        var off = Bukkit.getOfflinePlayer(playerName);
        if (off.hasPlayedBefore() || off.isOnline()) {
            return CompletableFuture.completedFuture(Optional.of(off.getUniqueId()));
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public @NotNull CompletableFuture<Optional<PlayerAddress>> findLastKnownAddress(@NotNull UUID playerUuid) {
        PlayerInfo info = plugin.getDatabase().getPlayer(playerUuid);
        if (info != null && info.getIp() != null) {
            return CompletableFuture.completedFuture(Optional.of(
                    new PlayerAddress(info.getIp(), Instant.ofEpochMilli(info.getLastSeen()))
            ));
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
package me.demro.dbans.api.impl;

import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
public class PlayerServiceImpl implements PlayerService {

    private final DBans plugin;

    @Override
    public @NotNull CompletableFuture<Optional<String>> findName(@NotNull UUID playerUuid) {
        PlayerInfo info = plugin.getDatabase().getPlayer(playerUuid);
        if (info != null && info.getName() != null) {
            return CompletableFuture.completedFuture(Optional.of(info.getName()));
        }

        var off = Bukkit.getOfflinePlayer(playerUuid);
        if (off.hasPlayedBefore() || off.isOnline()) {
            return CompletableFuture.completedFuture(Optional.ofNullable(off.getName()));
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> findUuid(@NotNull String playerName) {
        PlayerInfo info = plugin.getDatabase().getPlayerByName(playerName);
        if (info != null) {
            return CompletableFuture.completedFuture(Optional.of(info.getUuid()));
        }

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
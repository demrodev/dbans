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
        final Optional<String> name;
        if (info != null && info.getName() != null) {
            name = Optional.of(info.getName());
        } else {
            var off = Bukkit.getOfflinePlayer(playerUuid);
            name = (off.hasPlayedBefore() || off.isOnline()) ? Optional.ofNullable(off.getName()) : Optional.empty();
        }
        return CompletableFuture.completedFuture(name);
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> findUuid(@NotNull String playerName) {
        PlayerInfo info = plugin.getDatabase().getPlayerByName(playerName);
        final Optional<UUID> uuid;
        if (info != null) {
            uuid = Optional.of(info.getUuid());
        } else {
            var off = Bukkit.getOfflinePlayer(playerName);
            uuid = (off.hasPlayedBefore() || off.isOnline()) ? Optional.of(off.getUniqueId()) : Optional.empty();
        }
        return CompletableFuture.completedFuture(uuid);
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
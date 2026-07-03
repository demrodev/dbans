package me.demro.dbans.command;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

record BanIpTarget(@NotNull String ipOrMask,
                   @Nullable String playerName,
                   @Nullable UUID playerUuid) {

    @Contract("_ -> new")
    static @NotNull BanIpTarget forAddress(@NotNull String ipOrMask) {
        return new BanIpTarget(ipOrMask, null, null);
    }

    @Contract("_, _, _ -> new")
    static @NotNull BanIpTarget forPlayer(@NotNull String ip,
                                          @NotNull String playerName,
                                          @NotNull UUID playerUuid
    ) {
        return new BanIpTarget(ip, playerName, playerUuid);
    }

    @Contract(pure = true)
    boolean representsPlayer() {
        return playerUuid != null;
    }

    @Contract(pure = true)
    @NotNull UUID requiredPlayerUuid() {
        return Objects.requireNonNull(playerUuid, "Player target must have a UUID");
    }

    @Contract(pure = true)
    @NotNull String displayName() {
        return playerName != null ? playerName : ipOrMask;
    }

    @NotNull UUID resolvedUuid() {
        return playerUuid != null
                ? playerUuid
                : UUID.nameUUIDFromBytes(ipOrMask.getBytes(StandardCharsets.UTF_8));
    }
}

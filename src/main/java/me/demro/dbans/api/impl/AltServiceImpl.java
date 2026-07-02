package me.demro.dbans.api.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.alt.AltAccount;
import me.demro.dlibs.dbans.api.alt.AltDetectionReason;
import me.demro.dlibs.dbans.api.alt.AltService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of {@link AltService} using DBans internal AltAccountManager.
 */
@Slf4j
@RequiredArgsConstructor
public class AltServiceImpl implements AltService {

    private final DBans plugin;

    @Override
    public @NotNull CompletableFuture<List<AltAccount>> findAlts(@NotNull UUID playerUuid) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerUuid);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<String> altNames = plugin.getAltAccountManager().findAltAccounts(target.getName());
        List<AltAccount> accounts = altNames.stream()
                                            .map(name -> Bukkit.getOfflinePlayer(name))
                                            .filter(p -> p.hasPlayedBefore() || p.isOnline())
                                            .map(p -> new AltAccount(p.getUniqueId(), AltDetectionReason.SHARED_IP))
                                            .collect(Collectors.toList());

        return CompletableFuture.completedFuture(accounts);
    }
}
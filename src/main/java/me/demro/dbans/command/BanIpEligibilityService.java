package me.demro.dbans.command;

import lombok.RequiredArgsConstructor;
import me.demro.dbans.DBans;
import me.demro.dbans.api.adapter.PunishmentAdapter;
import me.demro.dlibs.dbans.api.punishment.PunishmentType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
final class BanIpEligibilityService {

    private final DBans plugin;

    @Contract(pure = true)
    private static @NotNull BanIpEligibility resolveEligibility(boolean allowed,
                                                                boolean immune
    ) {
        BanIpEligibility eligibility;
        if (!allowed) {
            eligibility = BanIpEligibility.HIGHER_PRIORITY;
        } else if (immune) {
            eligibility = BanIpEligibility.IMMUNE;
        } else {
            eligibility = BanIpEligibility.ALLOWED;
        }
        return eligibility;
    }

    @NotNull CompletableFuture<BanIpEligibility> check(@NotNull CommandSender sender,
                                                       @NotNull BanIpTarget target
    ) {
        if (!target.representsPlayer()) {
            return CompletableFuture.completedFuture(BanIpEligibility.ALLOWED);
        }

        UUID targetUuid = target.requiredPlayerUuid();
        CompletableFuture<Boolean> canPunish = plugin.getApi().permissions()
                                                     .canPunish(senderUuid(sender), targetUuid);
        CompletableFuture<Boolean> hasImmunity = plugin.getApi().permissions()
                                                       .hasImmunity(targetUuid, PunishmentType.IP_BAN);

        return canPunish.thenCombine(hasImmunity, BanIpEligibilityService::resolveEligibility);
    }

    private @NotNull UUID senderUuid(@NotNull CommandSender sender) {
        return sender instanceof Player player
                ? player.getUniqueId()
                : PunishmentAdapter.CONSOLE_UUID;
    }
}

package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import me.demro.dlibs.dbans.api.exception.PlayerNotFoundException;
import me.demro.dlibs.dbans.api.player.PlayerIdentity;
import me.demro.dlibs.dbans.api.punishment.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static java.util.Objects.requireNonNull;

@Slf4j
public class BanIpCommand implements CommandExecutor {

    private static final String COMMAND_NAME = "banip";
    private static final String PERMISSION = "dbans.banip";

    private final DBans plugin;
    private final BanIpTargetResolver targetResolver;
    private final BanIpEligibilityService eligibilityService;

    public BanIpCommand(DBans plugin) {
        this.plugin = plugin;
        this.targetResolver = new BanIpTargetResolver(plugin);
        this.eligibilityService = new BanIpEligibilityService(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String @NotNull [] args
    ) {
        BasePunishCommand.ParsedArgs parsed = BasePunishCommand.parseArgs(args);

        if (!isInitiallyBlocked(sender, parsed)) {
            List<String> filteredArgs = parsed.filteredArgs();
            String reason = filteredArgs.size() > 1
                    ? String.join(" ", filteredArgs.subList(1, filteredArgs.size()))
                    : "Не указана";

            targetResolver.resolve(sender, filteredArgs.getFirst())
                          .ifPresent(target -> executeBan(new BanExecution(
                                  sender,
                                  parsed.targetServer(),
                                  parsed.silent(),
                                  target,
                                  reason
                          )));
        }
        return true;
    }

    private boolean isInitiallyBlocked(@NotNull CommandSender sender,
                                       @NotNull BasePunishCommand.ParsedArgs parsed
    ) {
        boolean blocked;
        if (!sender.hasPermission(PERMISSION)) {
            MessageUtil.send(sender, "no_permission");
            blocked = true;
        } else if (parsed.filteredArgs().isEmpty()) {
            MessageUtil.send(sender, "usage_banip");
            blocked = true;
        } else if (parsed.targetServer() != null && !hasServerPermission(sender)) {
            MessageUtil.send(sender, "no_server_permission", "command", COMMAND_NAME);
            blocked = true;
        } else {
            blocked = false;
        }
        return blocked;
    }

    private void executeBan(@NotNull BanExecution execution) {
        CommandSender sender = execution.sender();
        BanIpTarget target = execution.target();
        String targetServer = execution.targetServer();

        if (plugin.getDatabase().isIpBanned(target.ipOrMask())) {
            MessageUtil.send(sender, "ip_banned_already", "target", target.displayName());
        } else if (isOnCooldown(sender)) {
            log.debug("IP ban command rejected because {} is on cooldown", sender.getName());
        } else if (!isServerArgumentSupported(targetServer)) {
            MessageUtil.send(sender, "server_argument_not_supported");
        } else if (target.representsPlayer()
                   && plugin.getSelfPunishChecker().isSelfPunish(sender, target.displayName())) {
            log.debug("IP ban command rejected because {} targeted themselves", sender.getName());
        } else {
            eligibilityService.check(sender, target)
                              .whenComplete((eligibility, throwable) -> runOnMainThread(() -> {
                                  if (throwable != null) {
                                      handleEligibilityFailure(execution.sender(), throwable);
                                  } else {
                                      handleEligibility(execution, requireNonNull(eligibility, "eligibility"));
                                  }
                              }));
        }
    }

    private void handleEligibility(@NotNull BanExecution execution,
                                   @NotNull BanIpEligibility eligibility
    ) {
        switch (eligibility) {
            case ALLOWED -> createBan(execution);
            case HIGHER_PRIORITY -> MessageUtil.send(
                    execution.sender(),
                    "cannot_punish_higher_priority",
                    "target",
                    execution.target().displayName()
            );
            case IMMUNE -> MessageUtil.send(
                    execution.sender(),
                    "target_immune_permission",
                    "target",
                    execution.target().displayName()
            );
        }
    }

    private void createBan(@NotNull BanExecution execution) {
        CommandSender sender = execution.sender();
        String targetServer = execution.targetServer();
        if (sender instanceof Player player) {
            plugin.getLimitsManager().setCooldown(player, COMMAND_NAME);
        }

        String finalServer = targetServer != null ? targetServer : plugin.getServerName();
        plugin.getApi().punishments()
              .create(buildRequest(execution, finalServer))
              .whenComplete((result, throwable) -> runOnMainThread(
                      () -> handleResult(execution, throwable)
              ));
    }

    @Contract("_, _ -> new")
    private @NotNull PunishmentCreateRequest buildRequest(@NotNull BanExecution execution,
                                                          @NotNull String finalServer
    ) {
        CommandSender sender = execution.sender();
        BanIpTarget target = execution.target();
        return PunishmentCreateRequest.builder()
                                      .target(PlayerIdentity.of(target.resolvedUuid(), target.displayName()))
                                      .type(PunishmentType.IP_BAN)
                                      .reason(PunishmentReason.of(execution.reason()))
                                      .duration(PunishmentDuration.permanent())
                                      .issuer(sender instanceof Player player
                                                      ? PunishmentIssuer.player(player.getUniqueId(), player.getName())
                                                      : PunishmentIssuer.console())
                                      .serverName(finalServer)
                                      .options(PunishmentOptions.builder()
                                                                .silent(execution.silent())
                                                                .broadcast(!execution.silent())
                                                                .notifyTarget(target.representsPlayer())
                                                                .build())
                                      .build();
    }

    private void handleResult(@NotNull BanExecution execution,
                              @Nullable Throwable throwable
    ) {
        CommandSender sender = execution.sender();
        BanIpTarget target = execution.target();
        if (throwable == null) {
            log.info("IP {} banned by {}", target.ipOrMask(), sender.getName());
        } else {
            Throwable cause = unwrap(throwable);
            if (cause instanceof PlayerNotFoundException) {
                MessageUtil.send(sender, "player_not_found", "target", target.displayName());
            } else {
                MessageUtil.send(sender, "error_creating_punishment", "error", exceptionMessage(cause));
                log.error("Error creating IP ban for {}", target.ipOrMask(), cause);
            }
        }
    }

    private void handleEligibilityFailure(@NotNull CommandSender sender,
                                          @NotNull Throwable throwable
    ) {
        Throwable cause = unwrap(throwable);
        MessageUtil.send(sender, "error_creating_punishment", "error", exceptionMessage(cause));
        log.error("Error checking IP ban eligibility", cause);
    }

    private @NotNull String exceptionMessage(@NotNull Throwable throwable) {
        return Optional.ofNullable(throwable.getMessage())
                       .orElse(throwable.getClass().getSimpleName());
    }

    private @NotNull Throwable unwrap(@NotNull Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private void runOnMainThread(@NotNull Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private boolean hasServerPermission(@NotNull CommandSender sender) {
        return sender.hasPermission("dbans.server.bypass") || sender.hasPermission("dbans.server.banip");
    }

    private boolean isServerArgumentSupported(@Nullable String targetServer) {
        String mode = plugin.getMode();
        return targetServer == null
               || mode.equalsIgnoreCase("sync")
               || mode.equalsIgnoreCase("sync_static");
    }

    private boolean isOnCooldown(@NotNull CommandSender sender) {
        boolean onCooldown = sender instanceof Player player
                             && plugin.getLimitsManager().isOnCooldown(player, COMMAND_NAME);
        if (onCooldown) {
            Player player = (Player) sender;
            int remaining = plugin.getLimitsManager().getRemainingCooldown(player, COMMAND_NAME);
            MessageUtil.send(sender, "command_on_cooldown",
                             "command", COMMAND_NAME,
                             "time", String.valueOf(remaining));
        }
        return onCooldown;
    }

    private record BanExecution(@NotNull CommandSender sender,
                                @Nullable String targetServer,
                                boolean silent,
                                @NotNull BanIpTarget target,
                                @NotNull String reason) {

    }

}

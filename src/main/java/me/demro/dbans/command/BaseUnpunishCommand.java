package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import me.demro.dlibs.dbans.api.exception.PunishmentNotFoundException;
import me.demro.dlibs.dbans.api.punishment.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class BaseUnpunishCommand implements CommandExecutor {

    protected final DBans plugin;

    @Contract(pure = true)
    protected BaseUnpunishCommand(DBans plugin) {
        this.plugin = plugin;
    }

    protected abstract PunishmentType getType(); // новый тип

    protected abstract String getFullPermission();

    protected abstract String getOwnPermission();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label,
                             String[] args
    ) {
        boolean hasFull = sender.hasPermission(getFullPermission());
        boolean hasOwn = sender.hasPermission(getOwnPermission());
        if (!hasFull && !hasOwn) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "usage_" + cmd.getName().toLowerCase());
            return true;
        }

        String input = args[0];

        // Если указан ID с #
        if (input.startsWith("#")) {
            String id = input.substring(1);
            PunishmentId punishmentId = PunishmentId.of(id);

            // Проверяем существование через новый API
            CompletableFuture<Optional<Punishment>> future = plugin.getApi().punishments().findById(punishmentId);
            Optional<Punishment> opt = future.join();
            if (opt.isEmpty()) {
                MessageUtil.send(sender, "punishment_not_found", "id", id);
                return true;
            }

            Punishment punishment = opt.get();
            if (punishment.type() != getType()) {
                MessageUtil.send(sender, "punishment_not_found", "id", id);
                return true;
            }

            if (!hasFull && sender instanceof Player p) {
                if (!punishment.targetUuid().equals(p.getUniqueId())) {
                    MessageUtil.send(sender, "cannot_unpunish_others", "type", getType().name().toLowerCase());
                    return true;
                }
            }

            // Отмена через новый API
            PunishmentRevokeRequest revokeRequest = new PunishmentRevokeRequest(
                    punishmentId,
                    sender instanceof Player
                            ? PunishmentIssuer.player(((Player) sender).getUniqueId(), sender.getName())
                            : PunishmentIssuer.console(),
                    PunishmentReason.of("Снятие наказания"),
                    plugin.getServerName(),
                    PunishmentOptions.defaults()
            );
            plugin.getApi().punishments().revoke(revokeRequest)
                  .whenComplete((v, ex) -> {
                      if (ex != null) {
                          if (ex instanceof PunishmentNotFoundException) {
                              MessageUtil.send(sender, "punishment_not_found", "id", id);
                          } else {
                              MessageUtil.send(sender, "error_revoking_punishment", "error", ex.getMessage());
                              log.error("Error revoking punishment", ex);
                          }
                      } else {
                          MessageUtil.send(sender, "punishment_revoked", "id", id);
                          log.info("Punishment {} revoked by {}", id, sender.getName());
                      }
                  });
            return true;
        }

        // Иначе - по имени игрока
        OfflinePlayer target = Bukkit.getOfflinePlayer(input);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", input);
            return true;
        }

        if (!hasFull && sender instanceof Player p) {
            if (!target.getUniqueId().equals(p.getUniqueId())) {
                MessageUtil.send(sender, "cannot_unpunish_others", "type", getType().name().toLowerCase());
                return true;
            }
        }

        // Проверяем активное наказание через новый API
        CompletableFuture<Boolean> hasActiveFuture = plugin.getApi().punishments().hasActive(target.getUniqueId(), getType());
        if (!hasActiveFuture.join()) {
            String notPunishedKey = switch (getType()) {
                case BAN -> "not_banned";
                case MUTE -> "not_muted";
                case JAIL -> "not_jailed";
                case WARNING -> "not_warned";
                case IP_BAN -> "ip_not_banned_for_player";
                default -> "not_punished";
            };
            MessageUtil.send(sender, notPunishedKey, "target", target.getName());
            return true;
        }

        // Найдём ID активного наказания - через find с query
        PunishmentQuery query = PunishmentQuery.builder()
                                               .targetUuid(target.getUniqueId())
                                               .type(getType())
                                               .status(PunishmentStatus.ACTIVE)
                                               .limit(1)
                                               .build();
        CompletableFuture<List<Punishment>> listFuture = plugin.getApi().punishments().find(query);
        List<Punishment> list = listFuture.join();
        if (list.isEmpty()) {
            MessageUtil.send(sender, "punishment_not_found", "id", "unknown");
            return true;
        }
        Punishment punishment = list.get(0);
        PunishmentId punishmentId = punishment.id();

        PunishmentRevokeRequest revokeRequest = new PunishmentRevokeRequest(
                punishmentId,
                sender instanceof Player
                        ? PunishmentIssuer.player(((Player) sender).getUniqueId(), sender.getName())
                        : PunishmentIssuer.console(),
                PunishmentReason.of("Снятие наказания"),
                plugin.getServerName(),
                PunishmentOptions.defaults()
        );
        plugin.getApi().punishments().revoke(revokeRequest)
              .whenComplete((v, ex) -> {
                  if (ex != null) {
                      if (ex instanceof PunishmentNotFoundException) {
                          MessageUtil.send(sender, "punishment_not_found", "id", punishmentId.value());
                      } else {
                          MessageUtil.send(sender, "error_revoking_punishment", "error", ex.getMessage());
                          log.error("Error revoking punishment", ex);
                      }
                  } else {
                      MessageUtil.send(sender, "punishment_revoked", "id", punishmentId.value());
                      log.info("Punishment {} revoked by {}", punishmentId.value(), sender.getName());
                  }
              });
        return true;
    }
}
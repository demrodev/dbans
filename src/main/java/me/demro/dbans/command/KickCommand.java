package me.demro.dbans.command;

import lombok.CustomLog;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import me.demro.dlibs.dbans.api.exception.PlayerNotFoundException;
import me.demro.dlibs.dbans.api.player.PlayerIdentity;
import me.demro.dlibs.dbans.api.punishment.*;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CustomLog
public class KickCommand extends BasePunishCommand {

    public KickCommand(DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.KICK;
    }

    @Override
    protected String getPermission() {
        return "dbans.kick";
    }

    @Override
    protected boolean isPermanent() {
        return true;
    }

    @Override
    protected void executePunishment(CommandSender sender, OfflinePlayer target, String reason, String finalServer,
                                     boolean silent, Long duration
    ) {
        PunishmentCreateRequest request = PunishmentCreateRequest.builder()
                                                                 .target(PlayerIdentity.of(target.getUniqueId(), target.getName()))
                                                                 .type(PunishmentType.KICK)
                                                                 .reason(PunishmentReason.of(reason))
                                                                 .duration(PunishmentDuration.instant())
                                                                 .issuer(sender instanceof Player
                                                                                 ? PunishmentIssuer.player(((Player) sender).getUniqueId(), sender.getName())
                                                                                 : PunishmentIssuer.console())
                                                                 .serverName(finalServer)
                                                                 .options(PunishmentOptions.builder()
                                                                                           .silent(silent)
                                                                                           .broadcast(!silent)
                                                                                           .notifyTarget(true)
                                                                                           .build())
                                                                 .build();

        plugin.getApi().punishments().create(request)
              .whenComplete((result, ex) -> {
                  if (ex != null) {
                      if (ex instanceof PlayerNotFoundException) {
                          MessageUtil.send(sender, "player_not_found", "target", target.getName());
                      } else {
                          MessageUtil.send(sender, "error_creating_punishment", "error", ex.getMessage());
                          log.error("Error kicking player", ex);
                      }
                  } else {
                      log.info("Player {} kicked by {}", target.getName(), sender.getName());
                  }
              });
    }
}
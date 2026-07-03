package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import me.demro.dlibs.dbans.api.exception.PlayerNotFoundException;
import me.demro.dlibs.dbans.api.player.PlayerIdentity;
import me.demro.dlibs.dbans.api.punishment.*;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

@Slf4j
public class TempBanCommand extends BasePunishCommand {

    public TempBanCommand(DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.BAN;
    }

    @Override
    protected String getPermission() {
        return "dbans.tempban";
    }

    @Override
    protected boolean isPermanent() {
        return false;
    }

    @Override
    protected void executePunishment(CommandSender sender, OfflinePlayer target, String reason, String finalServer,
                                     boolean silent, Long duration
    ) {
        if (duration == null || duration <= 0) {
            MessageUtil.send(sender, "invalid_time");
            return;
        }

        PunishmentCreateRequest request = PunishmentCreateRequest.builder()
                                                                 .target(PlayerIdentity.of(target.getUniqueId(), target.getName()))
                                                                 .type(PunishmentType.BAN)
                                                                 .reason(PunishmentReason.of(reason))
                                                                 .duration(PunishmentDuration.temporary(Duration.ofMillis(duration)))
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
                      // обработка ошибок
                      if (ex instanceof PlayerNotFoundException) {
                          MessageUtil.send(sender, "player_not_found", "target", target.getName());
                      } else {
                          MessageUtil.send(sender, "error_creating_punishment", "error", ex.getMessage());
                          log.error("Error creating tempban", ex);
                      }
                  } else {
                      log.info("Player {} tempbanned by {} for {}", target.getName(), sender.getName(), TimeUtil.formatDuration(duration));
                  }
              });
    }
}
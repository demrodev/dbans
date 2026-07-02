package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.exception.PlayerNotFoundException;
import me.demro.dlibs.dbans.api.player.PlayerIdentity;
import me.demro.dlibs.dbans.api.punishment.*;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Slf4j
public class MuteCommand extends BasePunishCommand {

    public MuteCommand(DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.MUTE;
    }

    @Override
    protected String getPermission() {
        return "dbans.mute";
    }

    @Override
    protected boolean isPermanent() {
        return true;
    }

    @Override
    protected void executePunishment(CommandSender sender, OfflinePlayer target, String reason, String finalServer, boolean silent, Long duration) {
        PunishmentCreateRequest request = PunishmentCreateRequest.builder()
                .target(PlayerIdentity.of(target.getUniqueId(), target.getName()))
                .type(PunishmentType.MUTE)
                .reason(PunishmentReason.of(reason))
                .duration(PunishmentDuration.permanent())
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
                            log.error("Error creating mute", ex);
                        }
                    } else {
                        log.info("Player {} muted by {}", target.getName(), sender.getName());
                    }
                });
    }
}
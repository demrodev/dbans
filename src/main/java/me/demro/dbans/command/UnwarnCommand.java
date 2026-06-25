package me.demro.dbans.command;

import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.model.Warning;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class UnwarnCommand extends BaseUnpunishCommand {

    public UnwarnCommand(DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.WARNING;
    }

    @Override
    protected String getFullPermission() {
        return "dbans.unwarn";
    }

    @Override
    protected String getOwnPermission() {
        return "dbans.unwarn.own";
    }

    @Override
    protected Punishment getActivePunishment(UUID targetUuid) {
        Warning warning = plugin.getDatabase().getActiveWarning(targetUuid);
        if (warning == null) return null;
        Punishment p = new Punishment();
        p.setId(warning.getId());
        p.setPlayerUuid(warning.getPlayerUuid());
        p.setPlayerName(warning.getPlayerName());
        p.setIssuerUuid(warning.getIssuerUuid());
        p.setIssuerName(warning.getIssuerName());
        p.setType(PunishmentType.WARNING);
        p.setReason(warning.getReason());
        p.setStartTime(warning.getStartTime());
        p.setEndTime(warning.getEndTime());
        p.setActive(warning.isActive());
        p.setServerName(warning.getServerName());
        p.setPardonedBy(warning.getPardonedBy());
        p.setPardonedAt(warning.getPardonedAt());
        return p;
    }

    @Override
    protected void pardon(Punishment punishment, CommandSender sender) {
        Warning warning = plugin.getDatabase().getWarningById(punishment.getId());
        if (warning != null && warning.isActive()) {
            warning.setActive(false);
            plugin.getDatabase().updateWarning(warning);
            if (plugin.getProxySyncManager() != null) {
                plugin.getProxySyncManager().sendPunishmentRevoke(warning);
            }
            Player online = Bukkit.getPlayer(warning.getPlayerUuid());
            if (online != null && online.isOnline()) {
                MessageUtil.send(online, "unwarn_notify", "issuer", sender.getName(), "id", warning.getId());
            }
        }
    }

    @Override
    protected String getBroadcastKey() {
        return "unwarn_broadcast";
    }

    @Override
    protected String getBroadcastPermission() {
        return "dbans.notify.unwarn";
    }
}
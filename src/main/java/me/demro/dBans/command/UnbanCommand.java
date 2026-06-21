package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class UnbanCommand extends BaseUnpunishCommand {

    public UnbanCommand(DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.BAN;
    }

    @Override
    protected String getFullPermission() {
        return "dbans.unban";
    }

    @Override
    protected String getOwnPermission() {
        return "dbans.unban.own";
    }

    @Override
    protected Punishment getActivePunishment(UUID targetUuid) {
        return plugin.getDatabase().getActivePunishment(targetUuid, PunishmentType.BAN,
                plugin.getServerName(), plugin.getMode());
    }

    @Override
    protected void pardon(Punishment punishment, CommandSender sender) {
        plugin.getDatabase().pardonPunishment(punishment.getId(), sender.getName(), "Снятие наказания");
    }

    @Override
    protected String getBroadcastKey() {
        return "unban_broadcast";
    }

    @Override
    protected String getBroadcastPermission() {
        return "dbans.notify.unban";
    }
}
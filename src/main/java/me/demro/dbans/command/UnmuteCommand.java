package me.demro.dbans.command;

import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class UnmuteCommand extends BaseUnpunishCommand {

    public UnmuteCommand(DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.MUTE;
    }

    @Override
    protected String getFullPermission() {
        return "dbans.unmute";
    }

    @Override
    protected String getOwnPermission() {
        return "dbans.unmute.own";
    }

    @Override
    protected Punishment getActivePunishment(UUID targetUuid) {
        return plugin.getDatabase().getActivePunishment(targetUuid, PunishmentType.MUTE,
                plugin.getServerName(), plugin.getMode());
    }

    @Override
    protected void pardon(Punishment punishment, CommandSender sender) {
        plugin.getDatabase().pardonPunishment(punishment.getId(), sender.getName(), "Снятие наказания");
    }

    @Override
    protected String getBroadcastKey() {
        return "unmute_broadcast";
    }

    @Override
    protected String getBroadcastPermission() {
        return "dbans.notify.unmute";
    }
}
package me.demro.dbans.command;

import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.punishment.PunishmentType;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class UnjailCommand extends BaseUnpunishCommand {

    public UnjailCommand(DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.JAIL;
    }

    @Override
    protected String getFullPermission() {
        return "dbans.unjail";
    }

    @Override
    protected String getOwnPermission() {
        return "dbans.unjail.own";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("jail.enabled", true)) {
            MessageUtil.send(sender, "jail_disabled");
            return true;
        }
        return super.onCommand(sender, cmd, label, args);
    }
}
package me.demro.dbans.command;

import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.punishment.PunishmentType;

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
}
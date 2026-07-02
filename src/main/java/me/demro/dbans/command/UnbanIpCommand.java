package me.demro.dbans.command;

import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.punishment.PunishmentType;

public class UnbanIpCommand extends BaseUnpunishCommand {

    public UnbanIpCommand(DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.IP_BAN;
    }

    @Override
    protected String getFullPermission() {
        return "dbans.unbanip";
    }

    @Override
    protected String getOwnPermission() {
        return "dbans.unbanip.own";
    }
}
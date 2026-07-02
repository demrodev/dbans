package me.demro.dbans.command;

import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.punishment.PunishmentType;

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
}
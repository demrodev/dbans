package me.demro.dbans.command;

import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.punishment.PunishmentType;

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
}
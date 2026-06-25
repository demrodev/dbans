package me.demro.dbans.command;

import me.demro.dbans.DBans;
import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

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
    protected Punishment getActivePunishment(UUID targetUuid) {
        JailPunishment jail = plugin.getDatabase().getActiveJail(targetUuid);
        if (jail == null) return null;
        Punishment p = new Punishment();
        p.setId(jail.getId());
        p.setPlayerUuid(jail.getPlayerUuid());
        p.setPlayerName(jail.getPlayerName());
        p.setIssuerUuid(jail.getIssuerUuid());
        p.setIssuerName(jail.getIssuerName());
        p.setType(PunishmentType.JAIL);
        p.setReason(jail.getReason());
        p.setStartTime(jail.getStartTime());
        p.setEndTime(jail.getEndTime());
        p.setActive(jail.isActive());
        p.setServerName(jail.getServerName());
        p.setPardonedBy(jail.getPardonedBy());
        p.setPardonedAt(jail.getPardonedAt());
        return p;
    }

    @Override
    protected void pardon(Punishment punishment, CommandSender sender) {
        JailPunishment jail = plugin.getDatabase().getActiveJail(punishment.getPlayerUuid());
        if (jail != null && jail.isActive()) {
            jail.setActive(false);
            plugin.getDatabase().updateJail(jail);
            if (plugin.getProxySyncManager() != null) {
                plugin.getProxySyncManager().sendPunishmentRevoke(jail);
            }
            Player online = Bukkit.getPlayer(jail.getPlayerUuid());
            if (online != null && online.isOnline()) {
                plugin.getJailManager().releaseFromJail(online, jail);
                MessageUtil.send(online, "unjail_notify", "issuer", sender.getName());
            }
        }
    }

    @Override
    protected String getBroadcastKey() {
        return "unjail_broadcast";
    }

    @Override
    protected String getBroadcastPermission() {
        return "dbans.notify.unjail";
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("jail.enabled", true)) {
            MessageUtil.send(sender, "jail_disabled");
            return true;
        }
        return super.onCommand(sender, cmd, label, args);
    }
}
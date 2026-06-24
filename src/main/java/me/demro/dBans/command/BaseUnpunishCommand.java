package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.model.JailPunishment;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.model.Warning;
import me.demro.dBans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class BaseUnpunishCommand implements CommandExecutor {
    protected final DBans plugin;

    protected BaseUnpunishCommand(DBans plugin) {
        this.plugin = plugin;
    }

    protected abstract PunishmentType getType();
    protected abstract String getFullPermission();
    protected abstract String getOwnPermission();
    protected abstract Punishment getActivePunishment(UUID targetUuid);
    protected abstract void pardon(Punishment punishment, CommandSender sender);
    protected abstract String getBroadcastKey();
    protected abstract String getBroadcastPermission();

    protected void removeIpBan(Punishment punishment) {}

    private Punishment findPunishmentById(String id) {
        Punishment p = plugin.getDatabase().getPunishmentById(id);
        if (p != null) return p;

        JailPunishment jail = plugin.getDatabase().getJailById(id);
        if (jail != null) {
            Punishment result = new Punishment();
            result.setId(jail.getId());
            result.setPlayerUuid(jail.getPlayerUuid());
            result.setPlayerName(jail.getPlayerName());
            result.setIssuerUuid(jail.getIssuerUuid());
            result.setIssuerName(jail.getIssuerName());
            result.setType(PunishmentType.JAIL);
            result.setReason(jail.getReason());
            result.setStartTime(jail.getStartTime());
            result.setEndTime(jail.getEndTime());
            result.setActive(jail.isActive());
            result.setServerName(jail.getServerName());
            result.setPardonedBy(jail.getPardonedBy());
            result.setPardonedAt(jail.getPardonedAt());
            return result;
        }

        Warning warning = plugin.getDatabase().getWarningById(id);
        if (warning != null) {
            Punishment result = new Punishment();
            result.setId(warning.getId());
            result.setPlayerUuid(warning.getPlayerUuid());
            result.setPlayerName(warning.getPlayerName());
            result.setIssuerUuid(warning.getIssuerUuid());
            result.setIssuerName(warning.getIssuerName());
            result.setType(PunishmentType.WARNING);
            result.setReason(warning.getReason());
            result.setStartTime(warning.getStartTime());
            result.setEndTime(warning.getEndTime());
            result.setActive(warning.isActive());
            result.setServerName(warning.getServerName());
            result.setPardonedBy(warning.getPardonedBy());
            result.setPardonedAt(warning.getPardonedAt());
            return result;
        }

        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        boolean hasFull = sender.hasPermission(getFullPermission());
        boolean hasOwn = sender.hasPermission(getOwnPermission());
        if (!hasFull && !hasOwn) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "usage_" + cmd.getName().toLowerCase());
            return true;
        }

        String input = args[0];

        if (input.startsWith("#")) {
            String id = input.substring(1);
            Punishment punishment = findPunishmentById(id);

            if (punishment == null || punishment.getType() != getType()) {
                MessageUtil.send(sender, "punishment_not_found", "id", id);
                return true;
            }

            if (!hasFull && sender instanceof Player) {
                Player p = (Player) sender;
                if (!punishment.getPlayerUuid().equals(p.getUniqueId())) {
                    MessageUtil.send(sender, "cannot_unpunish_others", "type", getType().name().toLowerCase());
                    return true;
                }
            }

            if (getType() == PunishmentType.IPBAN) {
                removeIpBan(punishment);
            }
            if (getType() == PunishmentType.MUTE) {
                plugin.cancelMuteExpiry(id);
            }

            pardon(punishment, sender);
            MessageUtil.broadcast(getBroadcastPermission(), getBroadcastKey(),
                    "sender", sender.getName(),
                    "target", punishment.getPlayerName(),
                    "id", id);
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(input);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", input);
            return true;
        }

        if (!hasFull && sender instanceof Player) {
            Player p = (Player) sender;
            if (!target.getUniqueId().equals(p.getUniqueId())) {
                MessageUtil.send(sender, "cannot_unpunish_others", "type", getType().name().toLowerCase());
                return true;
            }
        }

        Punishment active = getActivePunishment(target.getUniqueId());
        if (active == null || active.isExpired()) {
            String notPunishedKey = switch (getType()) {
                case BAN -> "not_banned";
                case MUTE -> "not_muted";
                case JAIL -> "not_jailed";
                case WARNING -> "not_warned";
                default -> "not_punished";
            };
            MessageUtil.send(sender, notPunishedKey, "target", target.getName());
            return true;
        }

        if (getType() == PunishmentType.MUTE) {
            plugin.cancelMuteExpiry(active.getId());
        }
        if (getType() == PunishmentType.IPBAN) {
            removeIpBan(active);
        }
        pardon(active, sender);
        if (plugin.getProxySyncManager() != null && active != null) {
            plugin.getProxySyncManager().sendPunishmentRevoke(active);
        }
        MessageUtil.broadcast(getBroadcastPermission(), getBroadcastKey(),
                "sender", sender.getName(),
                "target", target.getName(),
                "id", active.getId());
        return true;

    }
}
package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.regex.Pattern;

public class UnbanIpCommand extends BaseUnpunishCommand {
    private static final Pattern IP_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IP_MASK_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\*$");

    public UnbanIpCommand(DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.IPBAN;
    }

    @Override
    protected String getFullPermission() {
        return "dbans.unbanip";
    }

    @Override
    protected String getOwnPermission() {
        return "dbans.unbanip.own";
    }

    @Override
    protected Punishment getActivePunishment(UUID targetUuid) {
        return plugin.getDatabase().getActivePunishment(targetUuid, PunishmentType.IPBAN,
                plugin.getServerName(), plugin.getMode());
    }

    @Override
    protected void removeIpBan(Punishment punishment) {
        plugin.getDatabase().removeIpBanByPlayer(punishment.getPlayerUuid());
    }

    @Override
    protected void pardon(Punishment punishment, CommandSender sender) {
        plugin.getDatabase().pardonPunishment(punishment.getId(), sender.getName(), "Снятие IP-бана");
    }

    @Override
    protected String getBroadcastKey() {
        return "unbanip_broadcast";
    }

    @Override
    protected String getBroadcastPermission() {
        return "dbans.notify.unbanip";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission(getFullPermission())) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "usage_unbanip");
            return true;
        }
        String input = args[0];

        if (IP_PATTERN.matcher(input).matches() || IP_MASK_PATTERN.matcher(input).matches()) {
            plugin.getDatabase().removeIpBan(input);
            MessageUtil.send(sender, "ip_unbanned", "target", input);
            MessageUtil.broadcast(getBroadcastPermission(), getBroadcastKey(),
                    "sender", sender.getName(),
                    "target", input,
                    "id", "N/A");
            return true;
        }

        if (input.startsWith("#")) {
            String id = input.substring(1);
            Punishment punishment = plugin.getDatabase().getPunishmentById(id);
            if (punishment == null || punishment.getType() != PunishmentType.IPBAN) {
                MessageUtil.send(sender, "punishment_not_found", "id", id);
                return true;
            }
            if (sender instanceof Player && !sender.hasPermission("punishment.unbanip.others")) {
                MessageUtil.send(sender, "cannot_unban_others");
                return true;
            }
            removeIpBan(punishment);
            pardon(punishment, sender);
            MessageUtil.send(sender, "ip_unbanned", "target", punishment.getPlayerName());
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
        if (sender instanceof Player && !sender.hasPermission("dbans.unbanip")) {
            MessageUtil.send(sender, "cannot_unban_others");
            return true;
        }
        Punishment active = getActivePunishment(target.getUniqueId());
        if (active == null || active.isExpired()) {
            MessageUtil.send(sender, "ip_not_banned_for_player", "target", target.getName());
            return true;
        }
        removeIpBan(active);
        pardon(active, sender);
        MessageUtil.send(sender, "ip_unbanned", "target", target.getName());
        MessageUtil.broadcast(getBroadcastPermission(), getBroadcastKey(),
                "sender", sender.getName(),
                "target", target.getName(),
                "id", active.getId());
        return true;
    }
}
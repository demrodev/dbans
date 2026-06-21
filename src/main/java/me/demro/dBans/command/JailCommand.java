package me.demro.dBans.command;

import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.util.MessageUtil;
import me.demro.dBans.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JailCommand extends BasePunishCommand {

    public JailCommand(me.demro.dBans.DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.JAIL;
    }

    @Override
    protected String getPermission() {
        return "dbans.jail";
    }

    @Override
    protected boolean isPermanent() {
        return false;
    }

    @Override
    protected Long parseDuration(String[] args, int startIndex) {
        if (args.length > startIndex && TimeUtil.isTimeFormat(args[startIndex])) {
            try {
                return TimeUtil.parseDuration(args[startIndex]);
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    @Override
    protected void executePunishment(CommandSender sender, OfflinePlayer target, String reason, String finalServer, boolean silent, Long duration) {
        Player online = target.getPlayer();
        if (online == null || !online.isOnline()) {
            MessageUtil.send(sender, "player_not_online", "target", target.getName());
            return;
        }

        if (sender instanceof Player) {
            Player issuer = (Player) sender;
            long maxDuration = plugin.getLimitsManager().getMaxDuration(issuer, "jail");
            if (maxDuration > 0 && duration != null && duration > maxDuration) {
                String group = plugin.getLuckPermsHook().getPrimaryGroup(issuer);
                MessageUtil.send(sender, "limit_exceed", "max", TimeUtil.formatDuration(maxDuration), "group", group);
                return;
            }
        }

        Location previousLocation = online.getLocation().clone();
        String issuerName = sender.getName();

        String jailId = plugin.getJailManager().sendToJail(online, duration, previousLocation, issuerName, reason);
        if (jailId == null) {
            MessageUtil.send(sender, "jail_spawn_failed");
            return;
        }

        String durationStr = (duration != null && duration > 0) ? TimeUtil.formatDuration(duration) : "навсегда";
        MessageUtil.send(online, "jail_notify",
                "sender", issuerName, "reason", reason,
                "server", finalServer, "duration", durationStr, "id", jailId);

        String permission = silent ? null : "dbans.notify.jail";
        MessageUtil.broadcast(permission, "jail_broadcast",
                "sender", issuerName, "target", target.getName(),
                "reason", reason, "duration", durationStr,
                "server", finalServer, "id", jailId);
    }
}
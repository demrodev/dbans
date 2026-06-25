package me.demro.dbans.command;

import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.model.Warning;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WarnCommand extends BasePunishCommand {

    public WarnCommand(me.demro.dbans.DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.WARNING;
    }

    @Override
    protected String getPermission() {
        return "dbans.warn";
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
        if (sender instanceof Player) {
            Player issuer = (Player) sender;
            long maxDuration = plugin.getLimitsManager().getMaxDuration(issuer, "warn");
            if (maxDuration > 0 && duration != null && duration > maxDuration) {
                String group = plugin.getLuckPermsHook().getPrimaryGroup(issuer);
                MessageUtil.send(sender, "limit_exceed", "max", TimeUtil.formatDuration(maxDuration), "group", group);
                return;
            }
        }

        Long endTime = (duration != null && duration > 0) ? System.currentTimeMillis() + duration : null;
        Warning warning = new Warning(target.getUniqueId(), target.getName(),
                sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID,
                sender.getName(), reason, System.currentTimeMillis(), endTime, finalServer);
        plugin.getDatabase().saveWarning(warning);
        if (plugin.getProxySyncManager() != null) {
            plugin.getProxySyncManager().sendPunishmentCreate(warning);
        }

        Player online = target.getPlayer();
        if (online != null && finalServer.equals(plugin.getServerName())) {
            String durationStr = (duration != null && duration > 0) ? TimeUtil.formatDuration(duration) : "навсегда";
            MessageUtil.send(online, "warn_player",
                    "sender", sender.getName(), "reason", reason,
                    "duration", durationStr, "server", finalServer, "id", warning.getId());
        }

        String permission = silent ? null : "dbans.notify.warning";
        String durationStr = (duration != null && duration > 0) ? TimeUtil.formatDuration(duration) : "навсегда";
        MessageUtil.broadcast(permission, "warn_broadcast",
                "sender", sender.getName(), "target", target.getName(),
                "reason", reason, "duration", durationStr,
                "server", finalServer, "id", warning.getId());

        if (online != null && finalServer.equals(plugin.getServerName())) {
            plugin.getWarnManager().checkAndApplyThresholds(online);
        }
    }
}
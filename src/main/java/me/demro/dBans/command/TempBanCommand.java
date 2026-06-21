package me.demro.dBans.command;

import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.util.MessageUtil;
import me.demro.dBans.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TempBanCommand extends BasePunishCommand {

    public TempBanCommand(me.demro.dBans.DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.BAN;
    }

    @Override
    protected String getPermission() {
        return "dbans.tempban";
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
        if (duration == null || duration <= 0) {
            MessageUtil.send(sender, "invalid_time");
            return;
        }

        if (sender instanceof Player) {
            Player issuer = (Player) sender;
            long maxDuration = plugin.getLimitsManager().getMaxDuration(issuer, "tempban");
            if (maxDuration > 0 && duration > maxDuration) {
                String group = plugin.getLuckPermsHook().getPrimaryGroup(issuer);
                MessageUtil.send(sender, "limit_exceed", "max", TimeUtil.formatDuration(maxDuration), "group", group);
                return;
            }
        }

        long endTime = System.currentTimeMillis() + duration;
        Punishment ban = new Punishment(target.getUniqueId(), target.getName(),
                sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID,
                sender.getName(), PunishmentType.BAN, reason, System.currentTimeMillis(), endTime, finalServer);
        plugin.getDatabase().savePunishment(ban);

        Player online = target.getPlayer();
        if (online != null && finalServer.equals(plugin.getServerName())) {
            String kickMsg = MessageUtil.getRawMessage("tempban_player");
            if (kickMsg == null) kickMsg = "&c✖ Вы были забанены на %duration%.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
            kickMsg = kickMsg.replace("%reason%", reason).replace("%sender%", sender.getName())
                    .replace("%duration%", TimeUtil.formatDuration(duration))
                    .replace("%server%", finalServer).replace("%id%", ban.getId());
            online.kick(MessageUtil.deserializeForKick(kickMsg));
        }

        String permission = silent ? null : "dbans.notify.ban";
        MessageUtil.broadcast(permission, "tempban_broadcast",
                "sender", sender.getName(), "target", target.getName(),
                "reason", reason, "duration", TimeUtil.formatDuration(duration),
                "server", finalServer, "id", ban.getId());
    }
}
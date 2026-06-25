package me.demro.dbans.command;

import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TempMuteCommand extends BasePunishCommand {

    public TempMuteCommand(me.demro.dbans.DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.MUTE;
    }

    @Override
    protected String getPermission() {
        return "dbans.tempmute";
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
            long maxDuration = plugin.getLimitsManager().getMaxDuration(issuer, "tempmute");
            if (maxDuration > 0 && duration > maxDuration) {
                String group = plugin.getLuckPermsHook().getPrimaryGroup(issuer);
                MessageUtil.send(sender, "limit_exceed", "max", TimeUtil.formatDuration(maxDuration), "group", group);
                return;
            }
        }

        long endTime = System.currentTimeMillis() + duration;
        Punishment mute = new Punishment(target.getUniqueId(), target.getName(),
                sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID,
                sender.getName(), PunishmentType.MUTE, reason, System.currentTimeMillis(), endTime, finalServer);
        plugin.getDatabase().savePunishment(mute);
        // ======== ВСТАВКА ========
        if (plugin.getProxySyncManager() != null) {
            plugin.getProxySyncManager().sendPunishmentCreate(mute);
            plugin.getLogger().info("📤 [Sync] Sent punishment_create for " + mute.getId());
        }
        // ==========================
        plugin.scheduleMuteExpiry(mute);

        Player online = target.getPlayer();
        if (online != null && finalServer.equals(plugin.getServerName())) {
            MessageUtil.send(online, "tempmute_player",
                    "sender", sender.getName(), "reason", reason,
                    "duration", TimeUtil.formatDuration(duration),
                    "server", finalServer, "id", mute.getId());
        }

        String permission = silent ? null : "dbans.notify.mute";
        MessageUtil.broadcast(permission, "tempmute_broadcast",
                "sender", sender.getName(), "target", target.getName(),
                "reason", reason, "duration", TimeUtil.formatDuration(duration),
                "server", finalServer, "id", mute.getId());
    }
}
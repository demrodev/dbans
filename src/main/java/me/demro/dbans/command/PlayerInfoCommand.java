package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.PlayerInfo;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

@Slf4j
public class PlayerInfoCommand implements CommandExecutor {
    private final DBans plugin;

    public PlayerInfoCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.playerinfo")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "playerinfo_usage");
            return true;
        }
        String targetName = args[0];
        OfflinePlayer target = plugin.getPlayerCache().getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", targetName);
            return true;
        }

        PlayerInfo info = plugin.getDatabase().getPlayer(target.getUniqueId());
        String ip = (info != null) ? info.getIp() : "unknown";
        String location = (ip != null && !ip.equals("unknown")) ? plugin.getGeoIpManager().getLocation(ip) : "не определена";
        if (location == null) location = "не определена";

        List<Punishment> active = plugin.getDatabase().getActivePunishmentsIncludingJail(target.getUniqueId(), plugin.getServerName(), plugin.getMode());
        MessageUtil.send(sender, "playerinfo_header", "target", target.getName());
        MessageUtil.send(sender, "playerinfo_ip", "ip", ip);
        MessageUtil.send(sender, "playerinfo_location", "location", location);
        if (active.isEmpty()) {
            MessageUtil.send(sender, "playerinfo_no_active");
        } else {
            MessageUtil.send(sender, "playerinfo_active_header");
            for (Punishment p : active) {
                String typeName = p.getType().name();
                String expires = (p.getEndTime() == null) ? "навсегда" : TimeUtil.formatDuration(p.getEndTime() - System.currentTimeMillis());
                MessageUtil.send(sender, "playerinfo_active_entry",
                        "id", p.getId(),
                        "type", typeName,
                        "reason", p.getReason(),
                        "expires", expires);
            }
        }
        return true;
    }
}
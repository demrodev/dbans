package me.demro.dbans.command;

import lombok.CustomLog;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;

@CustomLog
public class GeoIpCommand implements CommandExecutor {

    private final DBans plugin;

    public GeoIpCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!sender.hasPermission("dbans.geoip")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "usage_geoip");
            return true;
        }
        String target = args[0];
        String ip;
        String displayName = target;
        Player onlinePlayer = Bukkit.getPlayer(target);
        if (onlinePlayer != null) {
            ip = onlinePlayer.getAddress().getAddress().getHostAddress();
            displayName = onlinePlayer.getName();
        } else if (isValidIp(target)) {
            ip = target;
        } else {
            OfflinePlayer offline = plugin.getPlayerCache().getOfflinePlayer(target);
            if (!offline.hasPlayedBefore()) {
                MessageUtil.send(sender, "player_not_found", "target", target);
                return true;
            }
            ip = plugin.getDatabase().getPlayerIp(offline.getUniqueId());
            if (ip == null) {
                MessageUtil.send(sender, "player_not_found", "target", target);
                return true;
            }
            displayName = offline.getName();
        }
        if (!plugin.getGeoIpManager().isReady()) {
            MessageUtil.send(sender, "geoip_not_ready");
            return true;
        }
        String location = plugin.getGeoIpManager().getLocation(ip);
        if (location == null) {
            MessageUtil.send(sender, "geoip_not_found", "target", displayName);
        } else {
            MessageUtil.send(sender, "geoip_result", "target", displayName, "ip", ip, "location", location);
        }
        return true;
    }

    private boolean isValidIp(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
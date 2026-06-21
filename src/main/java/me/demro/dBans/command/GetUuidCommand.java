package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class GetUuidCommand implements CommandExecutor {
    private final DBans plugin;

    public GetUuidCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.getuuid")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "getuuid_usage");
            return true;
        }
        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", targetName);
            return true;
        }
        MessageUtil.send(sender, "getuuid_result",
                "target", target.getName(),
                "uuid", target.getUniqueId().toString());
        return true;
    }
}
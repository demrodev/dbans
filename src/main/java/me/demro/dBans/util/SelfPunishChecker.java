package me.demro.dBans.util;

import me.demro.dBans.DBans;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SelfPunishChecker {
    private final DBans plugin;

    public SelfPunishChecker(DBans plugin) {
        this.plugin = plugin;
    }

    public boolean isSelfPunish(CommandSender sender, String targetName) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;
        if (player.getName().equalsIgnoreCase(targetName)) {
            MessageUtil.send(player, "cannot_punish_self");
            return true;
        }
        return false;
    }
}
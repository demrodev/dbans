package me.demro.dbans.util;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Slf4j
public class SelfPunishChecker {

    private final DBans plugin;

    public SelfPunishChecker(DBans plugin) {
        this.plugin = plugin;
    }

    public boolean isSelfPunish(CommandSender sender, String targetName) {
        if (!(sender instanceof Player player)) return false;
        if (player.getName().equalsIgnoreCase(targetName)) {
            MessageUtil.send(player, "cannot_punish_self");
            return true;
        }
        return false;
    }
}
package me.demro.dbans.util;

import lombok.extern.slf4j.Slf4j;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Slf4j
public class SelfPunishChecker {

    public boolean isSelfPunish(CommandSender sender, String targetName) {
        if (!(sender instanceof Player player)) return false;
        if (player.getName().equalsIgnoreCase(targetName)) {
            MessageUtil.send(player, "cannot_punish_self");
            return true;
        }
        return false;
    }
}
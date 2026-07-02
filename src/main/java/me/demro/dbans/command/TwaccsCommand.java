package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

@Slf4j
public class TwaccsCommand implements CommandExecutor {

    private final DBans plugin;

    public TwaccsCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.twaccs")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "usage_twaccs");
            return true;
        }
        String targetName = args[0];
        OfflinePlayer target = plugin.getPlayerCache().getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", targetName);
            return true;
        }
        List<String> alts = plugin.getAltAccountManager().findAltAccounts(target.getName());
        if (alts.isEmpty()) {
            MessageUtil.send(sender, "twaccs_no_alts", "target", target.getName());
        } else {
            StringBuilder sb = new StringBuilder();
            for (String alt : alts) {
                sb.append(alt).append(", ");
            }
            String altList = sb.length() > 0 ? sb.substring(0, sb.length() - 2) : "";
            MessageUtil.send(sender, "twaccs_result", "target", target.getName(), "alts", altList);
        }
        return true;
    }
}
package me.demro.dbans.command;

import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.List;

public class PStatCommand implements CommandExecutor {
    private final DBans plugin;

    public PStatCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.pstat")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "pstat_usage");
            return true;
        }
        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", targetName);
            return true;
        }

        List<Punishment> all = plugin.getDatabase().getPunishmentHistory(target.getUniqueId(), true);
        int bans = 0, mutes = 0, kicks = 0, ipbans = 0;
        for (Punishment p : all) {
            switch (p.getType()) {
                case BAN: bans++; break;
                case MUTE: mutes++; break;
                case KICK: kicks++; break;
                case IPBAN: ipbans++; break;
            }
        }
        MessageUtil.send(sender, "pstat_result",
                "target", target.getName(),
                "bans", String.valueOf(bans),
                "mutes", String.valueOf(mutes),
                "kicks", String.valueOf(kicks),
                "ipbans", String.valueOf(ipbans));
        return true;
    }
}
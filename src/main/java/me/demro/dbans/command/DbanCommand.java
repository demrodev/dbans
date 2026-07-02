package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

@Slf4j
public class DbanCommand implements CommandExecutor {

    private final DBans plugin;

    public DbanCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            MessageUtil.send(sender, "usage_dban");
            return true;
        }

        if (!sender.hasPermission("dbans.dban.reload")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        plugin.reloadJailConfig();
        plugin.reloadConfig();
        plugin.getPresetManager().loadPresets();
        MessageUtil.reloadMessages();
        plugin.getLimitsManager().reload();
        MessageUtil.send(sender, "reload_success");
        log.info("DBans configuration reloaded by {}", sender.getName());
        return true;
    }
}
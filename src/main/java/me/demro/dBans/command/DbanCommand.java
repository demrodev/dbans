package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

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
        // Перезагрузка основной конфигурации
        plugin.reloadConfig();
        // Перезагрузка пресетов
        plugin.getPresetManager().loadPresets();
        // Перезагрузка сообщений
        MessageUtil.reloadMessages();
        // Перезагрузка лимитов групп
        plugin.getLimitsManager().reload();
        // Сообщение об успехе
        MessageUtil.send(sender, "reload_success");
        return true;
    }
}
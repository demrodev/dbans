package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

@Slf4j
public class DropPunishCommand implements CommandExecutor {

    private final DBans plugin;

    public DropPunishCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.droppunish")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "droppunish_usage");
            return true;
        }

        String arg = args[0].toLowerCase();
        if (arg.equals("all")) {
            if (!sender.hasPermission("dbans.droppunish.all")) {
                MessageUtil.send(sender, "no_permission");
                return true;
            }
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                MessageUtil.send(sender, "droppunish_all_confirm");
                return true;
            }
            plugin.getDatabase().deleteAllPunishments();
            plugin.getDatabase().deleteAllJails();
            plugin.getDatabase().deleteAllWarnings();
            MessageUtil.send(sender, "droppunish_all_success");
            log.warn("All punishments deleted by {}", sender.getName());
            return true;
        }

        String id = args[0];
        Punishment punishment = plugin.getDatabase().getPunishmentById(id);
        if (punishment == null) {
            MessageUtil.send(sender, "punishment_not_found", "id", id);
            return true;
        }
        if (punishment.getType() == PunishmentType.MUTE && punishment.getEndTime() != null) {
            plugin.cancelMuteExpiry(id);
        }
        plugin.getDatabase().deletePunishment(id);
        MessageUtil.send(sender, "punishment_deleted", "id", id);
        log.info("Punishment {} deleted by {}", id, sender.getName());
        return true;
    }
}
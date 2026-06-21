package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.model.Warning;
import me.demro.dBans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class WarnListCommand implements CommandExecutor {
    private final DBans plugin;
    private SimpleDateFormat dateFormat;

    public WarnListCommand(DBans plugin) {
        this.plugin = plugin;
        String pattern = plugin.getConfig().getString("history.date_format", "dd.MM.yyyy HH:mm:ss");
        this.dateFormat = new SimpleDateFormat(pattern);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.warnlist")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "usage_warnlist");
            return true;
        }
        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", targetName);
            return true;
        }
        UUID targetUuid = target.getUniqueId();
        List<Warning> warnings = plugin.getDatabase().getAllWarningsForPlayer(targetUuid);
        if (warnings.isEmpty()) {
            MessageUtil.send(sender, "warnlist_empty", "target", target.getName());
            return true;
        }
        MessageUtil.send(sender, "warnlist_header", "target", target.getName());
        for (Warning w : warnings) {
            String date = dateFormat.format(new Date(w.getStartTime()));
            String status;
            if (!w.isActive()) status = "Снято";
            else if (w.isExpired()) status = "Истекло";
            else status = "Активно";
            String duration = w.getEndTime() == null ? "навсегда" : me.demro.dBans.util.TimeUtil.formatDuration(w.getEndTime() - w.getStartTime());
            MessageUtil.send(sender, "warnlist_entry",
                    "id", w.getId(),
                    "date", date,
                    "issuer", w.getIssuerName(),
                    "reason", w.getReason(),
                    "duration", duration,
                    "status", status);
        }
        MessageUtil.send(sender, "warnlist_footer", "total", String.valueOf(warnings.size()));
        return true;
    }
}
package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.model.Punishment;
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

public class HistoryCommand implements CommandExecutor {
    private final DBans plugin;
    private SimpleDateFormat dateFormat;

    public HistoryCommand(DBans plugin) {
        this.plugin = plugin;
        String pattern = plugin.getConfig().getString("history.date_format", "dd.MM.yyyy HH:mm:ss");
        this.dateFormat = new SimpleDateFormat(pattern);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.history")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "inspect_usage");
            return true;
        }
        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", targetName);
            return true;
        }
        UUID targetUuid = target.getUniqueId();
        List<Punishment> punishments = plugin.getDatabase().getPunishmentHistoryIncludingJail(targetUuid);
        if (punishments.isEmpty()) {
            MessageUtil.send(sender, "no_history", "target", target.getName());
            return true;
        }
        MessageUtil.send(sender, "history.header", "target", target.getName());
        for (Punishment p : punishments) {
            String date = dateFormat.format(new Date(p.getStartTime()));
            String typeName;
            switch (p.getType()) {
                case BAN: typeName = "Бан"; break;
                case MUTE: typeName = "Мут"; break;
                case KICK: typeName = "Кик"; break;
                case IPBAN: typeName = "IP-бан"; break;
                case JAIL: typeName = "Тюрьма"; break;
                default: typeName = "Наказание";
            }
            String statusKey;
            if (!p.isActive()) statusKey = "status_pardoned";
            else if (p.isExpired()) statusKey = "status_expired";
            else statusKey = "status_active";
            String statusRaw = MessageUtil.getRawMessage(statusKey);
            String status = MessageUtil.colorize(statusRaw);
            MessageUtil.send(sender, "history.entry",
                    "id", p.getId(),
                    "date", date,
                    "type", typeName,
                    "issuer", p.getIssuerName(),
                    "reason", p.getReason(),
                    "server", p.getServerName() != null ? p.getServerName() : "unknown",
                    "status", status);
        }
        MessageUtil.send(sender, "history.footer", "total", String.valueOf(punishments.size()));
        return true;
    }
}
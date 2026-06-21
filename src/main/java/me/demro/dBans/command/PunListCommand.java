package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.util.MessageUtil;
import me.demro.dBans.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class PunListCommand implements CommandExecutor {
    private final DBans plugin;
    private SimpleDateFormat dateFormat;

    public PunListCommand(DBans plugin) {
        this.plugin = plugin;
        String pattern = plugin.getConfig().getString("punlist.date_format", "dd.MM.yyyy HH:mm:ss");
        this.dateFormat = new SimpleDateFormat(pattern);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.punlist")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) page = 1;
            } catch (NumberFormatException ignored) {}
        }
        int perPage = plugin.getConfig().getInt("punlist.entries_per_page", 10);
        List<Punishment> all = plugin.getDatabase().getAllPunishmentsIncludingJail();
        if (all.isEmpty()) {
            MessageUtil.send(sender, "punlist.no_punishments");
            return true;
        }
        int totalPages = (int) Math.ceil((double) all.size() / perPage);
        if (page > totalPages) page = totalPages;
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, all.size());
        List<Punishment> sublist = all.subList(start, end);
        MessageUtil.send(sender, "punlist.header",
                "page", String.valueOf(page),
                "total_pages", String.valueOf(totalPages));
        for (Punishment p : sublist) {
            String date = dateFormat.format(new Date(p.getStartTime()));
            String duration;
            if (p.getEndTime() == null) {
                duration = plugin.getConfig().getString("permanent_word", "навсегда");
            } else {
                duration = TimeUtil.formatDuration(p.getEndTime() - p.getStartTime());
            }
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
            MessageUtil.send(sender, "punlist.entry",
                    "id", p.getId(),
                    "player", p.getPlayerName(),
                    "issuer", p.getIssuerName(),
                    "date", date,
                    "duration", duration,
                    "reason", p.getReason(),
                    "server", p.getServerName() != null ? p.getServerName() : "unknown",
                    "type", typeName,
                    "status", status);
        }
        MessageUtil.send(sender, "punlist.footer");
        return true;
    }
}
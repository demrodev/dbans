package me.demro.dbans.command;

import lombok.CustomLog;
import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

@CustomLog
public class InspectCommand implements CommandExecutor {

    private final DBans plugin;

    public InspectCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!sender.hasPermission("dbans.inspect")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, "inspect_usage");
            return true;
        }
        String targetName = args[0];
        OfflinePlayer target = plugin.getPlayerCache().getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", targetName);
            return true;
        }
        UUID targetUuid = target.getUniqueId();
        List<Punishment> active = plugin.getDatabase().getActivePunishmentsIncludingJail(targetUuid, plugin.getServerName(), plugin.getMode());
        if (active.isEmpty()) {
            MessageUtil.send(sender, "inspect.none", "target", target.getName());
            return true;
        }
        MessageUtil.send(sender, "inspect.header", "target", target.getName());
        for (Punishment p : active) {
            String typeKey = p.getType().name().toLowerCase();
            String server = p.getServerName() != null ? p.getServerName() : "unknown";
            if (p.getEndTime() == null) {
                MessageUtil.send(sender, "inspect.entry_permanent",
                                 "type", typeKey.equals("ban") ? "Бан" : (typeKey.equals("mute") ? "Мут" : "Тюрьма"),
                                 "id", p.getId(),
                                 "reason", p.getReason(),
                                 "server", server);
            } else {
                String expires = TimeUtil.formatDuration(p.getEndTime() - System.currentTimeMillis());
                String entryKey = "inspect.entry_" + typeKey;
                MessageUtil.send(sender, entryKey,
                                 "id", p.getId(),
                                 "reason", p.getReason(),
                                 "expires", expires,
                                 "server", server);
            }
        }
        MessageUtil.send(sender, "inspect.footer");
        return true;
    }
}
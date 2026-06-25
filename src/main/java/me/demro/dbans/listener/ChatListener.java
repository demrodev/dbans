package me.demro.dbans.listener;

import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import io.papermc.paper.event.player.AsyncChatEvent;

/**
 * Слушатель чата и команд – блокировка замученных игроков.
 * Использует кэш БД, поэтому дополнительные оптимизации не требуются.
 */
public class ChatListener implements Listener {
    private final DBans plugin;

    public ChatListener(DBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Punishment mute = plugin.getDatabase().getActivePunishment(player.getUniqueId(), PunishmentType.MUTE,
                plugin.getServerName(), plugin.getMode());
        if (mute == null) return;

        event.setCancelled(true);
        String duration = mute.getEndTime() == null ? "навсегда" : TimeUtil.formatDuration(mute.getEndTime() - System.currentTimeMillis());
        MessageUtil.send(player, "cannot_chat",
                "duration", duration,
                "reason", mute.getReason(),
                "sender", mute.getIssuerName(),
                "server", mute.getServerName());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Punishment mute = plugin.getDatabase().getActivePunishment(player.getUniqueId(), PunishmentType.MUTE,
                plugin.getServerName(), plugin.getMode());
        if (mute == null) return;

        String command = event.getMessage().toLowerCase();
        if (command.startsWith("/")) command = command.substring(1);
        String baseCommand = command.split(" ")[0];
        if (plugin.getConfig().getStringList("muted_command_blacklist").contains("*")) {
            event.setCancelled(true);
            MessageUtil.send(player, "muted_command_blocked", "command", baseCommand);
        } else if (plugin.getConfig().getStringList("muted_command_blacklist").contains(baseCommand)) {
            event.setCancelled(true);
            MessageUtil.send(player, "muted_command_blocked", "command", baseCommand);
        }
    }
}
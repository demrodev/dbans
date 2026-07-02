package me.demro.dbans.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import me.demro.dlibs.dbans.api.punishment.Punishment;
import me.demro.dlibs.dbans.api.punishment.PunishmentType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class ChatListener implements Listener {

    private final DBans plugin;

    public ChatListener(DBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        // Проверка мута через новый API
        CompletableFuture<Boolean> hasMuteFuture = plugin.getApi().punishments().hasActive(player.getUniqueId(), PunishmentType.MUTE);
        if (!hasMuteFuture.join()) {
            return;
        }

        // Если есть мут – получаем его для деталей
        CompletableFuture<List<Punishment>> muteListFuture = plugin.getApi().punishments().findActiveByTarget(player.getUniqueId());
        List<Punishment> mutes = muteListFuture.join();
        if (mutes.isEmpty()) {
            return;
        }
        Punishment mute = mutes.get(0);
        event.setCancelled(true);
        String duration = mute.isPermanent() ? "навсегда" : TimeUtil.formatDuration(mute.expiresAt().get().toEpochMilli() - System.currentTimeMillis());
        MessageUtil.send(player, "cannot_chat",
                         "duration", duration,
                         "reason", mute.reason().value(),
                         "sender", mute.issuer().name(),
                         "server", mute.serverName());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        // Проверка мута через API
        CompletableFuture<Boolean> hasMuteFuture = plugin.getApi().punishments().hasActive(player.getUniqueId(), PunishmentType.MUTE);
        if (!hasMuteFuture.join()) {
            return;
        }

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
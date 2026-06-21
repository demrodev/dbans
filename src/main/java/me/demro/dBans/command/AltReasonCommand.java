package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.api.adapter.PunishmentAdapter;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.util.MessageUtil;
import me.demro.dlibs.api.EventManager;
import me.demro.dlibs.api.events.PunishmentModifyEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AltReasonCommand implements CommandExecutor {
    private final DBans plugin;

    public AltReasonCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.altreason")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "altreason_usage");
            return true;
        }
        String id = args[0];
        String newReason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Punishment punishment = plugin.getDatabase().getPunishmentById(id);
        if (punishment == null) {
            MessageUtil.send(sender, "punishment_not_found", "id", id);
            return true;
        }

        String oldReason = punishment.getReason();
        plugin.getDatabase().updatePunishmentReason(id, newReason);
        MessageUtil.send(sender, "reason_updated", "id", id, "reason", newReason);

        try {
            EventManager em = plugin.getEventManager();
            if (em != null) {
                // Получаем обновлённое наказание
                Punishment updated = plugin.getDatabase().getPunishmentById(id);
                if (updated != null) {
                    PunishmentModifyEvent event = new PunishmentModifyEvent(
                            new PunishmentAdapter(updated),
                            oldReason, newReason, null, null
                    );
                    em.callEvent(event);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to call PunishmentModifyEvent: " + e.getMessage());
        }

        return true;
    }
}
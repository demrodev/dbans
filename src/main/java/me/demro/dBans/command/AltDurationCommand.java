package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.api.adapter.PunishmentAdapter;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.util.MessageUtil;
import me.demro.dBans.util.TimeUtil;
import me.demro.dlibs.api.EventManager;
import me.demro.dlibs.api.events.PunishmentModifyEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AltDurationCommand implements CommandExecutor {
    private final DBans plugin;

    public AltDurationCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.altduration")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "altduration_usage");
            return true;
        }
        String id = args[0];
        String durationStr = args[1];
        Punishment punishment = plugin.getDatabase().getPunishmentById(id);

        if (punishment == null) {
            MessageUtil.send(sender, "punishment_not_found", "id", id);
            return true;
        }
        if (punishment.getEndTime() == null) {
            MessageUtil.send(sender, "cannot_change_duration_permanent");
            return true;
        }

        long newDuration;
        try {
            newDuration = TimeUtil.parseDuration(durationStr);
        } catch (IllegalArgumentException e) {
            MessageUtil.send(sender, "invalid_time");
            return true;
        }
        long newEndTime = punishment.getStartTime() + newDuration;
        if (newDuration <= 0) {
            MessageUtil.send(sender, "cannot_change_duration_permanent");
            return true;
        }

        Long oldEnd = punishment.getEndTime();
        plugin.getDatabase().updatePunishmentEndTime(id, newEndTime);
        if (plugin.getProxySyncManager() != null) {
            Punishment updated = plugin.getDatabase().getPunishmentById(id);
            if (updated != null) {
                plugin.getProxySyncManager().sendPunishmentModify(updated, null, oldEnd);
            }
        }
        punishment.setEndTime(newEndTime);

        if (newEndTime <= System.currentTimeMillis()) {
            punishment.setActive(false);
            plugin.getDatabase().updatePunishment(punishment);
            MessageUtil.send(sender, "duration_updated_expired", "id", id);
        } else {
            MessageUtil.send(sender, "duration_updated", "id", id, "duration", TimeUtil.formatDuration(newDuration));
        }

        if (punishment.getType() == PunishmentType.MUTE) {
            plugin.cancelMuteExpiry(id);
            if (punishment.isActive() && punishment.getEndTime() > System.currentTimeMillis()) {
                plugin.scheduleMuteExpiry(punishment);
            }
        }

        try {
            EventManager em = plugin.getEventManager();
            if (em != null) {
                Punishment updated = plugin.getDatabase().getPunishmentById(id);
                if (updated != null) {
                    PunishmentModifyEvent event = new PunishmentModifyEvent(
                            new PunishmentAdapter(updated),
                            null, null, oldEnd, updated.getEndTime()
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
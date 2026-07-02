package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.punishment.PunishmentId;
import me.demro.dlibs.dbans.api.punishment.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
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

        // Проверяем существование через новый API
        CompletableFuture<Optional<Punishment>> future = plugin.getApi().punishments().findById(PunishmentId.of(id));
        Optional<Punishment> opt = future.join();
        if (opt.isEmpty()) {
            MessageUtil.send(sender, "punishment_not_found", "id", id);
            return true;
        }

        Punishment punishment = opt.get();
        if (punishment.type() == me.demro.dlibs.dbans.api.punishment.PunishmentType.BAN ||
                punishment.type() == me.demro.dlibs.dbans.api.punishment.PunishmentType.MUTE) {
            // Можно изменить длительность только для временных
            if (punishment.isPermanent()) {
                MessageUtil.send(sender, "cannot_change_duration_permanent");
                return true;
            }
        } else {
            MessageUtil.send(sender, "cannot_change_duration_for_type", "type", punishment.type().name());
            return true;
        }

        long newDuration;
        try {
            newDuration = TimeUtil.parseDuration(durationStr);
        } catch (IllegalArgumentException e) {
            MessageUtil.send(sender, "invalid_time");
            return true;
        }
        if (newDuration <= 0) {
            MessageUtil.send(sender, "invalid_time");
            return true;
        }

        long newEndTime = System.currentTimeMillis() + newDuration;
        plugin.getDatabase().updatePunishmentEndTime(id, newEndTime);
        MessageUtil.send(sender, "duration_updated", "id", id, "duration", TimeUtil.formatDuration(newDuration));
        log.info("Duration for punishment {} changed to {} by {}", id, durationStr, sender.getName());
        return true;
    }
}
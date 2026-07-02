package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import me.demro.dlibs.dbans.api.punishment.Punishment;
import me.demro.dlibs.dbans.api.punishment.PunishmentId;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
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

        // Проверяем существование через новый API
        CompletableFuture<Optional<Punishment>> future = plugin.getApi().punishments().findById(PunishmentId.of(id));
        Optional<Punishment> opt = future.join();
        if (opt.isEmpty()) {
            MessageUtil.send(sender, "punishment_not_found", "id", id);
            return true;
        }

        // Обновляем через прямой вызов к БД (в новом API нет метода изменения причины)
        plugin.getDatabase().updatePunishmentReason(id, newReason);
        MessageUtil.send(sender, "reason_updated", "id", id, "reason", newReason);
        log.info("Reason for punishment {} changed to '{}' by {}", id, newReason, sender.getName());
        return true;
    }
}
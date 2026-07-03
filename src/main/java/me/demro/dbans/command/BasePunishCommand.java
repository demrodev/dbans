package me.demro.dbans.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.PresetManager;
import me.demro.dbans.util.TimeUtil;
import me.demro.dlibs.dbans.api.punishment.PunishmentType;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public abstract class BasePunishCommand implements CommandExecutor {

    private static final UUID CONSOLE_UUID = UUID.nameUUIDFromBytes("CONSOLE".getBytes());
    protected final DBans plugin;

    @Contract("_ -> new")
    static @NotNull ParsedArgs parseArgs(String @NotNull [] args) {
        boolean silent = false;
        String targetServer = null;
        List<String> filteredArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.equalsIgnoreCase("-s")) {
                silent = true;
            } else if (arg.toLowerCase().startsWith("server:")) {
                String server = arg.substring(7);
                targetServer = server.isEmpty() ? null : server;
            } else {
                filteredArgs.add(arg);
            }
        }
        return new ParsedArgs(silent, targetServer, filteredArgs);
    }

    protected abstract PunishmentType getType(); // новый тип

    protected abstract String getPermission();

    protected abstract boolean isPermanent();

    protected abstract void executePunishment(CommandSender sender, OfflinePlayer target, String reason,
                                              String finalServer, boolean silent, Long duration
    );

    protected Long parseDuration(String @NotNull [] args, int startIndex) {
        if (args.length > startIndex && TimeUtil.isTimeFormat(args[startIndex])) {
            try {
                return TimeUtil.parseDuration(args[startIndex]);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label,
                             String[] args
    ) {
        String commandName = cmd.getName().toLowerCase();

        if (!sender.hasPermission(getPermission())) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }

        ParsedArgs parsed = parseArgs(args);
        boolean silent = parsed.silent();
        String targetServer = parsed.targetServer();
        List<String> filteredArgs = parsed.filteredArgs();

        if (filteredArgs.size() < 2) {
            MessageUtil.send(sender, "usage_" + commandName);
            return true;
        }

        if (targetServer != null) {
            boolean canUseServer = sender.hasPermission("dbans.server.bypass") ||
                                   sender.hasPermission("dbans.server." + commandName);
            if (!canUseServer) {
                MessageUtil.send(sender, "no_server_permission", "command", commandName);
                return true;
            }
        }

        String targetName = filteredArgs.getFirst();
        String reason;
        Long duration = null;

        // Обработка пресета
        String lastArg = filteredArgs.getLast();
        if (lastArg.startsWith("+")) {
            String presetName = lastArg.substring(1);
            PresetManager.PunishmentPreset preset = plugin.getPresetManager().getPreset(presetName);
            if (preset == null) {
                MessageUtil.send(sender, "preset_not_found", "name", presetName);
                return true;
            }
            if (preset.type() != null && !preset.type().equalsIgnoreCase(commandName)) {
                MessageUtil.send(sender, "preset_wrong_type", "preset", presetName, "command", commandName);
                return true;
            }
            if (!plugin.getPresetManager().canUsePreset(sender, preset)) {
                MessageUtil.send(sender, "no_preset_permission", "preset", presetName);
                return true;
            }
            if (isPermanent() && !preset.isPermanent()) {
                MessageUtil.send(sender, "preset_has_duration", "preset", presetName, "command", commandName);
                return true;
            }
            if (!isPermanent() && preset.isPermanent()) {
                MessageUtil.send(sender, "preset_no_duration", "preset", presetName, "command", commandName);
                return true;
            }
            reason = preset.reason();
            if (!preset.isPermanent()) {
                try {
                    duration = TimeUtil.parseDuration(preset.durationRaw());
                } catch (IllegalArgumentException e) {
                    MessageUtil.send(sender, "invalid_time");
                    return true;
                }
            }
        } else {
            // Обычный ввод
            if (filteredArgs.size() > 1 && TimeUtil.isTimeFormat(filteredArgs.get(1))) {
                try {
                    duration = TimeUtil.parseDuration(filteredArgs.get(1));
                } catch (IllegalArgumentException e) {
                    MessageUtil.send(sender, "invalid_time");
                    return true;
                }
                if (filteredArgs.size() < 3) {
                    MessageUtil.send(sender, "usage_" + commandName);
                    return true;
                }
                reason = String.join(" ", filteredArgs.subList(2, filteredArgs.size()));
            } else {
                reason = String.join(" ", filteredArgs.subList(1, filteredArgs.size()));
            }
        }

        if (isPermanent()) {
            duration = null;
        } else {
            if (duration == null) {
                MessageUtil.send(sender, "usage_" + commandName);
                return true;
            }
            if (duration <= 0) {
                MessageUtil.send(sender, "invalid_time");
                return true;
            }
        }

        // Проверка на самого себя
        if (plugin.getSelfPunishChecker().isSelfPunish(sender, targetName)) return true;

        OfflinePlayer target = plugin.getPlayerCache().getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", targetName);
            return true;
        }

        // Используем новый API для проверок иммунитета и прав
        UUID targetUuid = target.getUniqueId();
        PunishmentType apiType = getType(); // новый enum

        // Проверка иммунитета через PermissionService
        CompletableFuture<Boolean> immuneFuture = plugin.getApi().permissions().hasImmunity(targetUuid, apiType);
        if (immuneFuture.join()) {
            MessageUtil.send(sender, "target_immune_permission", "target", target.getName());
            return true;
        }

        // Проверка приоритета
        CompletableFuture<Boolean> canPunishFuture = plugin.getApi().permissions().canPunish(
                sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID,
                targetUuid
        );
        if (!canPunishFuture.join()) {
            MessageUtil.send(sender, "cannot_punish_higher_priority", "target", target.getName());
            return true;
        }

        // Проверка активного наказания (кроме KICK)
        if (apiType != PunishmentType.KICK) {
            CompletableFuture<Boolean> hasActiveFuture = plugin.getApi().punishments().hasActive(targetUuid, apiType);
            if (hasActiveFuture.join()) {
                String alreadyKey = switch (apiType) {
                    case BAN -> "ban_already";
                    case MUTE -> "mute_already";
                    case JAIL -> "already_jailed";
                    case WARNING -> "already_warned";
                    default -> null;
                };
                if (alreadyKey != null) {
                    MessageUtil.send(sender, alreadyKey, "target", target.getName());
                    return true;
                }
            }
        }

        // Кулдаун
        if (sender instanceof Player issuer) {
            if (plugin.getLimitsManager().isOnCooldown(issuer, commandName)) {
                int remaining = plugin.getLimitsManager().getRemainingCooldown(issuer, commandName);
                MessageUtil.send(sender, "command_on_cooldown", "command", commandName, "time", String.valueOf(remaining));
                return true;
            }
        }

        // Проверка лимитов длительности (для временных)
        if (!isPermanent() && sender instanceof Player) {
            long maxDuration = plugin.getLimitsManager().getMaxDuration((Player) sender, commandName);
            if (maxDuration > 0 && duration > maxDuration) {
                String group = plugin.getLuckPermsHook().getPrimaryGroup((Player) sender);
                MessageUtil.send(sender, "limit_exceed", "max", TimeUtil.formatDuration(maxDuration), "group", group);
                return true;
            }
        }

        String finalServer = (targetServer != null && !targetServer.isEmpty()) ? targetServer : plugin.getServerName();
        String mode = plugin.getMode();
        if (!mode.equalsIgnoreCase("sync") && !mode.equalsIgnoreCase("sync_static") && targetServer != null) {
            MessageUtil.send(sender, "server_argument_not_supported");
            return true;
        }

        if (sender instanceof Player) {
            plugin.getLimitsManager().setCooldown((Player) sender, commandName);
        }

        // Проверка silent
        boolean canSilent = true;
        if (sender instanceof Player) {
            canSilent = plugin.getLimitsManager().canUseSilent((Player) sender, commandName);
        }
        if (silent && !canSilent) {
            MessageUtil.send(sender, "silent_not_allowed", "command", commandName);
            silent = false;
        }

        // Выполняем наказание через новый API
        executePunishment(sender, target, reason, finalServer, silent, duration);
        return true;
    }

    record ParsedArgs(boolean silent, String targetServer, List<String> filteredArgs) {

    }
}
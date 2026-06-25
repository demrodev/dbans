package me.demro.dbans.command;

import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.PresetManager;
import me.demro.dbans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Абстрактный базовый класс для всех команд наказания (ban, mute, kick, jail, warn).
 * Содержит общую логику: парсинг флагов, проверки прав, иммунитетов, кулдаунов,
 * обработку пресетов, проверку существующего наказания и т.д.
 * Специфическая логика каждого наказания реализуется в методе executePunishment().
 */
public abstract class BasePunishCommand implements CommandExecutor {
    protected final DBans plugin;
    protected static final UUID CONSOLE_UUID = UUID.nameUUIDFromBytes("CONSOLE".getBytes());

    protected BasePunishCommand(DBans plugin) {
        this.plugin = plugin;
    }
    protected abstract PunishmentType getType();          // тип наказания (BAN, MUTE, KICK, JAIL, WARNING)
    protected abstract String getPermission();           // базовое разрешение (punishment.ban и т.п.)
    protected abstract boolean isPermanent();             // является ли наказание перманентным
    protected abstract void executePunishment(CommandSender sender, OfflinePlayer target, String reason, String finalServer, boolean silent, Long duration);

    protected Long parseDuration(String[] args, int startIndex) {
        return null; // по умолчанию – без длительности
    }

    protected boolean hasActivePunishment(OfflinePlayer target, String mode) {
        Punishment existing = plugin.getDatabase().getActivePunishment(target.getUniqueId(), getType(), plugin.getServerName(), mode);
        return existing != null && !existing.isExpired();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String commandName = cmd.getName().toLowerCase();

        if (!sender.hasPermission(getPermission())) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }

        boolean silent = false;
        String targetServer = null;
        String[] cleaned = new String[args.length];
        int cleanedIdx = 0;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("-s")) silent = true;
            else if (arg.toLowerCase().startsWith("server:")) targetServer = arg.substring(7);
            else cleaned[cleanedIdx++] = arg;
        }
        String[] cleanArgs = java.util.Arrays.copyOf(cleaned, cleanedIdx);

        if (targetServer != null) {
            boolean canUseServer = sender.hasPermission("dbans.server.bypass") ||
                    sender.hasPermission("dbans.server." + commandName);
            if (!canUseServer) {
                MessageUtil.send(sender, "no_server_permission", "command", commandName);
                return true;
            }
        }

        if (cleanArgs.length < 2) {
            MessageUtil.send(sender, "usage_" + commandName);
            return true;
        }

        String targetName = cleanArgs[0];
        String reason;
        Long duration = null;

        if (cleanArgs[cleanArgs.length - 1].startsWith("+")) {
            String presetName = cleanArgs[cleanArgs.length - 1].substring(1);
            PresetManager.PunishmentPreset preset = plugin.getPresetManager().getPreset(presetName);
            if (preset == null) {
                MessageUtil.send(sender, "preset_not_found", "name", presetName);
                return true;
            }
            if (preset.getType() != null && !preset.getType().equalsIgnoreCase(commandName)) {
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
            reason = preset.getReason();
            if (!preset.isPermanent()) {
                try {
                    duration = TimeUtil.parseDuration(preset.getDurationRaw());
                } catch (IllegalArgumentException e) {
                    MessageUtil.send(sender, "invalid_time");
                    return true;
                }
            }
        } else {
            duration = parseDuration(cleanArgs, 1);
            int reasonStart = (duration != null) ? 2 : 1;
            if (reasonStart >= cleanArgs.length) {
                MessageUtil.send(sender, "usage_" + commandName);
                return true;
            }
            reason = String.join(" ", java.util.Arrays.copyOfRange(cleanArgs, reasonStart, cleanArgs.length));
        }

        if (plugin.getSelfPunishChecker().isSelfPunish(sender, targetName)) return true;

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", targetName);
            return true;
        }

        if (!plugin.getLimitsManager().canPunish(sender, target)) return true;

        Player targetOnline = target.getPlayer();
        if (targetOnline != null) {
            if (plugin.getLimitsManager().isImmune(targetOnline, commandName)) {
                MessageUtil.send(sender, "target_immune_permission", "target", target.getName());
                return true;
            }
        } else {
            if (plugin.getLimitsManager().isImmune(target, commandName)) {
                MessageUtil.send(sender, "target_immune_permission", "target", target.getName());
                return true;
            }
        }

        if (getType() != PunishmentType.KICK && hasActivePunishment(target, plugin.getMode())) {
            String alreadyKey = (getType() == PunishmentType.BAN) ? "ban_already" :
                    (getType() == PunishmentType.MUTE) ? "mute_already" :
                            (getType() == PunishmentType.JAIL) ? "already_jailed" :
                                    (getType() == PunishmentType.WARNING) ? "already_warned" : null;
            if (alreadyKey != null) {
                MessageUtil.send(sender, alreadyKey, "target", target.getName());
                return true;
            }
        }

        if (sender instanceof Player) {
            Player issuer = (Player) sender;
            if (plugin.getLimitsManager().isOnCooldown(issuer, commandName)) {
                int remaining = plugin.getLimitsManager().getRemainingCooldown(issuer, commandName);
                MessageUtil.send(sender, "command_on_cooldown", "command", commandName, "time", String.valueOf(remaining));
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

        boolean canSilent = true;
        if (sender instanceof Player) {
            canSilent = plugin.getLimitsManager().canUseSilent((Player) sender, commandName);
        }
        if (silent && !canSilent) {
            MessageUtil.send(sender, "silent_not_allowed", "command", commandName);
            silent = false;
        }

        executePunishment(sender, target, reason, finalServer, silent, duration);

        return true;
    }
}
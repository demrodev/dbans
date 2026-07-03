package me.demro.dbans.bootstrap;

import lombok.experimental.UtilityClass;
import me.demro.dbans.DBans;
import me.demro.dbans.command.*;
import me.demro.dbans.command.tabcomplete.UniversalTabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

@UtilityClass
public final class CommandRegistrar {

    public static void register(DBans plugin) {
        Map<String, CommandExecutor> executors = coreExecutors(plugin);
        if (plugin.getConfig().getBoolean("jail.enabled", true)) {
            executors.put("jail", new JailCommand(plugin));
            executors.put("unjail", new UnjailCommand(plugin));
        }

        UniversalTabCompleter tabCompleter = new UniversalTabCompleter(plugin);
        executors.forEach((name, executor) -> {
            PluginCommand command = requireCommand(plugin, name);
            command.setExecutor(executor);
            command.setTabCompleter(tabCompleter);
        });
    }

    private static @NotNull PluginCommand requireCommand(@NotNull DBans plugin, String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            throw new PluginStartupException(
                    "Command '%s' is not declared in plugin.yml.".formatted(name));
        }
        return command;
    }

    private static @NotNull Map<String, CommandExecutor> coreExecutors(DBans plugin) {
        Map<String, CommandExecutor> executors = new LinkedHashMap<>();
        executors.put("ban", new BanCommand(plugin));
        executors.put("tempban", new TempBanCommand(plugin));
        executors.put("unban", new UnbanCommand(plugin));
        executors.put("mute", new MuteCommand(plugin));
        executors.put("tempmute", new TempMuteCommand(plugin));
        executors.put("unmute", new UnmuteCommand(plugin));
        executors.put("kick", new KickCommand(plugin));
        executors.put("banip", new BanIpCommand(plugin));
        executors.put("unbanip", new UnbanIpCommand(plugin));
        executors.put("history", new HistoryCommand(plugin));
        executors.put("droppunish", new DropPunishCommand(plugin));
        executors.put("inspect", new InspectCommand(plugin));
        executors.put("altreason", new AltReasonCommand(plugin));
        executors.put("altduration", new AltDurationCommand(plugin));
        executors.put("getuuid", new GetUuidCommand());
        executors.put("playerinfo", new PlayerInfoCommand(plugin));
        executors.put("pstat", new PStatCommand(plugin));
        executors.put("punlist", new PunListCommand(plugin));
        executors.put("presetlist", new PresetListCommand(plugin));
        executors.put("dban", new DbanCommand(plugin));
        executors.put("geoip", new GeoIpCommand(plugin));
        executors.put("twaccs", new TwaccsCommand(plugin));
        executors.put("bantwaccs", new BantwaccsCommand(plugin));
        executors.put("warn", new WarnCommand(plugin));
        executors.put("unwarn", new UnwarnCommand(plugin));
        executors.put("warnlist", new WarnListCommand(plugin));
        return executors;
    }
}

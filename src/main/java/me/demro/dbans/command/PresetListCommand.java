package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.PresetManager.PunishmentPreset;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@Slf4j
public class PresetListCommand implements CommandExecutor {

    private final DBans plugin;

    public PresetListCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!sender.hasPermission("dbans.presetlist")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        Collection<PunishmentPreset> presets = plugin.getPresetManager().getAllPresets();
        if (presets.isEmpty()) {
            MessageUtil.send(sender, "presetlist.no_presets");
            return true;
        }
        MessageUtil.send(sender, "presetlist.header");
        for (PunishmentPreset preset : presets) {
            String duration = preset.isPermanent()
                    ? plugin.getConfig().getString("permanent_word", "навсегда")
                    : me.demro.dbans.util.TimeUtil.formatDuration(preset.duration());
            String type = preset.type();
            if (type == null) type = "ban";
            String typeDisplay;
            switch (type.toLowerCase()) {
                case "ban":
                    typeDisplay = "Бан";
                    break;
                case "tempban":
                    typeDisplay = "Временный бан";
                    break;
                case "mute":
                    typeDisplay = "Мут";
                    break;
                case "tempmute":
                    typeDisplay = "Временный мут";
                    break;
                case "kick":
                    typeDisplay = "Кик";
                    break;
                default:
                    typeDisplay = type;
            }
            MessageUtil.send(sender, "presetlist.entry",
                             "name", preset.name(),
                             "type", typeDisplay,
                             "reason", preset.reason(),
                             "duration", duration);
        }
        MessageUtil.send(sender, "presetlist.footer");
        return true;
    }
}
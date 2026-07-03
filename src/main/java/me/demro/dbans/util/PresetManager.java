package me.demro.dbans.util;

import lombok.CustomLog;
import me.demro.dbans.DBans;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@CustomLog
public class PresetManager {

    private final DBans plugin;
    private final Map<String, PunishmentPreset> presets = new ConcurrentHashMap<>();
    private final Map<String, List<String>> presetNamesByType = new ConcurrentHashMap<>();

    public PresetManager(DBans plugin) {
        this.plugin = plugin;
        loadPresets();
    }

    public void loadPresets() {
        presets.clear();
        presetNamesByType.clear();
        File file = new File(plugin.getDataFolder(), "presets.yml");
        if (!file.exists()) plugin.saveResource("presets.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("presets");
        if (section == null) {
            log.warn("No presets section found in presets.yml");
            return;
        }

        for (String key : section.getKeys(false)) {
            String reason = section.getString(key + ".reason");
            String durationStr = section.getString(key + ".duration");
            String type = section.getString(key + ".type", "ban");
            String permission = section.getString(key + ".permission");
            Long duration = null;
            if (durationStr != null && !durationStr.isEmpty()) {
                try {
                    duration = TimeUtil.parseDuration(durationStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid duration in preset {}: {}", key, durationStr);
                }
            }
            PunishmentPreset preset = new PunishmentPreset(key, reason, duration, durationStr, type, permission);
            presets.put(key.toLowerCase(), preset);
        }

        for (PunishmentPreset preset : presets.values()) {
            presetNamesByType.computeIfAbsent(preset.type().toLowerCase(), k -> new ArrayList<>())
                             .add("+" + preset.name());
        }

        log.info("Loaded {} punishment presets", presets.size());
    }

    public PunishmentPreset getPreset(@NotNull String name) {
        return presets.get(name.toLowerCase());
    }

    public Collection<PunishmentPreset> getAllPresets() {
        return presets.values();
    }

    public List<PunishmentPreset> getPresetsByType(String type) {
        List<PunishmentPreset> result = new ArrayList<>();
        for (PunishmentPreset preset : presets.values()) {
            if (preset.type().equalsIgnoreCase(type)) {
                result.add(preset);
            }
        }
        return result;
    }

    public List<String> getPresetNamesByType(@NotNull String type) {
        return presetNamesByType.getOrDefault(type.toLowerCase(), Collections.emptyList());
    }

    public boolean canUsePreset(@NotNull CommandSender sender, PunishmentPreset preset) {
        if (sender.hasPermission("dbans.presets.bypass")) return true;
        if (preset.permission() == null || preset.permission().isEmpty()) return true;
        return sender.hasPermission(preset.permission());
    }

    public record PunishmentPreset(String name, String reason, Long duration, String durationRaw, String type,
                                   String permission) {

        @Contract(pure = true)
        public boolean isPermanent() {
            return duration == null;
        }
    }
}
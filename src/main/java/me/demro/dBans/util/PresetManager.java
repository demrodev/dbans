package me.demro.dBans.util;

import me.demro.dBans.DBans;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
        if (section == null) return;

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
                    plugin.getLogger().warning("Invalid duration in preset " + key + ": " + durationStr);
                }
            }
            presets.put(key.toLowerCase(), new PunishmentPreset(key, reason, duration, durationStr, type, permission));
        }

        for (PunishmentPreset preset : presets.values()) {
            presetNamesByType.computeIfAbsent(preset.getType().toLowerCase(), k -> new ArrayList<>())
                    .add("+" + preset.getName());
        }

        plugin.getLogger().info("Loaded " + presets.size() + " punishment presets");
    }

    public PunishmentPreset getPreset(String name) {
        return presets.get(name.toLowerCase());
    }

    public Collection<PunishmentPreset> getAllPresets() {
        return presets.values();
    }

    public List<PunishmentPreset> getPresetsByType(String type) {
        List<PunishmentPreset> result = new ArrayList<>();
        for (PunishmentPreset preset : presets.values()) {
            if (preset.getType().equalsIgnoreCase(type)) {
                result.add(preset);
            }
        }
        return result;
    }

    public List<String> getPresetNamesByType(String type) {
        return presetNamesByType.getOrDefault(type.toLowerCase(), Collections.emptyList());
    }

    public boolean canUsePreset(CommandSender sender, PunishmentPreset preset) {
        if (sender.hasPermission("dbans.presets.bypass")) return true;
        if (preset.getPermission() == null || preset.getPermission().isEmpty()) return true;
        return sender.hasPermission(preset.getPermission());
    }

    public static class PunishmentPreset {
        private final String name;
        private final String reason;
        private final Long duration;
        private final String durationRaw;
        private final String type;
        private final String permission;

        public PunishmentPreset(String name, String reason, Long duration, String durationRaw, String type, String permission) {
            this.name = name;
            this.reason = reason;
            this.duration = duration;
            this.durationRaw = durationRaw;
            this.type = type;
            this.permission = permission;
        }

        public String getName() { return name; }
        public String getReason() { return reason; }
        public Long getDuration() { return duration; }
        public String getDurationRaw() { return durationRaw; }
        public String getType() { return type; }
        public String getPermission() { return permission; }
        public boolean isPermanent() { return duration == null; }
    }
}
package me.demro.dBans.util;

import me.demro.dBans.DBans;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LimitsManager {
    private final DBans plugin;
    private final LuckPerms luckPerms;
    private final Map<String, GroupData> groups = new HashMap<>();
    private GroupData defaultData;

    private final Map<UUID, CachedPlayerData> playerDataCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(5);
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public LimitsManager(DBans plugin) {
        this.plugin = plugin;
        this.luckPerms = LuckPermsProvider.get();
        reload();
        startCacheCleanup();
    }

    private void startCacheCleanup() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            playerDataCache.values().removeIf(data -> now - data.timestamp > CACHE_TTL_MS);
        }, 1200L, 1200L);
    }

    public void reload() {
        groups.clear();
        playerDataCache.clear();
        File file = new File(plugin.getDataFolder(), "limits.yml");
        if (!file.exists()) plugin.saveResource("limits.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = config.getConfigurationSection("groups");
        if (sec == null) {
            plugin.getLogger().severe("limits.yml missing 'groups' section!");
            return;
        }
        for (String groupName : sec.getKeys(false)) {
            ConfigurationSection g = sec.getConfigurationSection(groupName);
            GroupData data = new GroupData();
            data.priority = g.getInt("priority", 0);
            ConfigurationSection durations = g.getConfigurationSection("max_durations");
            if (durations != null) {
                for (String key : durations.getKeys(false)) {
                    String val = durations.getString(key);
                    if (val == null) continue;
                    if (val.equals("0") || val.equalsIgnoreCase("unlimited")) {
                        data.maxDurations.put(key, -1L);
                    } else {
                        try {
                            data.maxDurations.put(key, TimeUtil.parseDuration(val));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid duration for " + groupName + "." + key + ": " + val);
                            data.maxDurations.put(key, -1L);
                        }
                    }
                }
            }

            ConfigurationSection cd = g.getConfigurationSection("cooldowns");
            if (cd != null) {
                for (String key : cd.getKeys(false)) {
                    data.cooldowns.put(key, cd.getInt(key, 0));
                }
            }

            data.immunities = g.getStringList("immunities");
            groups.put(groupName.toLowerCase(), data);
        }

        defaultData = groups.get("default");
        if (defaultData == null) {
            plugin.getLogger().warning("No 'default' group in limits.yml, creating fallback");
            defaultData = new GroupData();
            defaultData.priority = 1;
        }
        plugin.getLogger().info("Loaded limits for " + groups.size() + " groups");
    }

    private CachedPlayerData getCachedPlayerData(UUID uuid) {
        CachedPlayerData cached = playerDataCache.get(uuid);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            return cached;
        }
        String group = getPrimaryGroup(uuid);
        GroupData data = groups.getOrDefault(group.toLowerCase(), defaultData);
        cached = new CachedPlayerData(data, data.priority, group, System.currentTimeMillis());
        playerDataCache.put(uuid, cached);
        return cached;
    }

    private String getPrimaryGroup(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return "default";
        String primary = user.getPrimaryGroup();
        return primary != null ? primary : "default";
    }

    private String getPrimaryGroup(Player player) {
        return getPrimaryGroup(player.getUniqueId());
    }

    private String getPrimaryGroup(OfflinePlayer player) {
        return getPrimaryGroup(player.getUniqueId());
    }

    private GroupData getGroupData(Player player) {
        return getCachedPlayerData(player.getUniqueId()).data;
    }

    private GroupData getGroupData(OfflinePlayer player) {
        return getCachedPlayerData(player.getUniqueId()).data;
    }

    public long getMaxDuration(Player issuer, String type) {
        if (issuer.hasPermission("dbans.duration.bypass")) return -1L;
        if (issuer.hasPermission("dbans.duration." + type)) return -1L;
        GroupData data = getGroupData(issuer);
        Long val = data.maxDurations.get(type);
        if (val == null) val = defaultData.maxDurations.get(type);
        return val != null ? val : -1L;
    }

    public boolean isOnCooldown(Player player, String command) {
        if (player.hasPermission("dbans.cooldown.bypass")) return false;
        if (player.hasPermission("dbans.cooldown." + command)) return false;
        int seconds = getBaseCooldown(player, command);
        if (seconds <= 0) return false;
        String key = command + "#" + player.getUniqueId();
        Long last = cooldowns.get(key);
        if (last == null) return false;
        return (System.currentTimeMillis() - last) < seconds * 1000L;
    }

    public int getRemainingCooldown(Player player, String command) {
        if (player.hasPermission("dbans.cooldown.bypass")) return 0;
        if (player.hasPermission("dbans.cooldown." + command)) return 0;
        int seconds = getBaseCooldown(player, command);
        if (seconds <= 0) return 0;
        String key = command + "#" + player.getUniqueId();
        Long last = cooldowns.get(key);
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        long remaining = seconds * 1000L - elapsed;
        return remaining > 0 ? (int) (remaining / 1000) : 0;
    }

    public void setCooldown(Player player, String command) {
        if (player.hasPermission("dbans.cooldown.bypass")) return;
        if (player.hasPermission("dbans.cooldown." + command)) return;
        int seconds = getBaseCooldown(player, command);
        if (seconds <= 0) return;
        String key = command + "#" + player.getUniqueId();
        cooldowns.put(key, System.currentTimeMillis());
    }

    private int getBaseCooldown(Player player, String command) {
        GroupData data = getGroupData(player);
        Integer val = data.cooldowns.get(command);
        if (val == null) val = defaultData.cooldowns.get(command);
        return val != null ? val : 0;
    }

    public int getPriority(Player player) {
        return getCachedPlayerData(player.getUniqueId()).priority;
    }

    public int getPriority(OfflinePlayer player) {
        return getCachedPlayerData(player.getUniqueId()).priority;
    }

    public boolean hasGroupImmunity(Player target, String punishmentType) {
        GroupData data = getGroupData(target);
        if (data.immunities.contains("bypass")) return true;
        return data.immunities.contains(punishmentType);
    }

    public boolean hasGroupImmunity(OfflinePlayer target, String punishmentType) {
        GroupData data = getGroupData(target);
        if (data.immunities.contains("bypass")) return true;
        return data.immunities.contains(punishmentType);
    }

    public boolean isImmune(Player target, String punishmentType) {
        if (target.hasPermission("punishment." + punishmentType + ".immune")) return true;
        return hasGroupImmunity(target, punishmentType);
    }

    public boolean isImmune(OfflinePlayer target, String punishmentType) {
        return hasGroupImmunity(target, punishmentType);
    }

    public boolean canPunish(CommandSender issuer, OfflinePlayer target) {
        if (!(issuer instanceof Player)) return true;
        Player issuerPlayer = (Player) issuer;
        if (issuerPlayer.hasPermission("dbans.priority.bypass")) return true;
        int issuerPriority = getPriority(issuerPlayer);
        int targetPriority = getPriority(target);
        if (issuerPriority < targetPriority) {
            MessageUtil.send(issuer, "cannot_punish_higher_priority",
                    "target", target.getName(),
                    "target_priority", String.valueOf(targetPriority),
                    "issuer_priority", String.valueOf(issuerPriority));
            return false;
        }
        return true;
    }

    public boolean canUseSilent(Player player, String command) {
        if (player.hasPermission("dbans.silent.bypass")) return true;
        return player.hasPermission("dbans.silent." + command);
    }

    private static class GroupData {
        int priority = 0;
        Map<String, Long> maxDurations = new HashMap<>();
        Map<String, Integer> cooldowns = new HashMap<>();
        List<String> immunities = java.util.Collections.emptyList();
    }

    private static class CachedPlayerData {
        final GroupData data;
        final int priority;
        final String group;
        final long timestamp;

        CachedPlayerData(GroupData data, int priority, String group, long timestamp) {
            this.data = data;
            this.priority = priority;
            this.group = group;
            this.timestamp = timestamp;
        }
    }
}
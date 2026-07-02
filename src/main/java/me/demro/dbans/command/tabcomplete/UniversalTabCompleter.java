package me.demro.dbans.command.tabcomplete;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.model.Warning;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class UniversalTabCompleter implements TabCompleter {

    private final DBans plugin;
    private final Map<String, List<String>> presetCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> activeIdsCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTime = new ConcurrentHashMap<>();
    private final long cacheTtlMillis = TimeUnit.SECONDS.toMillis(5);

    public UniversalTabCompleter(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String command = cmd.getName().toLowerCase();
        List<String> completions = new ArrayList<>();

        switch (command) {
            case "ban":
            case "mute":
            case "banip":
                if (args.length == 1) {
                    completions.addAll(getOnlinePlayers());
                } else if (args.length == 2) {
                    completions.addAll(getPresetNamesCached(command));
                    completions.add("Reason...");
                } else if (args.length == 3) {
                    completions.add("-s");
                } else if (args.length == 4) {
                    completions.add("server:lobby");
                    completions.add("server:survival");
                }
                break;

            case "tempban":
            case "tempmute":
                if (args.length == 1) {
                    completions.addAll(getOnlinePlayers());
                } else if (args.length == 2) {
                    completions.addAll(getPresetNamesCached(command));
                    completions.addAll(getCommonDurations());
                } else if (args.length == 3) {
                    completions.add("Reason...");
                } else if (args.length == 4) {
                    completions.add("-s");
                } else if (args.length == 5) {
                    completions.add("server:lobby");
                    completions.add("server:survival");
                }
                break;

            case "jail":
            case "warn":
                if (args.length == 1) {
                    completions.addAll(getOnlinePlayers());
                } else if (args.length == 2) {
                    completions.addAll(getPresetNamesCached(command));
                    completions.addAll(getCommonDurations());
                    completions.add("Reason...");
                } else if (args.length == 3) {
                    completions.add("Reason...");
                } else if (args.length == 4) {
                    completions.add("-s");
                } else if (args.length == 5) {
                    completions.add("server:lobby");
                    completions.add("server:survival");
                }
                break;

            case "kick":
                if (args.length == 1) {
                    completions.addAll(getOnlinePlayers());
                } else if (args.length == 2) {
                    completions.addAll(getPresetNamesCached("kick"));
                    completions.add("AFK");
                    completions.add("Advertisement");
                }
                break;

            case "unban":
            case "unmute":
            case "unjail":
            case "unwarn":
            case "unbanip":
                if (args.length == 1) {
                    completions.addAll(getActiveIdsCached(command, sender));
                    completions.addAll(getOnlinePlayers());
                }
                break;

            case "history":
            case "inspect":
            case "playerinfo":
            case "pstat":
            case "warnlist":
            case "twaccs":
            case "bantwaccs":
            case "getuuid":
            case "geoip":
                if (args.length == 1) {
                    completions.addAll(getOnlinePlayers());
                }
                break;

            case "punlist":
                if (args.length == 1) {
                    completions.addAll(getPagesList(5));
                }
                break;

            case "presetlist":
                if (args.length == 1) {
                    completions.addAll(getPagesList(3));
                }
                break;

            case "dban":
                if (args.length == 1) {
                    completions.add("reload");
                }
                break;

            case "droppunish":
                if (args.length == 1) {
                    completions.add("#ID");
                    completions.add("all");
                    List<String> recentIds = getRecentPunishmentIds(3);
                    completions.addAll(recentIds);
                }
                break;

            case "altreason":
                if (args.length == 1) {
                    completions.addAll(getAllPunishmentIdsWithPrefixCached());
                } else if (args.length == 2) {
                    completions.add("New reason...");
                }
                break;

            case "altduration":
                if (args.length == 1) {
                    completions.addAll(getAllPunishmentIdsWithPrefixCached());
                } else if (args.length == 2) {
                    completions.addAll(getCommonDurations());
                }
                break;
        }

        String lastArg = args.length > 0 ? args[args.length - 1] : "";
        return StringUtil.copyPartialMatches(lastArg, completions, new ArrayList<>());
    }

    private List<String> getPresetNamesCached(String commandType) {
        String cacheKey = "preset_" + commandType;
        Long time = cacheTime.get(cacheKey);
        if (time != null && System.currentTimeMillis() - time < cacheTtlMillis) {
            return presetCache.getOrDefault(cacheKey, Collections.emptyList());
        }
        List<String> presets;
        try {
            presets = plugin.getPresetManager().getPresetNamesByType(commandType);
        } catch (Exception e) {
            log.warn("Failed to get preset names for {}: {}", commandType, e.getMessage());
            presets = new ArrayList<>();
        }
        presetCache.put(cacheKey, presets);
        cacheTime.put(cacheKey, System.currentTimeMillis());
        return presets;
    }

    private List<String> getActiveIdsCached(String command, CommandSender sender) {
        String cacheKey = "active_" + command;
        Long time = cacheTime.get(cacheKey);
        if (time != null && System.currentTimeMillis() - time < cacheTtlMillis) {
            return activeIdsCache.getOrDefault(cacheKey, Collections.emptyList());
        }
        List<String> ids = new ArrayList<>();
        String prefix = "#";
        switch (command) {
            case "unban":
                List<Punishment> bans = plugin.getDatabase().getAllActivePunishmentsByType(PunishmentType.BAN);
                for (Punishment p : bans) ids.add(prefix + p.getId());
                break;
            case "unmute":
                List<Punishment> mutes = plugin.getDatabase().getAllActivePunishmentsByType(PunishmentType.MUTE);
                for (Punishment p : mutes) ids.add(prefix + p.getId());
                break;
            case "unjail":
                List<JailPunishment> jails = plugin.getDatabase().getAllActiveJails();
                for (JailPunishment j : jails) ids.add(prefix + j.getId());
                break;
            case "unwarn":
                List<Warning> warns = plugin.getDatabase().getAllWarnings();
                for (Warning w : warns) {
                    if (w.isActive()) ids.add(prefix + w.getId());
                }
                break;
            case "unbanip":
                ids.addAll(plugin.getDatabase().getAllIpBans());
                break;
        }
        activeIdsCache.put(cacheKey, ids);
        cacheTime.put(cacheKey, System.currentTimeMillis());
        return ids;
    }

    private List<String> getAllPunishmentIdsWithPrefixCached() {
        String cacheKey = "all_ids";
        Long time = cacheTime.get(cacheKey);
        if (time != null && System.currentTimeMillis() - time < cacheTtlMillis) {
            return activeIdsCache.getOrDefault(cacheKey, Collections.emptyList());
        }
        List<String> ids = new ArrayList<>();
        List<Punishment> all = plugin.getDatabase().getAllPunishments();
        int limit = Math.min(all.size(), 10);
        for (int i = 0; i < limit; i++) {
            ids.add("#" + all.get(i).getId());
        }
        activeIdsCache.put(cacheKey, ids);
        cacheTime.put(cacheKey, System.currentTimeMillis());
        return ids;
    }

    private List<String> getRecentPunishmentIds(int count) {
        List<String> ids = new ArrayList<>();
        List<Punishment> all = plugin.getDatabase().getAllPunishments();
        int limit = Math.min(all.size(), count);
        for (int i = 0; i < limit; i++) {
            ids.add("#" + all.get(i).getId());
        }
        return ids;
    }

    private List<String> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream()
                     .map(Player::getName)
                     .collect(Collectors.toList());
    }

    private List<String> getCommonDurations() {
        return Arrays.asList("1h", "2h", "6h", "12h", "1d", "3d", "7d", "30d");
    }

    private List<String> getPagesList(int maxPage) {
        List<String> pages = new ArrayList<>();
        for (int i = 1; i <= maxPage; i++) {
            pages.add(String.valueOf(i));
        }
        return pages;
    }
}
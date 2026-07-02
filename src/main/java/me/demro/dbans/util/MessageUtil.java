package me.demro.dbans.util;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class MessageUtil {

    private static final Map<String, String> cache = new HashMap<>();
    private static final Pattern HOVER_PATTERN = Pattern.compile("\\{hover:(.+)}$", Pattern.DOTALL);
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer CHAT_SERIALIZER = LegacyComponentSerializer.builder()
                                                                                              .character('&')
                                                                                              .hexColors()
                                                                                              .build();
    private static final LegacyComponentSerializer KICK_SERIALIZER = LegacyComponentSerializer.builder()
                                                                                              .character('§')
                                                                                              .hexColors()
                                                                                              .build();
    private static DBans plugin;
    private static YamlConfiguration messagesConfig;

    public static void init(DBans pl) {
        plugin = pl;
        reloadMessages();
        TimeUtil.init(pl);
    }

    public static void reloadMessages() {
        cache.clear();
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        messagesConfig = YamlConfiguration.loadConfiguration(file);
    }

    @Contract("null -> null")
    public static String getRawMessage(String key) {
        if (key == null) return null;
        String cached = cache.get(key);
        if (cached != null) return cached;
        if (messagesConfig != null && messagesConfig.contains(key)) {
            String value = messagesConfig.getString(key);
            if (value != null) {
                cache.put(key, value);
                return value;
            }
        }
        String fallback = plugin.getConfig().getString("messages." + key);
        if (fallback != null) {
            cache.put(key, fallback);
            return fallback;
        }
        log.warn("Missing message key: {}", key);
        return null;
    }

    private static String replacePlaceholders(String message, Object @NotNull ... placeholders) {
        String result = message;
        String prefix = plugin.getConfig().getString("prefix", "");
        String permanent = plugin.getConfig().getString("permanent_word", "навсегда");
        result = result.replace("%prefix%", prefix).replace("%permanent%", permanent);
        for (int i = 0; i < placeholders.length; i += 2) {
            String key = placeholders[i].toString();
            String value = placeholders[i + 1].toString();
            result = result.replace("%" + key + "%", value);
        }
        return result;
    }

    @Contract("null, _ -> !null")
    public static Component deserialize(String message, Object... placeholders) {
        if (message == null) return Component.empty();
        String processed = replacePlaceholders(message, placeholders);
        Matcher hoverMatcher = HOVER_PATTERN.matcher(processed);
        String main = processed;
        String hover = null;
        if (hoverMatcher.find()) {
            main = processed.substring(0, hoverMatcher.start());
            hover = hoverMatcher.group(1);
        }
        Component component = CHAT_SERIALIZER.deserialize(main);
        if (hover != null) {
            component = component.hoverEvent(HoverEvent.showText(CHAT_SERIALIZER.deserialize(hover)));
        }
        return component;
    }

    public static @NotNull String serializeForKick(String message, Object... placeholders) {
        if (message == null) return "";
        String processed = replacePlaceholders(message, placeholders);
        String withoutHover = HOVER_PATTERN.matcher(processed).replaceAll("");
        String withoutHex = HEX_PATTERN.matcher(withoutHover).replaceAll("");
        return withoutHex.replace('&', '§');
    }

    public static @NotNull Component deserializeForKick(String message, Object... placeholders) {
        String plain = serializeForKick(message, placeholders);
        return Component.text(plain);
    }

    public static @NotNull String colorize(String message, Object... placeholders) {
        if (message == null) return "";
        String processed = replacePlaceholders(message, placeholders);
        Matcher hoverMatcher = HOVER_PATTERN.matcher(processed);
        if (hoverMatcher.find()) {
            processed = processed.substring(0, hoverMatcher.start());
        }
        Component component = CHAT_SERIALIZER.deserialize(processed);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public static @NotNull String colorize(String message) {
        return colorize(message, new Object[0]);
    }

    public static void send(CommandSender sender, String key, Object... placeholders) {
        if (sender == null) return;
        String raw = getRawMessage(key);
        if (raw == null) {
            sender.sendMessage(Component.text("§c[DBans] Missing message: " + key));
            return;
        }
        sender.sendMessage(deserialize(raw, placeholders));
    }

    public static void broadcast(String permission, String key, Object... placeholders) {
        String raw = getRawMessage(key);
        if (raw == null) {
            log.warn("Missing message key for broadcast: {}", key);
            return;
        }
        Component component = deserialize(raw, placeholders);
        if (permission == null) {
            Bukkit.getServer().sendMessage(component);
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission(permission) || p.hasPermission("dbans.notify.*")) {
                    p.sendMessage(component);
                }
            }
            Bukkit.getConsoleSender().sendMessage(component);
        }
    }
}
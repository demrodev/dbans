package me.demro.dbans.util;

import me.demro.dbans.DBans;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {
    private static DBans plugin;
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smhd])");
    private static final java.util.Map<Long, String> formattedCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_MAX_SIZE = 1000;

    public static void init(DBans pl) { plugin = pl; }

    public static long parseDuration(String input) throws IllegalArgumentException {
        Matcher m = TIME_PATTERN.matcher(input);
        long total = 0;
        int lastEnd = 0;
        while (m.find()) {
            long num = Long.parseLong(m.group(1));
            char unit = m.group(2).charAt(0);
            long millis;
            switch (unit) {
                case 's': millis = TimeUnit.SECONDS.toMillis(num); break;
                case 'm': millis = TimeUnit.MINUTES.toMillis(num); break;
                case 'h': millis = TimeUnit.HOURS.toMillis(num); break;
                case 'd': millis = TimeUnit.DAYS.toMillis(num); break;
                default: throw new IllegalArgumentException("Invalid time unit: " + unit);
            }
            total += millis;
            lastEnd = m.end();
        }
        if (lastEnd != input.length()) throw new IllegalArgumentException("Unparsable time string: " + input);
        if (total <= 0) throw new IllegalArgumentException("Duration must be positive");
        return total;
    }

    public static boolean isTimeFormat(String input) {
        return TIME_PATTERN.matcher(input).matches();
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return "0" + getUnitForm(0, "second");
        if (formattedCache.size() < CACHE_MAX_SIZE) {
            String cached = formattedCache.get(millis);
            if (cached != null) return cached;
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" ").append(getUnitForm(days, "day")).append(" ");
        if (hours > 0) sb.append(hours).append(" ").append(getUnitForm(hours, "hour")).append(" ");
        if (minutes > 0) sb.append(minutes).append(" ").append(getUnitForm(minutes, "minute")).append(" ");
        if (seconds > 0 && sb.length() == 0) sb.append(seconds).append(" ").append(getUnitForm(seconds, "second"));

        String result = sb.toString().trim();
        if (formattedCache.size() < CACHE_MAX_SIZE) {
            formattedCache.put(millis, result);
        }
        return result;
    }

    private static String getUnitForm(long number, String unitKey) {
        if (plugin == null) return unitKey;
        String path = "time_units." + unitKey + ".";
        long n = Math.abs(number);
        int lastDigit = (int) (n % 10);
        int lastTwo = (int) (n % 100);
        if (lastTwo >= 11 && lastTwo <= 14) {
            return plugin.getConfig().getString(path + "many", unitKey);
        }
        switch (lastDigit) {
            case 1: return plugin.getConfig().getString(path + "one", unitKey);
            case 2:
            case 3:
            case 4: return plugin.getConfig().getString(path + "few", unitKey);
            default: return plugin.getConfig().getString(path + "many", unitKey);
        }
    }
}
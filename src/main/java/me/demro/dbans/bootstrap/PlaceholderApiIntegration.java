package me.demro.dbans.bootstrap;

import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import me.demro.dbans.DBans;
import me.demro.dbans.placeholder.DBansExpansion;
import org.bukkit.Bukkit;

@CustomLog
@UtilityClass
public final class PlaceholderApiIntegration {

    public static void tryRegister(DBans plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new DBansExpansion(plugin).register();
        log.info("Registered PlaceholderAPI placeholders");
    }
}

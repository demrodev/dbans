package me.demro.dbans.bootstrap;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.placeholder.DBansExpansion;
import org.bukkit.Bukkit;

@Slf4j
@UtilityClass
public final class PlaceholderApiIntegration {

    public static void tryRegister(DBans plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new DBansExpansion(plugin).register();
        log.info("Registered PlaceholderAPI placeholders.");
    }
}

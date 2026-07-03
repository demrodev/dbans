package me.demro.dbans.bootstrap;

import lombok.experimental.UtilityClass;
import me.demro.dbans.DBans;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@UtilityClass
public final class JailConfigLoader {

    public static @NotNull YamlConfiguration loadOrCreateDefault(DBans plugin, @NotNull File file) {
        if (!file.exists()) {
            plugin.saveResource("jail.yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}

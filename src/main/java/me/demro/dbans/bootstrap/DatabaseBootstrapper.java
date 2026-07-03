package me.demro.dbans.bootstrap;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.database.DatabaseManager;
import me.demro.dbans.database.H2Database;
import me.demro.dbans.database.MySQLDatabase;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

@Slf4j
@UtilityClass
public final class DatabaseBootstrapper {

    public static @NotNull DatabaseManager create(@NotNull DBans plugin, String mode) {
        String dbType = plugin.getConfig().getString("database.type", "h2");

        if (!"single".equalsIgnoreCase(mode) && "h2".equalsIgnoreCase(dbType)) {
            throw new PluginStartupException(
                    "Mode '%s' requires MySQL! H2 is not supported.".formatted(mode));
        }

        DatabaseManager database = "mysql".equalsIgnoreCase(dbType)
                ? new MySQLDatabase(plugin)
                : new H2Database(plugin);

        try {
            database.init();
        } catch (SQLException e) {
            throw new PluginStartupException("Failed to initialize database: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PluginStartupException("Unexpected error while initializing database: " + e.getMessage(), e);
        }

        log.info("Database connected: {}", dbType);
        return database;
    }
}

package me.demro.dbans.bootstrap;

import lombok.experimental.UtilityClass;
import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.DBansAPI;
import me.demro.dlibs.dbans.api.spi.DBansServiceRegistrar;

@UtilityClass
public final class ApiRegistrar {

    @SuppressWarnings("UnstableApiUsage")
    public static void register(DBans plugin, DBansAPI api) {
        DBansServiceRegistrar.register(plugin, api);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static void unregister(DBansAPI api) {
        DBansServiceRegistrar.unregister(api);
    }
}

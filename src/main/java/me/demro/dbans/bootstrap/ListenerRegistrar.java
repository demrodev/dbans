package me.demro.dbans.bootstrap;

import lombok.experimental.UtilityClass;
import me.demro.dbans.DBans;
import me.demro.dbans.listener.ChatListener;
import me.demro.dbans.listener.JailListener;
import me.demro.dbans.listener.LoginListener;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public final class ListenerRegistrar {

    public static void register(@NotNull DBans plugin) {
        plugin.getServer().getPluginManager().registerEvents(new LoginListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ChatListener(plugin), plugin);

        if (plugin.getConfig().getBoolean("jail.enabled", true)) {
            plugin.getServer().getPluginManager().registerEvents(new JailListener(plugin), plugin);
        }
    }
}

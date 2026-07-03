package me.demro.dbans.bootstrap;

import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import me.demro.dbans.DBans;
import me.demro.dbans.sync.Constants;
import me.demro.dbans.sync.ProxySyncManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@CustomLog
@UtilityClass
public final class ProxySyncBootstrapper {

    public static @Nullable ProxySyncManager start(@NotNull DBans plugin) {
        if (!ProxySyncManager.isSyncMode(plugin.getMode())) {
            log.info("Proxy sync disabled (mode={})", plugin.getMode());
            return null;
        }

        ProxySyncManager proxySyncManager = new ProxySyncManager(plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, Constants.CHANNEL_NAME);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, Constants.CHANNEL_NAME, proxySyncManager);
        log.info("Proxy sync manager registered");
        return proxySyncManager;
    }

    public static void stop(@NotNull DBans plugin, @Nullable ProxySyncManager proxySyncManager) {
        if (proxySyncManager == null) {
            return;
        }
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, Constants.CHANNEL_NAME);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, Constants.CHANNEL_NAME, proxySyncManager);
    }
}

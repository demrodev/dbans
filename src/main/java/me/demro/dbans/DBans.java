package me.demro.dbans;

import lombok.CustomLog;
import lombok.Getter;
import me.demro.dbans.api.impl.DBansAPIImpl;
import me.demro.dbans.bootstrap.*;
import me.demro.dbans.database.DatabaseManager;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.sync.ProxySyncManager;
import me.demro.dbans.util.*;
import me.demro.dbans.util.geo.GeoIpManager;
import me.demro.dlibs.dbans.api.DBansAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CustomLog
@Getter
public final class DBans extends JavaPlugin {

    private static final int METRICS_PLUGIN_ID = 32027;

    private DatabaseManager database;
    private CacheManager cacheManager;
    private PlayerCache playerCache;
    private LuckPermsHook luckPermsHook;
    private PresetManager presetManager;
    private SelfPunishChecker selfPunishChecker;
    private GeoIpManager geoIpManager;
    private LimitsManager limitsManager;
    private JailManager jailManager;
    private AltAccountManager altAccountManager;
    private WarnManager warnManager;
    private PunishmentSyncManager punishmentSyncManager;
    private ProxySyncManager proxySyncManager;
    private MuteExpiryScheduler muteExpiryScheduler;
    private DBansAPI api;

    private File jailConfigFile;
    private YamlConfiguration jailConfig;

    @Override
    public void onEnable() {
        try {
            bootstrap();
        } catch (PluginStartupException e) {
            log.error(e.getMessage(), e.getCause());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void bootstrap() {
        saveDefaultConfig();
        reloadConfig();
        MessageUtil.init(this);

        String mode = getMode();

        database = DatabaseBootstrapper.create(this, mode);
        cacheManager = new CacheManager(this);
        database.setCacheManager(cacheManager);

        PlaceholderApiIntegration.tryRegister(this);

        jailConfigFile = new File(getDataFolder(), "jail.yml");
        jailConfig = JailConfigLoader.loadOrCreateDefault(this, jailConfigFile);
//        log.info("jail.yml loaded.");

        jailManager = new JailManager(this);
        luckPermsHook = new LuckPermsHook(this);
        presetManager = new PresetManager(this);
        selfPunishChecker = new SelfPunishChecker();
        geoIpManager = new GeoIpManager(this);
        limitsManager = new LimitsManager(this);
        altAccountManager = new AltAccountManager(this);
        warnManager = new WarnManager(this);

        api = new DBansAPIImpl(this);
        ApiRegistrar.register(this, api);

        if (ProxySyncManager.isSyncMode(mode)) {
            punishmentSyncManager = new PunishmentSyncManager(this);
        }
        proxySyncManager = ProxySyncBootstrapper.start(this);

        muteExpiryScheduler = new MuteExpiryScheduler(this, proxySyncManager);
        muteExpiryScheduler.deactivateExpiredMutes();

        CommandRegistrar.register(this);
        ListenerRegistrar.register(this);

        muteExpiryScheduler.rescheduleAll();

        //noinspection UnstableApiUsage
        log.info("Version running: {}", getPluginMeta().getVersion());
        new Metrics(this, METRICS_PLUGIN_ID);

        playerCache = new PlayerCache();
        Bukkit.getOnlinePlayers().forEach(playerCache::update);
    }

    @Override
    public void onDisable() {
        if (muteExpiryScheduler != null) {
            muteExpiryScheduler.cancelAll();
        }

        ProxySyncBootstrapper.stop(this, proxySyncManager);
        proxySyncManager = null;

        if (api != null) {
            ApiRegistrar.unregister(api);
            api = null;
        }

        if (database != null) {
            database.close();
        }
    }

    public void scheduleMuteExpiry(@NotNull Punishment mute) {
        requireScheduler().schedule(mute);
    }

    public void cancelMuteExpiry(String punishmentId) {
        requireScheduler().cancel(punishmentId);
    }

    @Contract(pure = true)
    private MuteExpiryScheduler requireScheduler() {
        if (muteExpiryScheduler == null) {
            throw new IllegalStateException(
                    "Mute expiry scheduler is not available; the plugin has not finished starting or failed to start");
        }
        return muteExpiryScheduler;
    }

    @Contract(" -> !null")
    public String getServerName() {
        return getConfig().getString("server_name", "unknown");
    }

    @Contract(" -> !null")
    public String getMode() {
        return getConfig().getString("mode", "single");
    }

    public void reloadJailConfig() {
        if (jailConfigFile == null) {
            jailConfigFile = new File(getDataFolder(), "jail.yml");
        }
        jailConfig = JailConfigLoader.loadOrCreateDefault(this, jailConfigFile);
        if (jailManager != null) {
            jailManager.reload();
        }
        log.info("jail.yml reloaded");
    }

    public void addNotification(UUID playerUuid, String messageKey, Map<String, String> placeholders) {
        database.addNotification(playerUuid, messageKey, placeholders);
    }

    public List<Map<String, String>> getAndClearNotifications(UUID playerUuid) {
        return database.getAndClearNotifications(playerUuid);
    }
}

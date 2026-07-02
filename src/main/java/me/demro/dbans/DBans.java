package me.demro.dbans;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.api.impl.DBansAPIImpl;
import me.demro.dbans.api.adapter.NewPunishmentAdapter;
import me.demro.dbans.command.*;
import me.demro.dbans.command.tabcomplete.UniversalTabCompleter;
import me.demro.dbans.database.*;
import me.demro.dbans.listener.*;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.placeholder.DBansExpansion;
import me.demro.dbans.sync.Constants;
import me.demro.dbans.sync.ProxySyncManager;
import me.demro.dbans.util.*;
import me.demro.dbans.util.geo.GeoIpManager;
import me.demro.dlibs.dbans.api.DBansAPI;
import me.demro.dlibs.dbans.api.event.EventOrigin;
import me.demro.dlibs.dbans.api.event.PunishmentExpireEvent;
import me.demro.dlibs.dbans.api.spi.DBansServiceRegistrar;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Getter
@Setter
public final class DBans extends JavaPlugin {

    private DatabaseManager database;
    private LuckPermsHook luckPermsHook;
    private PresetManager presetManager;
    private SelfPunishChecker selfPunishChecker;
    private GeoIpManager geoIpManager;
    private LimitsManager limitsManager;
    private final Map<String, BukkitTask> muteExpiryTasks = new HashMap<>();
    private JailManager jailManager;
    private YamlConfiguration jailConfig;
    private AltAccountManager altAccountManager;
    private WarnManager warnManager;
    private File jailConfigFile;
    private PunishmentSyncManager punishmentSyncManager;
    private CacheManager cacheManager;
    private PlayerCache playerCache;
    private ProxySyncManager proxySyncManager;

    // НОВОЕ API
    private DBansAPI api;

    // ===== МЕТОДЫ УВЕДОМЛЕНИЙ =====
    public void addNotification(UUID playerUuid, String messageKey, Map<String, String> placeholders) {
        database.addNotification(playerUuid, messageKey, placeholders);
    }

    public List<Map<String, String>> getAndClearNotifications(UUID playerUuid) {
        return database.getAndClearNotifications(playerUuid);
    }

    public void clearNotifications(UUID playerUuid) {
        database.clearNotifications(playerUuid);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        MessageUtil.init(this);

        String dbType = getConfig().getString("database.type", "h2");
        String mode = getMode();
        if (!"single".equalsIgnoreCase(mode) && "h2".equalsIgnoreCase(dbType)) {
            log.error("Mode '{}' requires MySQL! H2 is not supported. Disabling plugin.", mode);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            if (dbType.equalsIgnoreCase("mysql")) {
                database = new MySQLDatabase(this);
            } else {
                database = new H2Database(this);
            }
            database.init();
            log.info("Database connected: {}", dbType);
        } catch (SQLException e) {
            log.error("Failed to initialize database: {}", e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        cacheManager = new CacheManager(this);
        if (database != null) {
            database.setCacheManager(cacheManager);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DBansExpansion(this).register();
            log.info("PlaceholderAPI expansion registered.");
        }

        jailConfigFile = new File(getDataFolder(), "jail.yml");
        if (!jailConfigFile.exists()) saveResource("jail.yml", false);
        jailConfig = YamlConfiguration.loadConfiguration(jailConfigFile);
        log.info("jail.yml loaded.");

        jailManager = new JailManager(this);
        luckPermsHook = new LuckPermsHook(this);
        presetManager = new PresetManager(this);
        selfPunishChecker = new SelfPunishChecker(this);
        geoIpManager = new GeoIpManager(this);
        limitsManager = new LimitsManager(this);
        altAccountManager = new AltAccountManager(this);
        warnManager = new WarnManager(this);

        // ===== ИНИЦИАЛИЗАЦИЯ НОВОГО API =====
        this.api = new DBansAPIImpl(this);
        DBansServiceRegistrar.register(this, api);
        log.info("New dBans API registered and ready.");

        if ("sync".equalsIgnoreCase(mode) || "sync_static".equalsIgnoreCase(mode)) {
            punishmentSyncManager = new PunishmentSyncManager(this);
        }

        // Прокси-синхронизация
        if (mode.equalsIgnoreCase("sync") || mode.equalsIgnoreCase("sync_static")) {
            proxySyncManager = new ProxySyncManager(this);
            getServer().getMessenger().registerOutgoingPluginChannel(this, Constants.CHANNEL_NAME);
            getServer().getMessenger().registerIncomingPluginChannel(this, Constants.CHANNEL_NAME, proxySyncManager);
            log.info("✅ Proxy sync manager registered.");
        } else {
            log.info("ℹ️ Proxy sync disabled (mode={})", mode);
        }

        // Проверка и деактивация истекших мутов при загрузке
        checkAndDeactivateExpiredMutes();

        // Регистрация команд
        getCommand("ban").setExecutor(new BanCommand(this));
        getCommand("tempban").setExecutor(new TempBanCommand(this));
        getCommand("unban").setExecutor(new UnbanCommand(this));
        getCommand("mute").setExecutor(new MuteCommand(this));
        getCommand("tempmute").setExecutor(new TempMuteCommand(this));
        getCommand("unmute").setExecutor(new UnmuteCommand(this));
        getCommand("kick").setExecutor(new KickCommand(this));
        getCommand("banip").setExecutor(new BanIpCommand(this));
        getCommand("unbanip").setExecutor(new UnbanIpCommand(this));
        getCommand("history").setExecutor(new HistoryCommand(this));
        getCommand("droppunish").setExecutor(new DropPunishCommand(this));
        getCommand("inspect").setExecutor(new InspectCommand(this));
        getCommand("altreason").setExecutor(new AltReasonCommand(this));
        getCommand("altduration").setExecutor(new AltDurationCommand(this));
        getCommand("getuuid").setExecutor(new GetUuidCommand(this));
        getCommand("playerinfo").setExecutor(new PlayerInfoCommand(this));
        getCommand("pstat").setExecutor(new PStatCommand(this));
        getCommand("punlist").setExecutor(new PunListCommand(this));
        getCommand("presetlist").setExecutor(new PresetListCommand(this));
        getCommand("dban").setExecutor(new DbanCommand(this));
        getCommand("geoip").setExecutor(new GeoIpCommand(this));
        getCommand("twaccs").setExecutor(new TwaccsCommand(this));
        getCommand("bantwaccs").setExecutor(new BantwaccsCommand(this));
        getCommand("warn").setExecutor(new WarnCommand(this));
        getCommand("unwarn").setExecutor(new UnwarnCommand(this));
        getCommand("warnlist").setExecutor(new WarnListCommand(this));
        if (getConfig().getBoolean("jail.enabled", true)) {
            getCommand("jail").setExecutor(new JailCommand(this));
            getCommand("unjail").setExecutor(new UnjailCommand(this));
        }

        getServer().getPluginManager().registerEvents(new LoginListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        if (getConfig().getBoolean("jail.enabled", true)) {
            getServer().getPluginManager().registerEvents(new JailListener(this), this);
        }

        UniversalTabCompleter universalTabCompleter = new UniversalTabCompleter(this);
        for (String cmd : new String[]{"ban", "tempban", "mute", "tempmute", "kick", "unban", "unmute",
                "banip", "unbanip", "history", "droppunish", "altreason", "altduration",
                "getuuid", "playerinfo", "pstat", "inspect", "punlist", "presetlist", "dban", "geoip",
                "jail", "unjail", "warn", "unwarn", "warnlist", "twaccs", "bantwaccs"}) {
            getCommand(cmd).setTabCompleter(universalTabCompleter);
        }

        // Планирование активных мутов
        rescheduleAllMutes();

        log.info("DBans v{} by demrodev enabled", getPluginMeta().getVersion());

        int pluginId = 32027;
        new Metrics(this, pluginId);

        playerCache = new PlayerCache();
        for (Player p : Bukkit.getOnlinePlayers()) {
            playerCache.update(p);
        }
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : muteExpiryTasks.values()) {
            task.cancel();
        }
        muteExpiryTasks.clear();

        if (proxySyncManager != null) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, Constants.CHANNEL_NAME);
            getServer().getMessenger().unregisterIncomingPluginChannel(this, Constants.CHANNEL_NAME, proxySyncManager);
            proxySyncManager = null;
        }

        // Отмена регистрации API
        if (api != null) {
            DBansServiceRegistrar.unregister(api);
            api = null;
        }

        if (database != null) {
            database.close();
        }
        log.info("DBans disabled");
    }

    // ===== МЕТОД ДЛЯ ДЕАКТИВАЦИИ ИСТЕКШИХ МУТОВ =====
    private void checkAndDeactivateExpiredMutes() {
        List<Punishment> activeMutes = database.getAllActivePunishmentsByType(PunishmentType.MUTE);
        long now = System.currentTimeMillis();
        boolean anyExpired = false;

        for (Punishment mute : activeMutes) {
            if (mute.getEndTime() != null && mute.getEndTime() <= now) {
                mute.setActive(false);
                database.updatePunishment(mute);
                log.info("🔁 Deactivated expired mute: {} for {}", mute.getId(), mute.getPlayerName());
                anyExpired = true;

                cacheManager.invalidateMuteCache(mute.getPlayerUuid());

                try {
                    PunishmentExpireEvent event = new PunishmentExpireEvent(
                            new NewPunishmentAdapter(mute),
                            EventOrigin.INTERNAL,
                            Instant.now(),
                            false
                    );
                    Bukkit.getPluginManager().callEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to call new PunishmentExpireEvent: {}", e.getMessage());
                }

                if (proxySyncManager != null) {
                    proxySyncManager.sendPunishmentExpire(mute);
                }

                Player p = Bukkit.getPlayer(mute.getPlayerUuid());
                if (p != null && p.isOnline()) {
                    MessageUtil.send(p, "expire_mute", "id", mute.getId());
                }
            }
        }

        if (anyExpired) {
            log.info("✅ Проверка истекших мутов завершена. Некоторые муты были деактивированы.");
        } else {
            log.info("✅ Истекших мутов не обнаружено.");
        }
    }

    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С МУТАМИ =====
    public void scheduleMuteExpiry(Punishment mute) {
        if (mute.getEndTime() == null) return;
        long delay = mute.getEndTime() - System.currentTimeMillis();
        if (delay <= 0) {
            mute.setActive(false);
            database.updatePunishment(mute);
            cacheManager.invalidateMuteCache(mute.getPlayerUuid());

            Player p = Bukkit.getPlayer(mute.getPlayerUuid());
            if (p != null && p.isOnline()) {
                MessageUtil.send(p, "expire_mute", "id", mute.getId());
            }
            try {
                PunishmentExpireEvent event = new PunishmentExpireEvent(
                        new NewPunishmentAdapter(mute),
                        EventOrigin.INTERNAL,
                        Instant.now(),
                        false
                );
                Bukkit.getPluginManager().callEvent(event);
            } catch (Exception e) {
                log.warn("Failed to call new PunishmentExpireEvent: {}", e.getMessage());
            }
            if (proxySyncManager != null) {
                proxySyncManager.sendPunishmentExpire(mute);
            }
            return;
        }
        BukkitTask task = getServer().getScheduler().runTaskLater(this, () -> {
            mute.setActive(false);
            database.updatePunishment(mute);
            cacheManager.invalidateMuteCache(mute.getPlayerUuid());

            Player p = Bukkit.getPlayer(mute.getPlayerUuid());
            if (p != null && p.isOnline()) {
                MessageUtil.send(p, "expire_mute", "id", mute.getId());
            }
            muteExpiryTasks.remove(mute.getId());
            try {
                PunishmentExpireEvent event = new PunishmentExpireEvent(
                        new NewPunishmentAdapter(mute),
                        EventOrigin.INTERNAL,
                        Instant.now(),
                        false
                );
                Bukkit.getPluginManager().callEvent(event);
            } catch (Exception e) {
                log.warn("Failed to call new PunishmentExpireEvent: {}", e.getMessage());
            }
            if (proxySyncManager != null) {
                proxySyncManager.sendPunishmentExpire(mute);
            }
        }, delay / 50);
        muteExpiryTasks.put(mute.getId(), task);
    }

    public void cancelMuteExpiry(String punishmentId) {
        BukkitTask task = muteExpiryTasks.remove(punishmentId);
        if (task != null) task.cancel();
    }

    private void rescheduleAllMutes() {
        for (BukkitTask task : muteExpiryTasks.values()) {
            task.cancel();
        }
        muteExpiryTasks.clear();
        List<Punishment> activeMutes = database.getAllActivePunishmentsByType(PunishmentType.MUTE);
        for (Punishment mute : activeMutes) {
            if (mute.getEndTime() != null && mute.isActive() && mute.getEndTime() > System.currentTimeMillis()) {
                scheduleMuteExpiry(mute);
            }
        }
    }

    // ===== ГЕТТЕРЫ =====
    public String getServerName() {
        return getConfig().getString("server_name", "unknown");
    }

    public String getMode() {
        return getConfig().getString("mode", "single");
    }

    // Новый геттер API
    public DBansAPI getApi() {
        return api;
    }

    // ===== ПЕРЕЗАГРУЗКА КОНФИГУРАЦИИ JAIL =====
    public void reloadJailConfig() {
        if (jailConfigFile == null) {
            jailConfigFile = new File(getDataFolder(), "jail.yml");
        }
        jailConfig = YamlConfiguration.loadConfiguration(jailConfigFile);
        if (jailManager != null) {
            jailManager.reload();
        }
        log.info("jail.yml reloaded");
    }
}
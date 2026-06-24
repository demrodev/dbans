package me.demro.dBans;

import me.demro.dBans.api.DBansAPIImpl;
import me.demro.dBans.api.adapter.PunishmentAdapter;
import me.demro.dBans.command.*;
import me.demro.dBans.command.tabcomplete.UniversalTabCompleter;
import me.demro.dBans.database.*;
import me.demro.dBans.listener.*;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.placeholder.DBansExpansion;
import me.demro.dBans.sync.Constants;
import me.demro.dBans.sync.ProxySyncManager;
import me.demro.dBans.util.*;
import me.demro.dBans.util.geo.GeoIpManager;
import me.demro.dlibs.api.DBansAPI;
import me.demro.dlibs.api.DBansProvider;
import me.demro.dlibs.api.EventManager;
import me.demro.dlibs.api.events.PunishmentExpireEvent;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DBans extends JavaPlugin implements DBansProvider.DBansAPIProvider {
    private DatabaseManager database;
    private LuckPermsHook luckPermsHook;
    private PresetManager presetManager;
    private SelfPunishChecker selfPunishChecker;
    private ProxySyncManager proxySyncManager;
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
    private DBansAPI api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        MessageUtil.init(this);

        String dbType = getConfig().getString("database.type", "h2");
        String mode = getMode();
        if (!"single".equalsIgnoreCase(mode) && "h2".equalsIgnoreCase(dbType)) {
            getLogger().severe("Mode '" + mode + "' requires MySQL! H2 is not supported. Disabling plugin.");
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
            getLogger().info("Database connected: " + dbType);
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        cacheManager = new CacheManager(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DBansExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        jailConfigFile = new File(getDataFolder(), "jail.yml");
        if (!jailConfigFile.exists()) saveResource("jail.yml", false);
        jailConfig = YamlConfiguration.loadConfiguration(jailConfigFile);
        getLogger().info("jail.yml loaded.");

        jailManager = new JailManager(this);
        luckPermsHook = new LuckPermsHook(this);
        presetManager = new PresetManager(this);
        selfPunishChecker = new SelfPunishChecker(this);
        geoIpManager = new GeoIpManager(this);
        limitsManager = new LimitsManager(this);
        altAccountManager = new AltAccountManager(this);
        warnManager = new WarnManager(this);


        // Инициализация API
        this.api = new DBansAPIImpl(this);
        getLogger().info("DBans API initialized and ready");

        if (mode.equalsIgnoreCase("sync") || mode.equalsIgnoreCase("sync_static")) {
            proxySyncManager = new ProxySyncManager(this);
            getServer().getMessenger().registerOutgoingPluginChannel(this, Constants.CHANNEL_NAME);
            getServer().getMessenger().registerIncomingPluginChannel(this, Constants.CHANNEL_NAME, proxySyncManager);
            getLogger().info("✅ Proxy sync manager registered.");
        } else {
            getLogger().info("ℹ️ Proxy sync disabled (mode=" + mode + ")");
        }

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

        rescheduleAllMutes();

        getLogger().info("DBans v" + getPluginMeta().getVersion() + " by demrodev enabled");

        int pluginId = 32027;
        Metrics metrics = new Metrics(this, pluginId);
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : muteExpiryTasks.values()) task.cancel();
        muteExpiryTasks.clear();
        if (database != null) database.close();
        getLogger().info("DBans disabled");
        if (proxySyncManager != null) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, Constants.CHANNEL_NAME);
            getServer().getMessenger().unregisterIncomingPluginChannel(this, Constants.CHANNEL_NAME, proxySyncManager);
            proxySyncManager = null;
        }
    }

    // Геттеры
    public DatabaseManager getDatabase() { return database; }
    public CacheManager getCacheManager() { return cacheManager; }
    public LuckPermsHook getLuckPermsHook() { return luckPermsHook; }
    public PresetManager getPresetManager() { return presetManager; }
    public SelfPunishChecker getSelfPunishChecker() { return selfPunishChecker; }
    public ProxySyncManager getProxySyncManager() { return proxySyncManager; }
    public GeoIpManager getGeoIpManager() { return geoIpManager; }
    public LimitsManager getLimitsManager() { return limitsManager; }
    public String getServerName() { return getConfig().getString("server_name", "unknown"); }
    public String getMode() { return getConfig().getString("mode", "single"); }
    public JailManager getJailManager() { return jailManager; }
    public YamlConfiguration getJailConfig() { return jailConfig; }
    public AltAccountManager getAltAccountManager() { return altAccountManager; }
    public WarnManager getWarnManager() { return warnManager; }
    public PunishmentSyncManager getPunishmentSyncManager() { return punishmentSyncManager; }

    // Реализация метода интерфейса DBansProvider.DBansAPIProvider
    @Override
    public DBansAPI getAPI() {
        return api;
    }

    public EventManager getEventManager() {
        return api != null ? api.getEventManager() : null;
    }

    public void scheduleMuteExpiry(Punishment mute) {
        if (mute.getEndTime() == null) return;
        long delay = mute.getEndTime() - System.currentTimeMillis();
        if (delay <= 0) {
            mute.setActive(false);
            database.updatePunishment(mute);
            if (proxySyncManager != null) {
                proxySyncManager.sendPunishmentExpire(mute);
            }
            Player p = Bukkit.getPlayer(mute.getPlayerUuid());
            if (p != null && p.isOnline()) {
                String rawMsg = MessageUtil.getRawMessage("tempmute_expired");
                if (rawMsg != null) p.sendMessage(MessageUtil.deserialize(rawMsg));
            }
            try {
                EventManager em = getEventManager();
                if (em != null) {
                    PunishmentExpireEvent event = new PunishmentExpireEvent(
                            new PunishmentAdapter(mute)
                    );
                    em.callEvent(event);
                }
            } catch (Exception e) {
                getLogger().warning("Failed to call PunishmentExpireEvent: " + e.getMessage());
            }
            return;
        }
        BukkitTask task = getServer().getScheduler().runTaskLater(this, () -> {
            mute.setActive(false);
            database.updatePunishment(mute);
            Player p = Bukkit.getPlayer(mute.getPlayerUuid());
            if (p != null && p.isOnline()) {
                String rawMsg = MessageUtil.getRawMessage("tempmute_expired");
                if (rawMsg != null) p.sendMessage(MessageUtil.deserialize(rawMsg));
            }
            muteExpiryTasks.remove(mute.getId());
            try {
                EventManager em = getEventManager();
                if (em != null) {
                    PunishmentExpireEvent event = new PunishmentExpireEvent(
                            new PunishmentAdapter(mute)
                    );
                    em.callEvent(event);
                }
            } catch (Exception e) {
                getLogger().warning("Failed to call PunishmentExpireEvent: " + e.getMessage());
            }
        }, delay / 50);
        muteExpiryTasks.put(mute.getId(), task);
    }

    public void cancelMuteExpiry(String punishmentId) {
        BukkitTask task = muteExpiryTasks.remove(punishmentId);
        if (task != null) task.cancel();
    }

    private void rescheduleAllMutes() {
        for (BukkitTask task : muteExpiryTasks.values()) task.cancel();
        muteExpiryTasks.clear();
        List<Punishment> activeMutes = database.getAllActivePunishmentsByType(PunishmentType.MUTE);
        for (Punishment mute : activeMutes) {
            if (mute.getEndTime() != null && mute.isActive()) {
                scheduleMuteExpiry(mute);
            }
        }
    }

    public void reloadJailConfig() {
        if (jailConfigFile == null) jailConfigFile = new File(getDataFolder(), "jail.yml");
        jailConfig = YamlConfiguration.loadConfiguration(jailConfigFile);
        if (jailManager != null) jailManager.reload();
        getLogger().info("jail.yml reloaded");
    }
}
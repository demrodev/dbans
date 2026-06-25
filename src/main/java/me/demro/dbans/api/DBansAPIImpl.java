package me.demro.dbans.api;

import me.demro.dbans.DBans;
import me.demro.dlibs.api.DBansAPI;
import me.demro.dlibs.api.EventManager;
import me.demro.dlibs.api.PlayerManager;
import me.demro.dlibs.api.PunishmentManager;
import org.bukkit.plugin.Plugin;

public class DBansAPIImpl implements DBansAPI {
    private final DBans plugin;
    private final PunishmentManager punishmentManager;
    private final PlayerManager playerManager;
    private final EventManager eventManager;

    public DBansAPIImpl(DBans plugin) {
        this.plugin = plugin;
        this.punishmentManager = new PunishmentManagerImpl(plugin);
        this.playerManager = new PlayerManagerImpl(plugin);
        this.eventManager = new EventManagerImpl(plugin);
    }

    @Override
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    @Override
    public PlayerManager getPlayerManager() { return playerManager; }
    @Override
    public EventManager getEventManager() { return eventManager; }
    @Override
    public boolean isEnabled() { return plugin.isEnabled(); }
    @Override
    public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override
    public Plugin getPlugin() { return plugin; }
}
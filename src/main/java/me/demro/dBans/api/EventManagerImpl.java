package me.demro.dBans.api;

import me.demro.dBans.DBans;
import me.demro.dlibs.api.EventManager;
import me.demro.dlibs.api.events.PunishmentEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public class EventManagerImpl implements EventManager {
    private final DBans plugin;

    public EventManagerImpl(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void unregisterListener(Listener listener) {
        org.bukkit.event.HandlerList.unregisterAll(listener);
    }

    @Override
    public void callEvent(PunishmentEvent event) {
        Bukkit.getPluginManager().callEvent(event);
    }
}
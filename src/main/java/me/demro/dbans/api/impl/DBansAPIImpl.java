package me.demro.dbans.api.impl;

import lombok.Getter;
import me.demro.dbans.DBans;
import me.demro.dlibs.dbans.api.DBansAPI;
import me.demro.dlibs.dbans.api.alt.AltService;
import me.demro.dlibs.dbans.api.permission.PermissionService;
import me.demro.dlibs.dbans.api.player.PlayerService;
import me.demro.dlibs.dbans.api.punishment.PunishmentService;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Main implementation of the new dBans API.
 */
@Getter
public class DBansAPIImpl implements DBansAPI {

    private final DBans plugin;
    private final PunishmentService punishments;
    private final PlayerService players;
    private final PermissionService permissions;
    private final AltService alts;

    public DBansAPIImpl(DBans plugin) {
        this.plugin = plugin;
        this.punishments = new PunishmentServiceImpl(plugin);
        this.players = new PlayerServiceImpl(plugin);
        this.permissions = new PermissionServiceImpl(plugin);
        this.alts = new AltServiceImpl(plugin);
    }

    @Override
    public @NotNull PunishmentService punishments() {
        return punishments;
    }

    @Override
    public @NotNull PlayerService players() {
        return players;
    }

    @Override
    public @NotNull PermissionService permissions() {
        return permissions;
    }

    @Override
    public @NotNull AltService alts() {
        return alts;
    }

    @Override
    public boolean isAvailable() {
        return plugin.isEnabled();
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public @NotNull String version() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public @NotNull Plugin plugin() {
        return plugin;
    }
}
package me.demro.dBans.api;

import me.demro.dBans.DBans;
import me.demro.dBans.model.PlayerInfo;
import me.demro.dlibs.api.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerManagerImpl implements PlayerManager {
    private final DBans plugin;

    public PlayerManagerImpl(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPlayerIp(UUID playerUuid) {
        PlayerInfo info = plugin.getDatabase().getPlayer(playerUuid);
        return info != null ? info.getIp() : null;
    }

    @Override
    public boolean hasImmunity(UUID playerUuid, me.demro.dlibs.api.PunishmentType type) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerUuid);
        // Преобразуем API-тип в строку для внутреннего метода
        return plugin.getLimitsManager().isImmune(target, type.name().toLowerCase());
    }

    @Override
    public int getPriority(UUID playerUuid) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerUuid);
        return plugin.getLimitsManager().getPriority(target);
    }

    @Override
    public boolean canPunish(UUID issuerUuid, UUID targetUuid) {
        if (issuerUuid.equals(UUID.nameUUIDFromBytes("CONSOLE".getBytes()))) {
            return true;
        }
        Player issuer = Bukkit.getPlayer(issuerUuid);
        if (issuer == null) return false;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        return plugin.getLimitsManager().canPunish(issuer, target);
    }
}
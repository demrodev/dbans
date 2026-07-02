package me.demro.dbans.util;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AltAccountManager {
    private final DBans plugin;

    public AltAccountManager(DBans plugin) {
        this.plugin = plugin;
        log.debug("AltAccountManager initialized");
    }

    /**
     * Находит альт-аккаунты для указанного игрока по общему IP.
     * @param playerName имя игрока
     * @return список имён альт-аккаунтов (без самого игрока)
     */
    public List<String> findAltAccounts(String playerName) {
        List<String> alts = new ArrayList<>();
        try {
            // Получаем IP игрока из таблицы players
            String ip = plugin.getDatabase().getPlayerIpByName(playerName);
            if (ip == null) {
                log.debug("No IP found for player {}", playerName);
                return alts;
            }
            // Получаем всех игроков с этим IP, кроме самого playerName
            alts = plugin.getDatabase().getPlayerNamesByIp(playerName);
            log.debug("Found {} alt accounts for {}: {}", alts.size(), playerName, alts);
        } catch (Exception e) {
            log.error("Failed to find alt accounts for {}: {}", playerName, e.getMessage(), e);
        }
        return alts;
    }
}
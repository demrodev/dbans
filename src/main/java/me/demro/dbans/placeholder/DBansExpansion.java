package me.demro.dbans.placeholder;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class DBansExpansion extends PlaceholderExpansion {
    private final DBans plugin;

    public DBansExpansion(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dbans";
    }

    @Override
    public @NotNull String getAuthor() {
        return "demrodev";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (identifier == null) return null;

        // ========== СТАТИСТИКА ПОЛУЧЕННЫХ НАКАЗАНИЙ (target) ==========
        // %dbans_stat_total%
        if (identifier.equals("stat_total")) {
            return String.valueOf(plugin.getDatabase().getTotalPunishmentsCount());
        }

        // %dbans_stat_total_<type>%
        if (identifier.startsWith("stat_total_")) {
            String type = identifier.substring("stat_total_".length()).toUpperCase();
            return String.valueOf(plugin.getDatabase().getPunishmentsCountByType(type));
        }

        // %dbans_stat_<player>_total%
        if (identifier.startsWith("stat_") && identifier.endsWith("_total")) {
            String playerName = identifier.substring(5, identifier.length() - 6);
            return String.valueOf(plugin.getDatabase().getTotalPunishmentsCountByPlayer(playerName));
        }

        // %dbans_stat_<player>_<type>%
        if (identifier.startsWith("stat_")) {
            String rest = identifier.substring(5);
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore == -1) return null;
            String playerName = rest.substring(0, lastUnderscore);
            String type = rest.substring(lastUnderscore + 1).toUpperCase();
            return String.valueOf(plugin.getDatabase().getPunishmentsCountByTypeAndPlayer(type, playerName));
        }

        // %dbans_player_total% – общее количество наказаний текущего игрока
        if (identifier.equals("player_total")) {
            if (player == null) return null;
            return String.valueOf(plugin.getDatabase().getTotalPunishmentsCountByPlayer(player.getName()));
        }

        // %dbans_player_<type>% – количество наказаний текущего игрока по типу
        if (identifier.startsWith("player_")) {
            if (player == null) return null;
            String type = identifier.substring(7).toUpperCase();
            return String.valueOf(plugin.getDatabase().getPunishmentsCountByTypeAndPlayer(type, player.getName()));
        }

        // ========== СТАТИСТИКА ВЫДАННЫХ НАКАЗАНИЙ (issuer) ==========

        // %dbans_gives_total_player% – все наказания, выданные текущим игроком
        if (identifier.equals("gives_total_player")) {
            if (player == null) return null;
            return String.valueOf(plugin.getDatabase().getPunishmentsIssuedByPlayer(player.getName()));
        }

        // %dbans_gives_<playerName>_total% – все наказания, выданные указанным игроком
        if (identifier.startsWith("gives_") && identifier.endsWith("_total")) {
            String playerName = identifier.substring(6, identifier.length() - 6);
            return String.valueOf(plugin.getDatabase().getPunishmentsIssuedByPlayer(playerName));
        }

        // %dbans_gives_<type>_player% – наказания определённого типа, выданные текущим игроком
        if (identifier.startsWith("gives_") && identifier.endsWith("_player")) {
            if (player == null) return null;
            String type = identifier.substring(6, identifier.length() - 7).toUpperCase();
            return String.valueOf(plugin.getDatabase().getPunishmentsIssuedByPlayerAndType(player.getName(), type));
        }

        // %dbans_gives_<playerName>_<type>% – наказания определённого типа, выданные указанным игроком
        if (identifier.startsWith("gives_")) {
            String rest = identifier.substring(6);
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore == -1) return null;
            String playerName = rest.substring(0, lastUnderscore);
            String type = rest.substring(lastUnderscore + 1).toUpperCase();
            return String.valueOf(plugin.getDatabase().getPunishmentsIssuedByPlayerAndType(playerName, type));
        }

        return null;
    }
}
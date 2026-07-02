package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

@Slf4j
public class BantwaccsCommand implements CommandExecutor {
    private final DBans plugin;

    public BantwaccsCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.bantwaccs")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "usage_bantwaccs");
            return true;
        }
        String targetName = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        OfflinePlayer target = plugin.getPlayerCache().getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", targetName);
            return true;
        }
        List<String> alts = plugin.getAltAccountManager().findAltAccounts(target.getName());
        if (alts.isEmpty()) {
            MessageUtil.send(sender, "twaccs_no_alts", "target", target.getName());
            return true;
        }
        StringBuilder bannedList = new StringBuilder();
        UUID issuerUuid = (sender instanceof Player) ? ((Player) sender).getUniqueId() : UUID.nameUUIDFromBytes("CONSOLE".getBytes());
        for (String alt : alts) {
            OfflinePlayer altPlayer = plugin.getPlayerCache().getOfflinePlayer(alt);
            Punishment ban = Punishment.builder()
                    .playerUuid(altPlayer.getUniqueId())
                    .playerName(alt)
                    .issuerUuid(issuerUuid)
                    .issuerName(sender.getName())
                    .type(PunishmentType.BAN)
                    .reason(reason)
                    .startTime(System.currentTimeMillis())
                    .endTime(null)
                    .active(true)
                    .serverName(plugin.getServerName())
                    .build();
            plugin.getDatabase().savePunishment(ban);
            if (plugin.getProxySyncManager() != null) {
                plugin.getProxySyncManager().sendPunishmentCreate(ban);
                log.info("📤 [Sync] Sent punishment_create for {}", ban.getId());
            }
            bannedList.append(alt).append(" (#").append(ban.getId()).append("), ");
            Player online = altPlayer.getPlayer();
            if (online != null) {
                String kickMsg = MessageUtil.getRawMessage("ban_player");
                if (kickMsg == null) kickMsg = "&c✖ Вы были забанены навсегда.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
                kickMsg = kickMsg.replace("%reason%", reason)
                        .replace("%sender%", sender.getName())
                        .replace("%server%", plugin.getServerName())
                        .replace("%id%", ban.getId());
                online.kick(MessageUtil.deserializeForKick(kickMsg));
            }
        }
        String banned = bannedList.length() > 0 ? bannedList.substring(0, bannedList.length() - 2) : "";
        MessageUtil.broadcast("dbans.notify.ban", "bantwaccs_broadcast",
                "sender", sender.getName(),
                "target", target.getName(),
                "alts", banned,
                "reason", reason);
        return true;
    }
}
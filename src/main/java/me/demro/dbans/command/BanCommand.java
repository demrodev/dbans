package me.demro.dbans.command;

import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BanCommand extends BasePunishCommand {

    public BanCommand(me.demro.dbans.DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.BAN;
    }

    @Override
    protected String getPermission() {
        return "dbans.ban";
    }

    @Override
    protected boolean isPermanent() {
        return true;
    }

    @Override
    protected void executePunishment(CommandSender sender, OfflinePlayer target, String reason, String finalServer, boolean silent, Long duration) {
        Punishment ban = new Punishment(target.getUniqueId(), target.getName(),
                sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID,
                sender.getName(), PunishmentType.BAN, reason, System.currentTimeMillis(), null, finalServer);
        plugin.getDatabase().savePunishment(ban);
        // ======== ВСТАВКА ========
        if (plugin.getProxySyncManager() != null) {
            plugin.getProxySyncManager().sendPunishmentCreate(ban);
            plugin.getLogger().info("📤 [Sync] Sent punishment_create for " + ban.getId());
        }
        // ==========================

        Player online = target.getPlayer();
        if (online != null && finalServer.equals(plugin.getServerName())) {
            String kickMsg = MessageUtil.getRawMessage("ban_player");
            if (kickMsg == null) kickMsg = "&c✖ Вы были забанены навсегда.\nПричина: %reason%\nАдминистратор: %sender%\nСервер: %server%\nID: #%id%";
            kickMsg = kickMsg.replace("%reason%", reason)
                    .replace("%sender%", sender.getName())
                    .replace("%server%", finalServer)
                    .replace("%id%", ban.getId());
            online.kick(MessageUtil.deserializeForKick(kickMsg));
        }

        String permission = silent ? null : "dbans.notify.ban";
        MessageUtil.broadcast(permission, "ban_broadcast",
                "sender", sender.getName(), "target", target.getName(),
                "reason", reason, "server", finalServer, "id", ban.getId());
    }
}
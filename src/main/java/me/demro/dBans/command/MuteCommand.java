package me.demro.dBans.command;

import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.util.MessageUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MuteCommand extends BasePunishCommand {

    public MuteCommand(me.demro.dBans.DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.MUTE;
    }

    @Override
    protected String getPermission() {
        return "dbans.mute";
    }

    @Override
    protected boolean isPermanent() {
        return true;
    }

    @Override
    protected void executePunishment(CommandSender sender, OfflinePlayer target, String reason, String finalServer, boolean silent, Long duration) {
        Punishment mute = new Punishment(target.getUniqueId(), target.getName(),
                sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID,
                sender.getName(), PunishmentType.MUTE, reason, System.currentTimeMillis(), null, finalServer);
        plugin.getDatabase().savePunishment(mute);
        // ======== ВСТАВКА ========
        if (plugin.getProxySyncManager() != null) {
            plugin.getProxySyncManager().sendPunishmentCreate(mute);
            plugin.getLogger().info("📤 [Sync] Sent punishment_create for " + mute.getId());
        }
        // ==========================

        Player online = target.getPlayer();
        if (online != null && finalServer.equals(plugin.getServerName())) {
            MessageUtil.send(online, "mute_player",
                    "sender", sender.getName(), "reason", reason,
                    "server", finalServer, "id", mute.getId());
        }

        String permission = silent ? null : "dbans.notify.mute";
        MessageUtil.broadcast(permission, "mute_broadcast",
                "sender", sender.getName(), "target", target.getName(),
                "reason", reason, "server", finalServer, "id", mute.getId());
    }
}
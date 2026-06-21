package me.demro.dBans.command;

import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.util.MessageUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class KickCommand extends BasePunishCommand {

    public KickCommand(me.demro.dBans.DBans plugin) {
        super(plugin);
    }

    @Override
    protected PunishmentType getType() {
        return PunishmentType.KICK;
    }

    @Override
    protected String getPermission() {
        return "dbans.kick";
    }

    @Override
    protected boolean isPermanent() {
        return true;
    }

    @Override
    protected boolean hasActivePunishment(OfflinePlayer target, String mode) {
        return false;
    }

    @Override
    protected void executePunishment(CommandSender sender, OfflinePlayer target, String reason, String finalServer, boolean silent, Long duration) {
        Punishment kick = new Punishment(target.getUniqueId(), target.getName(),
                sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_UUID,
                sender.getName(), PunishmentType.KICK, reason, System.currentTimeMillis(), null, finalServer);
        plugin.getDatabase().savePunishment(kick);

        Player online = target.getPlayer();
        if (online != null && finalServer.equals(plugin.getServerName())) {
            String kickMsg = MessageUtil.getRawMessage("kick_player");
            if (kickMsg == null) kickMsg = "&c✖ Вас кикнули.\nПричина: %reason%\nАдминистратор: %sender%";
            kickMsg = kickMsg.replace("%reason%", reason).replace("%sender%", sender.getName());
            online.kick(MessageUtil.deserializeForKick(kickMsg));
        }

        String permission = silent ? null : "dbans.notify.kick";
        MessageUtil.broadcast(permission, "kick_broadcast",
                "sender", sender.getName(), "target", target.getName(),
                "reason", reason, "server", finalServer, "id", kick.getId());
    }
}
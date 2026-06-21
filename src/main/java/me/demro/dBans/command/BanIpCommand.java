package me.demro.dBans.command;

import me.demro.dBans.DBans;
import me.demro.dBans.model.Punishment;
import me.demro.dBans.model.PunishmentType;
import me.demro.dBans.util.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.regex.Pattern;

public class BanIpCommand implements CommandExecutor {
    private final DBans plugin;
    private static final Pattern IP_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IP_MASK_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\*$");

    public BanIpCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dbans.banip")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }

        boolean silent = false;
        String targetServer = null;
        String[] cleaned = new String[args.length];
        int cleanedIdx = 0;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("-s")) {
                silent = true;
            } else if (arg.toLowerCase().startsWith("server:")) {
                targetServer = arg.substring(7);
                if (targetServer.isEmpty()) targetServer = null;
            } else {
                cleaned[cleanedIdx++] = arg;
            }
        }
        String[] cleanArgs = java.util.Arrays.copyOf(cleaned, cleanedIdx);

        if (targetServer != null) {
            boolean canUseServer = sender.hasPermission("dbans.server.bypass") ||
                    sender.hasPermission("dbans.server.banip");
            if (!canUseServer) {
                MessageUtil.send(sender, "no_server_permission", "command", "banip");
                return true;
            }
        }

        if (cleanArgs.length < 1) {
            MessageUtil.send(sender, "usage_banip");
            return true;
        }

        String targetInput = cleanArgs[0];
        String reason = cleanArgs.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(cleanArgs, 1, cleanArgs.length)) : "Не указана";
        String ipOrMask;
        OfflinePlayer target = null;
        UUID playerUuid = null;
        String playerName = null;

        Player onlineTarget = Bukkit.getPlayer(targetInput);
        if (onlineTarget != null) {
            target = onlineTarget;
            ipOrMask = onlineTarget.getAddress().getAddress().getHostAddress();
            playerName = onlineTarget.getName();
            playerUuid = onlineTarget.getUniqueId();
            if (!plugin.getLimitsManager().canPunish(sender, target)) return true;
            if (plugin.getLimitsManager().isImmune(target, "banip")) {
                MessageUtil.send(sender, "target_immune_permission", "target", target.getName());
                return true;
            }
            if (plugin.getSelfPunishChecker().isSelfPunish(sender, playerName)) return true;
        } else if (IP_PATTERN.matcher(targetInput).matches() || IP_MASK_PATTERN.matcher(targetInput).matches()) {
            ipOrMask = targetInput;
        } else {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetInput);
            if (!offlineTarget.hasPlayedBefore()) {
                MessageUtil.send(sender, "player_not_found", "target", targetInput);
                return true;
            }
            target = offlineTarget;
            playerName = offlineTarget.getName();
            playerUuid = offlineTarget.getUniqueId();
            ipOrMask = plugin.getDatabase().getIpByPlayerName(playerName);
            if (ipOrMask == null) {
                MessageUtil.send(sender, "ip_not_found_for_player", "target", playerName);
                return true;
            }
            if (!plugin.getLimitsManager().canPunish(sender, target)) return true;
            if (plugin.getLimitsManager().isImmune(target, "banip")) {
                MessageUtil.send(sender, "target_immune_permission", "target", target.getName());
                return true;
            }
            if (plugin.getSelfPunishChecker().isSelfPunish(sender, playerName)) return true;
        }

        if (plugin.getDatabase().isIpBanned(ipOrMask)) {
            MessageUtil.send(sender, "ip_banned_already", "target", playerName != null ? playerName : ipOrMask);
            return true;
        }

        if (sender instanceof Player) {
            Player issuer = (Player) sender;
            if (plugin.getLimitsManager().isOnCooldown(issuer, "banip")) {
                int remaining = plugin.getLimitsManager().getRemainingCooldown(issuer, "banip");
                MessageUtil.send(sender, "command_on_cooldown", "command", "banip", "time", String.valueOf(remaining));
                return true;
            }
        }

        String finalServer = (targetServer != null && !targetServer.isEmpty()) ? targetServer : plugin.getServerName();
        String mode = plugin.getMode();
        if (!mode.equalsIgnoreCase("sync") && !mode.equalsIgnoreCase("sync_static") && targetServer != null) {
            MessageUtil.send(sender, "server_argument_not_supported");
            return true;
        }

        plugin.getDatabase().saveIpBan(ipOrMask, playerUuid, playerName, sender.getName(), reason, System.currentTimeMillis(), null);
        Punishment ipBanPunishment = null;
        if (playerUuid != null) {
            ipBanPunishment = new Punishment(playerUuid, playerName,
                    (sender instanceof Player) ? ((Player) sender).getUniqueId() : UUID.nameUUIDFromBytes("CONSOLE".getBytes()),
                    sender.getName(), PunishmentType.IPBAN, reason, System.currentTimeMillis(), null, finalServer);
            plugin.getDatabase().savePunishment(ipBanPunishment);
        }

        if (sender instanceof Player) {
            plugin.getLimitsManager().setCooldown((Player) sender, "banip");
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            String playerIp = p.getAddress().getAddress().getHostAddress();
            if (matchesIpMask(playerIp, ipOrMask)) {
                String kickMsg = MessageUtil.getRawMessage("banip_player");
                if (kickMsg == null) kickMsg = "&c✖ Ваш IP-адрес заблокирован.\nПричина: %reason%\nАдминистратор: %sender%";
                kickMsg = kickMsg.replace("%reason%", reason).replace("%sender%", sender.getName());
                p.kickPlayer(MessageUtil.serializeForKick(kickMsg));
            }
        }

        boolean canSilent = true;
        if (sender instanceof Player) {
            canSilent = plugin.getLimitsManager().canUseSilent((Player) sender, "banip");
        }
        if (silent && !canSilent) {
            MessageUtil.send(sender, "silent_not_allowed", "command", "banip");
            silent = false;
        }

        String id = ipBanPunishment != null ? ipBanPunishment.getId() : "N/A";
        if (!silent) {
            MessageUtil.broadcast("dbans.notify.ipban", "banip_broadcast",
                    "sender", sender.getName(),
                    "target", playerName != null ? playerName : ipOrMask,
                    "reason", reason,
                    "server", finalServer,
                    "id", id);
        } else {
            MessageUtil.send(sender, "banip_broadcast",
                    "sender", sender.getName(),
                    "target", playerName != null ? playerName : ipOrMask,
                    "reason", reason,
                    "server", finalServer,
                    "id", id);
        }
        return true;
    }

    private boolean matchesIpMask(String ip, String ipOrMask) {
        if (ipOrMask.contains("*")) {
            String[] maskParts = ipOrMask.split("\\.");
            String[] ipParts = ip.split("\\.");
            for (int i = 0; i < 4; i++) {
                if (!maskParts[i].equals("*") && !maskParts[i].equals(ipParts[i])) {
                    return false;
                }
            }
            return true;
        } else {
            return ip.equals(ipOrMask);
        }
    }
}
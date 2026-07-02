package me.demro.dbans.command;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import me.demro.dlibs.dbans.api.exception.PlayerNotFoundException;
import me.demro.dlibs.dbans.api.player.PlayerIdentity;
import me.demro.dlibs.dbans.api.punishment.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
public class BanIpCommand implements CommandExecutor {

    private static final Pattern IP_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IP_MASK_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\*$");
    private final DBans plugin;

    public BanIpCommand(DBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!sender.hasPermission("dbans.banip")) {
            MessageUtil.send(sender, "no_permission");
            return true;
        }

        boolean silent = false;
        String targetServer = null;
        List<String> filteredArgs = new ArrayList<>();

        for (String arg : args) {
            if (arg.equalsIgnoreCase("-s")) {
                silent = true;
            } else if (arg.toLowerCase().startsWith("server:")) {
                targetServer = arg.substring(7);
                if (targetServer.isEmpty()) targetServer = null;
            } else {
                filteredArgs.add(arg);
            }
        }

        if (filteredArgs.isEmpty()) {
            MessageUtil.send(sender, "usage_banip");
            return true;
        }

        if (targetServer != null) {
            boolean canUseServer = sender.hasPermission("dbans.server.bypass") ||
                                   sender.hasPermission("dbans.server.banip");
            if (!canUseServer) {
                MessageUtil.send(sender, "no_server_permission", "command", "banip");
                return true;
            }
        }

        String targetInput = filteredArgs.get(0);
        String reason = filteredArgs.size() >= 2 ? String.join(" ", filteredArgs.subList(1, filteredArgs.size())) : "Не указана";
        String ipOrMask;
        OfflinePlayer target = null;
        UUID playerUuid = null;
        String playerName;

        Player onlineTarget = Bukkit.getPlayer(targetInput);
        if (onlineTarget != null) {
            target = onlineTarget;
            ipOrMask = onlineTarget.getAddress().getAddress().getHostAddress();
            playerName = onlineTarget.getName();
            playerUuid = onlineTarget.getUniqueId();
            // Проверка через новый API
            if (plugin.getApi().permissions().canPunish(
                    sender instanceof Player ? ((Player) sender).getUniqueId() : UUID.nameUUIDFromBytes("CONSOLE".getBytes()),
                    playerUuid
            ).join() == false) {
                MessageUtil.send(sender, "cannot_punish_higher_priority", "target", playerName);
                return true;
            }
            if (plugin.getApi().permissions().hasImmunity(playerUuid, PunishmentType.IP_BAN).join()) {
                MessageUtil.send(sender, "target_immune_permission", "target", playerName);
                return true;
            }
            if (plugin.getSelfPunishChecker().isSelfPunish(sender, playerName)) return true;
        } else if (IP_PATTERN.matcher(targetInput).matches() || IP_MASK_PATTERN.matcher(targetInput).matches()) {
            playerName = null;
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
            if (plugin.getApi().permissions().canPunish(
                    sender instanceof Player ? ((Player) sender).getUniqueId() : UUID.nameUUIDFromBytes("CONSOLE".getBytes()),
                    playerUuid
            ).join() == false) {
                MessageUtil.send(sender, "cannot_punish_higher_priority", "target", playerName);
                return true;
            }
            if (plugin.getApi().permissions().hasImmunity(playerUuid, PunishmentType.IP_BAN).join()) {
                MessageUtil.send(sender, "target_immune_permission", "target", playerName);
                return true;
            }
            if (plugin.getSelfPunishChecker().isSelfPunish(sender, playerName)) return true;
        }

        if (plugin.getDatabase().isIpBanned(ipOrMask)) {
            MessageUtil.send(sender, "ip_banned_already", "target", playerName != null ? playerName : ipOrMask);
            return true;
        }

        if (sender instanceof Player issuer) {
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

        // Создаём IP-бан через новый API
        PunishmentCreateRequest request = PunishmentCreateRequest.builder()
                                                                 .target(PlayerIdentity.of(playerUuid != null ? playerUuid : UUID.randomUUID(), playerName != null ? playerName : ipOrMask))
                                                                 .type(PunishmentType.IP_BAN)
                                                                 .reason(PunishmentReason.of(reason))
                                                                 .duration(PunishmentDuration.permanent())
                                                                 .issuer(sender instanceof Player
                                                                                 ? PunishmentIssuer.player(((Player) sender).getUniqueId(), sender.getName())
                                                                                 : PunishmentIssuer.console())
                                                                 .serverName(finalServer)
                                                                 .options(PunishmentOptions.builder()
                                                                                           .silent(silent)
                                                                                           .broadcast(!silent)
                                                                                           .notifyTarget(true)
                                                                                           .build())
                                                                 .build();

        plugin.getApi().punishments().create(request)
              .whenComplete((result, ex) -> {
                  if (ex != null) {
                      if (ex instanceof PlayerNotFoundException) {
                          MessageUtil.send(sender, "player_not_found", "target", playerName != null ? playerName : ipOrMask);
                      } else {
                          MessageUtil.send(sender, "error_creating_punishment", "error", ex.getMessage());
                          log.error("Error creating IP ban", ex);
                      }
                  } else {
                      if (sender instanceof Player) {
                          plugin.getLimitsManager().setCooldown((Player) sender, "banip");
                      }
                      log.info("IP {} banned by {}", ipOrMask, sender.getName());
                  }
              });

        return true;
    }
}
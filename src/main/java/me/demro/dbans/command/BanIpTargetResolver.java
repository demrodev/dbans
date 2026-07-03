package me.demro.dbans.command;

import lombok.RequiredArgsConstructor;
import me.demro.dbans.DBans;
import me.demro.dbans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@RequiredArgsConstructor
final class BanIpTargetResolver {

    private final DBans plugin;

    @NotNull Optional<BanIpTarget> resolve(@NotNull CommandSender sender,
                                           @NotNull String input
    ) {
        Player online = Bukkit.getPlayerExact(input);
        Optional<BanIpTarget> target;

        if (online != null) {
            target = resolveOnline(sender, online);
        } else if (isValidIpOrMask(input)) {
            target = Optional.of(BanIpTarget.forAddress(input));
        } else {
            target = resolveOffline(sender, input);
        }
        return target;
    }

    private @NotNull Optional<BanIpTarget> resolveOnline(@NotNull CommandSender sender,
                                                         @NotNull Player player
    ) {
        var address = player.getAddress();
        Optional<BanIpTarget> target;

        if (address == null) {
            MessageUtil.send(sender, "ip_not_found_for_player", "target", player.getName());
            target = Optional.empty();
        } else {
            target = Optional.of(BanIpTarget.forPlayer(
                    address.getAddress().getHostAddress(),
                    player.getName(),
                    player.getUniqueId()
            ));
        }
        return target;
    }

    private @NotNull Optional<BanIpTarget> resolveOffline(@NotNull CommandSender sender,
                                                          @NotNull String input
    ) {
        OfflinePlayer player = plugin.getPlayerCache().getOfflinePlayer(input);
        Optional<BanIpTarget> target;

        if (!player.hasPlayedBefore() && !player.isOnline()) {
            MessageUtil.send(sender, "player_not_found", "target", input);
            target = Optional.empty();
        } else {
            String playerName = Optional.ofNullable(player.getName()).orElse(input);
            String ip = plugin.getDatabase().getIpByPlayerName(playerName);
            if (ip == null) {
                MessageUtil.send(sender, "ip_not_found_for_player", "target", playerName);
                target = Optional.empty();
            } else {
                target = Optional.of(BanIpTarget.forPlayer(
                        ip,
                        playerName,
                        player.getUniqueId()
                ));
            }
        }
        return target;
    }

    private boolean isValidIpOrMask(@NotNull String value) {
        String[] octets = value.split("\\.", -1);
        boolean valid = octets.length == 4;

        for (int index = 0; valid && index < octets.length; index++) {
            String octet = octets[index];
            boolean wildcard = index == octets.length - 1 && octet.equals("*");
            valid = wildcard || isValidOctet(octet);
        }
        return valid;
    }

    private boolean isValidOctet(@NotNull String value) {
        boolean valid = !value.isEmpty()
                        && value.length() <= 3
                        && value.chars().allMatch(character ->
                                                          character >= '0' && character <= '9');

        if (valid) {
            int numericValue = Integer.parseInt(value);
            valid = numericValue <= 255
                    && (value.length() == 1 || value.charAt(0) != '0');
        }
        return valid;
    }
}

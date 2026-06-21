package me.demro.dBans.listener;

import me.demro.dBans.DBans;
import me.demro.dBans.model.JailPunishment;
import me.demro.dBans.util.JailManager;
import me.demro.dBans.util.MessageUtil;
import me.demro.dBans.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryOpenEvent;

public class JailListener implements Listener {
    private final DBans plugin;
    private final JailManager jailManager;

    public JailListener(DBans plugin) {
        this.plugin = plugin;
        this.jailManager = plugin.getJailManager();
    }

    private boolean isInJailWorld(Player player) {
        World w = player.getWorld();
        return w != null && w.getName().equals("jail");
    }

    private boolean isAllowedCommand(String command) {
        String cmd = command.toLowerCase();
        return cmd.startsWith("/jail") || cmd.startsWith("/unjail") || cmd.startsWith("/dbans") || cmd.startsWith("/help");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
            event.setCancelled(true);
            MessageUtil.send(p, "jail_world_block_break");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
            event.setCancelled(true);
            MessageUtil.send(p, "jail_world_block_place");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
            event.setCancelled(true);
            MessageUtil.send(p, "jail_world_interact");
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player p = (Player) event.getPlayer();
            if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
                event.setCancelled(true);
                MessageUtil.send(p, "jail_world_inventory");
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player p = (Player) event.getDamager();
            if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
                event.setCancelled(true);
            }
        }
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
                event.setCancelled(true);
                p.setFoodLevel(20);
                p.setSaturation(10f);
                MessageUtil.send(p, "jail_food_fill");
            }
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && !isAllowedCommand(event.getMessage())) {
            event.setCancelled(true);
            MessageUtil.send(p, "jail_world_command");
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (jailManager.isTeleportAllowed(p)) return;
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass")) {
            if (event.getTo().getWorld() != null && !event.getTo().getWorld().getName().equals("jail")) {
                event.setCancelled(true);
                MessageUtil.send(p, "jail_teleport_blocked");
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        jailManager.handlePlayerJoin(event.getPlayer());
        jailManager.teleportOutOfJailIfNeeded(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (jailManager.isJailed(p)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
                return;
            }
            if (from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch()) {
                event.setTo(new Location(to.getWorld(), to.getX(), to.getY(), to.getZ(), from.getYaw(), from.getPitch()));
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        if (jailManager.isJailed(p)) {
            event.setCancelled(true);
            JailPunishment jail = plugin.getDatabase().getActiveJail(p.getUniqueId());
            if (jail != null) {
                String duration = jail.getEndTime() == null ? "навсегда" : TimeUtil.formatDuration(jail.getEndTime() - System.currentTimeMillis());
                MessageUtil.send(p, "jail_no_chat",
                        "sender", jail.getIssuerName(),
                        "reason", jail.getReason(),
                        "server", jail.getServerName(),
                        "duration", duration);
            } else {
                MessageUtil.send(p, "jail_no_chat_fallback");
            }
        }
    }

    @EventHandler
    public void onJailedCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (jailManager.isJailed(p)) {
            event.setCancelled(true);
            JailPunishment jail = plugin.getDatabase().getActiveJail(p.getUniqueId());
            if (jail != null) {
                String duration = jail.getEndTime() == null ? "навсегда" : TimeUtil.formatDuration(jail.getEndTime() - System.currentTimeMillis());
                MessageUtil.send(p, "jail_no_commands",
                        "sender", jail.getIssuerName(),
                        "reason", jail.getReason(),
                        "server", jail.getServerName(),
                        "duration", duration);
            } else {
                MessageUtil.send(p, "jail_no_commands_fallback");
            }
        }
    }
}
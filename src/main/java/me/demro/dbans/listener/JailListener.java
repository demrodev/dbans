package me.demro.dbans.listener;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.util.JailManager;
import me.demro.dbans.util.MessageUtil;
import me.demro.dbans.util.TimeUtil;
import me.demro.dlibs.dbans.api.punishment.Punishment;
import me.demro.dlibs.dbans.api.punishment.PunishmentType;
import org.bukkit.Bukkit;
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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
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

    private boolean isJailed(Player player) {
        // Проверка через новый API
        CompletableFuture<Boolean> future = plugin.getApi().punishments().hasActive(player.getUniqueId(), PunishmentType.JAIL);
        return future.join();
    }

    private boolean isAllowedCommand(String command) {
        String cmd = command.toLowerCase();
        return cmd.startsWith("/jail") || cmd.startsWith("/unjail") || cmd.startsWith("/dbans") || cmd.startsWith("/help");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
            event.setCancelled(true);
            MessageUtil.send(p, "jail_world_block_break");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
            event.setCancelled(true);
            MessageUtil.send(p, "jail_world_block_place");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
            event.setCancelled(true);
            MessageUtil.send(p, "jail_world_interact");
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p) {
            if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
                event.setCancelled(true);
                MessageUtil.send(p, "jail_world_inventory");
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player p = event.getPlayer();
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
                event.setCancelled(true);
            }
        }
        if (event.getEntity() instanceof Player p) {
            if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p) {
            if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
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
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p) && !isAllowedCommand(event.getMessage())) {
            event.setCancelled(true);
            MessageUtil.send(p, "jail_world_command");
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (jailManager.isTeleportAllowed(p)) return;
        if (isInJailWorld(p) && !p.hasPermission("dbans.jail.bypass") && isJailed(p)) {
            if (event.getTo().getWorld() != null && !event.getTo().getWorld().getName().equals("jail")) {
                event.setCancelled(true);
                MessageUtil.send(p, "jail_teleport_blocked");
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // Проверяем, есть ли активный джейл, и если игрок не в мире jail – телепортируем
        CompletableFuture<Boolean> hasJailFuture = plugin.getApi().punishments().hasActive(p.getUniqueId(), PunishmentType.JAIL);
        if (hasJailFuture.join()) {
            // Получаем активный джейл для деталей
            CompletableFuture<List<Punishment>> jailListFuture = plugin.getApi().punishments().findActiveByTarget(p.getUniqueId());
            List<Punishment> jails = jailListFuture.join();
            if (!jails.isEmpty()) {
                Punishment jail = jails.get(0);
                // Если игрок не в мире jail – телепортируем (используем JailManager)
                if (!p.getWorld().getName().equals("jail")) {
                    // Получаем внутренний JailPunishment для location
                    JailPunishment internalJail = plugin.getDatabase().getActiveJail(p.getUniqueId());
                    if (internalJail != null && internalJail.getJailLocation() != null) {
                        p.teleport(internalJail.getJailLocation());
                    } else {
                        // fallback – телепорт в спавн jail мира
                        World w = Bukkit.getWorld("jail");
                        if (w != null) p.teleport(w.getSpawnLocation());
                    }
                    // Применяем эффекты джейла через JailManager (он уже обрабатывает это при handlePlayerJoin)
                }
                // Вызываем handlePlayerJoin для применения эффектов и NPC
                jailManager.handlePlayerJoin(p);
            }
        } else {
            // Если игрок в jail мире, но не jailed – телепортируем наружу
            jailManager.teleportOutOfJailIfNeeded(p);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (isJailed(p)) {
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
        if (isJailed(p)) {
            event.setCancelled(true);
            // Получаем детали джейла через API
            CompletableFuture<List<Punishment>> jailListFuture = plugin.getApi().punishments().findActiveByTarget(p.getUniqueId());
            List<Punishment> jails = jailListFuture.join();
            if (!jails.isEmpty()) {
                Punishment jail = jails.get(0);
                String duration = jail.isPermanent() ? "навсегда" : TimeUtil.formatDuration(jail.expiresAt().get().toEpochMilli() - System.currentTimeMillis());
                MessageUtil.send(p, "jail_no_chat",
                                 "sender", jail.issuer().name(),
                                 "reason", jail.reason().value(),
                                 "server", jail.serverName(),
                                 "duration", duration);
            } else {
                MessageUtil.send(p, "jail_no_chat_fallback");
            }
        }
    }

    @EventHandler
    public void onJailedCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (isJailed(p)) {
            event.setCancelled(true);
            CompletableFuture<List<Punishment>> jailListFuture = plugin.getApi().punishments().findActiveByTarget(p.getUniqueId());
            List<Punishment> jails = jailListFuture.join();
            if (!jails.isEmpty()) {
                Punishment jail = jails.get(0);
                String duration = jail.isPermanent() ? "навсегда" : TimeUtil.formatDuration(jail.expiresAt().get().toEpochMilli() - System.currentTimeMillis());
                MessageUtil.send(p, "jail_no_commands",
                                 "sender", jail.issuer().name(),
                                 "reason", jail.reason().value(),
                                 "server", jail.serverName(),
                                 "duration", duration);
            } else {
                MessageUtil.send(p, "jail_no_commands_fallback");
            }
        }
    }
}
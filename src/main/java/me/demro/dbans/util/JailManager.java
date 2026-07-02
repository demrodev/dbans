package me.demro.dbans.util;

import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.model.JailPunishment;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.LookClose;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class JailManager {

    private final DBans plugin;
    private final Map<UUID, BukkitTask> phraseTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> lightningTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Object> npcMap = new ConcurrentHashMap<>();
    private final Set<UUID> teleportAllowed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, JailPunishment> activeJailCache = new ConcurrentHashMap<>();
    private final boolean jailEnabled;
    private final int randomRange;
    private final boolean citizensEnabled;
    private BukkitTask expiryChecker;

    public JailManager(DBans plugin) {
        this.plugin = plugin;
        this.citizensEnabled = Bukkit.getPluginManager().getPlugin("Citizens") != null;
        if (!citizensEnabled) {
            log.warn("Citizens2 not found! Using ArmorStand for jail NPC.");
        }
        this.jailEnabled = plugin.getJailConfig().getBoolean("enabled", true);
        this.randomRange = plugin.getJailConfig().getInt("random_range", 500);

        if (jailEnabled) {
            createJailWorld();
            startExpiryChecker();
            refreshActiveJailCache();
            log.info("Jail system enabled.");
        } else {
            log.info("Jail system is disabled in jail.yml. No resources will be consumed.");
        }
    }

    private void refreshActiveJailCache() {
        activeJailCache.clear();
        for (JailPunishment jail : plugin.getDatabase().getAllActiveJails()) {
            if (jail.isActive() && !jail.isExpired()) {
                activeJailCache.put(jail.getPlayerUuid(), jail);
            }
        }
    }

    private void createJailWorld() {
        if (Bukkit.getWorld("jail") == null) {
            WorldCreator creator = new WorldCreator("jail");
            creator.environment(World.Environment.THE_END);
            creator.generator(new me.demro.dbans.world.JailWorldGenerator());
            creator.createWorld();
        }
        World world = Bukkit.getWorld("jail");
        if (world != null) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setTime(0);
        }
    }

    private void startExpiryChecker() {
        expiryChecker = new BukkitRunnable() {
            @Override
            public void run() {
                List<JailPunishment> expired = plugin.getDatabase().getExpiredActiveJails();
                for (JailPunishment jail : expired) {
                    Player player = Bukkit.getPlayer(jail.getPlayerUuid());
                    if (player != null && player.isOnline()) {
                        releaseFromJail(player, jail);
                    } else {
                        plugin.getDatabase().setPendingUnjail(jail.getPlayerUuid(), true);
                    }
                    jail.setActive(false);
                    plugin.getDatabase().updateJail(jail);
                    activeJailCache.remove(jail.getPlayerUuid());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public String sendToJail(Player player, Long durationMillis, Location previousLocation,
                             String issuerName, String reason
    ) {
        if (!jailEnabled) {
            MessageUtil.send(player, "jail_disabled");
            return null;
        }

        if (activeJailCache.containsKey(player.getUniqueId())) {
            MessageUtil.send(player, "already_jailed");
            return null;
        }

        World jailWorld = Bukkit.getWorld("jail");
        if (jailWorld == null) {
            MessageUtil.send(player, "jail_no_world");
            return null;
        }

        player.setGameMode(GameMode.ADVENTURE);

        int platformSize = plugin.getJailConfig().getInt("platform_size", 10);
        int centerX = (int) (Math.random() * randomRange * 2) - randomRange;
        int centerZ = (int) (Math.random() * randomRange * 2) - randomRange;
        int y = 65;

        for (int dx = -platformSize / 2; dx <= platformSize / 2; dx++) {
            for (int dz = -platformSize / 2; dz <= platformSize / 2; dz++) {
                jailWorld.getBlockAt(centerX + dx, y - 1, centerZ + dz).setType(Material.BARRIER);
            }
        }
        Location jailLoc = new Location(jailWorld, centerX + 0.5, y, centerZ + 0.5);
        player.teleport(jailLoc);

        applyJailEffects(player);
        startLightning(player);

        String npcName = MessageUtil.colorize(plugin.getJailConfig().getString("npc.name", "&cТюремщик"));
        Location npcLoc = jailLoc.clone().add(2, 0, 0);
        Object npc = createNPC(jailWorld, npcLoc, npcName);
        if (npc != null) {
            npcMap.put(player.getUniqueId(), npc);
            startPhrases(player, npc, npcName);
        }

        String id = Punishment.generateId();
        long start = System.currentTimeMillis();
        Long end = (durationMillis == null || durationMillis <= 0) ? null : start + durationMillis;
        JailPunishment jail = JailPunishment.builder()
                                            .id(id)
                                            .playerUuid(player.getUniqueId())
                                            .playerName(player.getName())
                                            .issuerUuid(UUID.nameUUIDFromBytes("CONSOLE".getBytes()))
                                            .issuerName(issuerName)
                                            .reason(reason)
                                            .startTime(start)
                                            .endTime(end)
                                            .active(true)
                                            .serverName(plugin.getServerName())
                                            .previousLocation(previousLocation)
                                            .jailLocation(jailLoc)
                                            .build();
        plugin.getDatabase().saveJail(jail);
        if (plugin.getProxySyncManager() != null) {
            plugin.getProxySyncManager().sendPunishmentCreate(jail);
        }
        if (plugin.getProxySyncManager() != null) {
            Punishment p = Punishment.builder()
                                     .id(jail.getId())
                                     .playerUuid(jail.getPlayerUuid())
                                     .playerName(jail.getPlayerName())
                                     .issuerUuid(jail.getIssuerUuid())
                                     .issuerName(jail.getIssuerName())
                                     .type(PunishmentType.JAIL)
                                     .reason(jail.getReason())
                                     .startTime(jail.getStartTime())
                                     .endTime(jail.getEndTime())
                                     .active(jail.isActive())
                                     .serverName(jail.getServerName())
                                     .build();
            plugin.getProxySyncManager().sendPunishmentCreate(p);
        }
        activeJailCache.put(player.getUniqueId(), jail);

        teleportAllowed.add(player.getUniqueId());
        try {
            player.teleport(jailLoc);
        } finally {
            teleportAllowed.remove(player.getUniqueId());
        }

        String durationStr = (end == null) ? "навсегда" : TimeUtil.formatDuration(durationMillis);
        MessageUtil.send(player, "jail_auto_start", "duration", durationStr, "reason", reason);
        return id;
    }

    private void applyJailEffects(@NotNull Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 10, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
    }

    private @Nullable Object createNPC(World world, Location loc, String name) {
        if (citizensEnabled) {
            try {
                NPCRegistry registry = CitizensAPI.getNPCRegistry();
                NPC npc = registry.createNPC(EntityType.PLAYER, name);
                npc.spawn(loc);
                npc.getOrAddTrait(LookClose.class).toggle();
                npc.setProtected(true);
                return npc;
            } catch (Throwable e) {
                log.warn("Failed to create Citizens NPC: {}", e.getMessage());
                return null;
            }
        } else {
            ArmorStand as = world.spawn(loc, ArmorStand.class);
            as.setCustomName(name);
            as.setCustomNameVisible(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setVisible(false);
            as.setMetadata("jailNPC", new FixedMetadataValue(plugin, true));
            return as;
        }
    }

    private void destroyNPC(Object npc) {
        switch (npc) {
            case NPC npc1 when citizensEnabled -> npc1.destroy();
            case ArmorStand armorStand -> armorStand.remove();
            case null, default -> {
            }
        }
    }

    private void startPhrases(Player player, Object npc, String npcName) {
        List<String> phrases = plugin.getJailConfig().getStringList("npc.phrases");
        int interval = plugin.getJailConfig().getInt("npc.phrase_interval", 10);
        if (phrases.isEmpty()) return;
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isJailed(player)) {
                    cancel();
                    phraseTasks.remove(player.getUniqueId());
                    return;
                }
                String phrase = phrases.get(new Random().nextInt(phrases.size()));
                player.sendMessage(MessageUtil.deserialize("&7[" + npcName + "&7] &f" + phrase));
            }
        }.runTaskTimer(plugin, interval * 20L, interval * 20L);
        phraseTasks.put(player.getUniqueId(), task);
    }

    private void startLightning(@NotNull Player player) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isJailed(player)) {
                    cancel();
                    lightningTasks.remove(player.getUniqueId());
                    return;
                }
                player.getWorld().strikeLightningEffect(player.getLocation());
            }
        }.runTaskTimer(plugin, 0L, 100L);
        lightningTasks.put(player.getUniqueId(), task);
    }

    private void removePlatform(Location center, int size) {
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int y = center.getBlockY() - 1;
        for (int dx = -size / 2; dx <= size / 2; dx++) {
            for (int dz = -size / 2; dz <= size / 2; dz++) {
                world.getBlockAt(cx + dx, y, cz + dz).setType(Material.AIR);
            }
        }
    }

    public void releaseFromJail(Player player, JailPunishment jail) {
        if (!jailEnabled) return;

        int platformSize = plugin.getJailConfig().getInt("platform_size", 10);
        removePlatform(jail.getJailLocation(), platformSize);

        jail.setActive(false);
        plugin.getDatabase().updateJail(jail);
        plugin.getDatabase().clearPendingUnjail(player.getUniqueId());
        activeJailCache.remove(player.getUniqueId());

        teleportAllowed.add(player.getUniqueId());
        try {
            Location previous = jail.getPreviousLocation();
            if (previous != null && previous.getWorld() != null) {
                player.teleport(previous);
            } else {
                player.teleport(player.getWorld().getSpawnLocation());
                log.warn("Previous location is null for {}, teleported to spawn.", player.getName());
            }
        } finally {
            teleportAllowed.remove(player.getUniqueId());
        }

        clearAllEffects(player);

        if (player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        BukkitTask lt = lightningTasks.remove(player.getUniqueId());
        if (lt != null) lt.cancel();
        BukkitTask pt = phraseTasks.remove(player.getUniqueId());
        if (pt != null) pt.cancel();
        Object npc = npcMap.remove(player.getUniqueId());
        if (npc != null) destroyNPC(npc);

        MessageUtil.send(player, "jail_expired");
    }

    public void handlePlayerJoin(Player player) {
        if (!jailEnabled) return;
        UUID uuid = player.getUniqueId();

        if (plugin.getDatabase().hasPendingUnjail(uuid)) {
            JailPunishment jail = null;
            List<JailPunishment> jails = plugin.getDatabase().getAllJailsForPlayer(uuid);
            for (JailPunishment j : jails) {
                if (j.getEndTime() != null && j.getEndTime() <= System.currentTimeMillis() && !j.isActive()) {
                    jail = j;
                    break;
                }
            }
            if (jail != null) {
                Location target = jail.getPreviousLocation();
                if (target == null || target.getWorld() == null) {
                    target = player.getWorld().getSpawnLocation();
                }
                teleportAllowed.add(uuid);
                try {
                    player.teleport(target);
                } finally {
                    teleportAllowed.remove(uuid);
                }
                clearAllEffects(player);
                if (player.getGameMode() == GameMode.ADVENTURE) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                int platformSize = plugin.getJailConfig().getInt("platform_size", 10);
                removePlatform(jail.getJailLocation(), platformSize);
                jail.setActive(false);
                plugin.getDatabase().updateJail(jail);
                plugin.getDatabase().clearPendingUnjail(uuid);
                activeJailCache.remove(uuid);
                MessageUtil.send(player, "jail_expired");
                player.updateInventory();
            } else {
                plugin.getDatabase().clearPendingUnjail(uuid);
            }
            teleportOutOfJailIfNeeded(player);
            return;
        }

        JailPunishment jail = activeJailCache.get(uuid);
        if (jail == null) {
            jail = plugin.getDatabase().getActiveJail(uuid);
            if (jail != null && jail.isActive() && !jail.isExpired()) {
                activeJailCache.put(uuid, jail);
            }
        }

        if (jail != null && jail.isActive()) {
            if (jail.isExpired()) {
                releaseFromJail(player, jail);
                jail.setActive(false);
                plugin.getDatabase().updateJail(jail);
                activeJailCache.remove(uuid);
            } else {
                Location jailLoc = jail.getJailLocation();
                if (jailLoc == null || jailLoc.getWorld() == null) {
                    World w = Bukkit.getWorld("jail");
                    if (w != null) jailLoc = new Location(w, 0.5, 65, 0.5);
                }
                teleportAllowed.add(uuid);
                try {
                    player.teleport(jailLoc);
                } finally {
                    teleportAllowed.remove(uuid);
                }
                applyJailEffects(player);
                startLightning(player);
                if (!npcMap.containsKey(uuid)) {
                    World jailWorld = Bukkit.getWorld("jail");
                    if (jailWorld != null) {
                        String npcName = MessageUtil.colorize(plugin.getJailConfig().getString("npc.name", "&cТюремщик"));
                        Location npcLoc = jailLoc.clone().add(2, 0, 0);
                        Object npc = createNPC(jailWorld, npcLoc, npcName);
                        if (npc != null) {
                            npcMap.put(uuid, npc);
                            startPhrases(player, npc, npcName);
                        }
                    }
                }
            }
        } else {
            teleportOutOfJailIfNeeded(player);
        }
    }

    public void teleportOutOfJailIfNeeded(Player player) {
        if (!jailEnabled) return;
        UUID uuid = player.getUniqueId();
        if (teleportAllowed.contains(uuid)) return;
        if (player.getWorld().getName().equals("jail") && !isJailed(player)) {
            Location target = null;
            List<JailPunishment> history = plugin.getDatabase().getAllJailsForPlayer(uuid);
            if (!history.isEmpty()) {
                JailPunishment last = history.get(0);
                if (last.getPreviousLocation() != null && last.getPreviousLocation().getWorld() != null) {
                    target = last.getPreviousLocation();
                }
            }
            if (target == null) target = player.getWorld().getSpawnLocation();
            teleportAllowed.add(uuid);
            try {
                player.teleport(target);
                MessageUtil.send(player, "jail_teleport_out");
            } finally {
                teleportAllowed.remove(uuid);
            }
            clearAllEffects(player);
        }
    }

    public boolean isJailed(Player player) {
        if (!jailEnabled) return false;
        JailPunishment jail = activeJailCache.get(player.getUniqueId());
        return jail != null && jail.isActive() && !jail.isExpired();
    }

    public boolean isTeleportAllowed(@NotNull Player player) {
        return teleportAllowed.contains(player.getUniqueId());
    }

    public void reload() {
        if (expiryChecker != null) expiryChecker.cancel();
        for (BukkitTask task : phraseTasks.values()) task.cancel();
        for (BukkitTask task : lightningTasks.values()) task.cancel();
        phraseTasks.clear();
        lightningTasks.clear();
        for (Object npc : npcMap.values()) destroyNPC(npc);
        npcMap.clear();
        activeJailCache.clear();
        if (jailEnabled) {
            startExpiryChecker();
            refreshActiveJailCache();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isJailed(p)) handlePlayerJoin(p);
            }
        }
    }

    public String teleportToJail(Player player, Long durationMillis, Location previousLocation,
                                 String issuerName, String reason
    ) {
        return sendToJail(player, durationMillis, previousLocation, issuerName, reason);
    }

    private void clearAllEffects(@NotNull Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
    }

    public boolean isEnabled() {
        return jailEnabled;
    }
}
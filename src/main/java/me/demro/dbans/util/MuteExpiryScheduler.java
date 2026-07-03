package me.demro.dbans.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.demro.dbans.DBans;
import me.demro.dbans.api.adapter.PunishmentAdapter;
import me.demro.dbans.model.Punishment;
import me.demro.dbans.model.PunishmentType;
import me.demro.dbans.sync.ProxySyncManager;
import me.demro.dlibs.dbans.api.event.EventOrigin;
import me.demro.dlibs.dbans.api.event.PunishmentExpireEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class MuteExpiryScheduler {

    private static final long MILLIS_PER_TICK = 50L;

    private final DBans plugin;
    private final ProxySyncManager proxySyncManager;
    private final Map<String, BukkitTask> tasks = new ConcurrentHashMap<>();

    public void deactivateExpiredMutes() {
        List<Punishment> activeMutes = plugin.getDatabase().getAllActivePunishmentsByType(PunishmentType.MUTE);
        long now = System.currentTimeMillis();
        int expiredCount = 0;

        for (Punishment mute : activeMutes) {
            if (mute.getEndTime() != null && mute.getEndTime() <= now) {
                expireMute(mute);
                expiredCount++;
            }
        }

        log.info("Deactivated {} expired mute(s) out of {} active.", expiredCount, activeMutes.size());
    }

    public void rescheduleAll() {
        cancelAll();
        List<Punishment> activeMutes = plugin.getDatabase().getAllActivePunishmentsByType(PunishmentType.MUTE);
        for (Punishment mute : activeMutes) {
            if (mute.getEndTime() != null && mute.isActive() && mute.getEndTime() > System.currentTimeMillis()) {
                schedule(mute);
            }
        }
    }

    public void schedule(@NotNull Punishment mute) {
        if (mute.getEndTime() == null) {
            return;
        }
        long delay = mute.getEndTime() - System.currentTimeMillis();
        if (delay <= 0) {
            expireMute(mute);
            return;
        }

        cancel(mute.getId());

        long delayTicks = Math.max(1L, delay / MILLIS_PER_TICK);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            expireMute(mute);
            tasks.remove(mute.getId());
        }, delayTicks);
        tasks.put(mute.getId(), task);
    }

    public void cancel(String punishmentId) {
        BukkitTask task = tasks.remove(punishmentId);
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelAll() {
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
    }

    private void expireMute(@NotNull Punishment mute) {
        mute.setActive(false);
        plugin.getDatabase().updatePunishment(mute);
        plugin.getCacheManager().invalidateMuteCache(mute.getPlayerUuid());

        Player player = Bukkit.getPlayer(mute.getPlayerUuid());
        if (player != null && player.isOnline()) {
            MessageUtil.send(player, "expire_mute", "id", mute.getId());
        }

        try {
            PunishmentExpireEvent event = new PunishmentExpireEvent(
                    new PunishmentAdapter(mute), EventOrigin.INTERNAL, Instant.now(), false
            );
            Bukkit.getPluginManager().callEvent(event);
        } catch (Exception e) {
            log.warn("Failed to dispatch PunishmentExpireEvent for mute {}: {}", mute.getId(), e.getMessage());
        }

        if (proxySyncManager != null) {
            proxySyncManager.sendPunishmentExpire(mute);
        }
    }
}

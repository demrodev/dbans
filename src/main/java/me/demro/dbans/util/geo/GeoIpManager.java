package me.demro.dbans.util.geo;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import lombok.CustomLog;
import me.demro.dbans.DBans;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

@CustomLog
public class GeoIpManager {

    private final DBans plugin;
    private final ConcurrentHashMap<String, String> locationCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> countryCodeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> countryNameCache = new ConcurrentHashMap<>();
    private DatabaseReader reader;
    private boolean ready = false;

    public GeoIpManager(DBans plugin) {
        this.plugin = plugin;
        loadDatabase();
        startCacheCleaner();
    }

    private void loadDatabase() {
        new BukkitRunnable() {
            @Override
            public void run() {
                File dataFolder = plugin.getDataFolder();
                File geoipDir = new File(dataFolder, "geoip");
                if (!geoipDir.exists()) geoipDir.mkdirs();

                File dbFile = new File(geoipDir, "GeoLite2-City.mmdb");
                boolean needDownload = !dbFile.exists() ||
                                       (System.currentTimeMillis() - dbFile.lastModified() > 7L * 24 * 60 * 60 * 1000);

                if (needDownload) {
                    log.info("Downloading MaxMind GeoIP2 City database...");
                    String url = "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-City.mmdb";
                    if (HttpDownloader.downloadFile(url, dbFile)) {
                        log.info("MaxMind GeoIP database downloaded successfully");
                    } else {
                        log.warn("Failed to download GeoIP database. Using existing file (if any)");
                    }
                }

                if (dbFile.exists()) {
                    try {
                        reader = new DatabaseReader.Builder(dbFile).build();
                        ready = true;
                        log.info("MaxMind GeoIP database loaded from {}", dbFile.getPath());
                    } catch (Exception e) {
                        log.error("Failed to load GeoIP database: {}", e.getMessage(), e);
                    }
                } else {
                    log.warn("No GeoIP database available. /geoip command will not work");
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void reload() {
        ready = false;
        reader = null;
        locationCache.clear();
        countryCodeCache.clear();
        countryNameCache.clear();
        loadDatabase();
    }

    public String getCountryCode(String ip) {
        if (!ready || reader == null) return null;
        String cached = countryCodeCache.get(ip);
        if (cached != null) return cached;

        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse response = reader.city(ipAddress);
            String code = response.getCountry().getIsoCode();
            if (code != null) {
                countryCodeCache.put(ip, code);
            }
            return code;
        } catch (Exception e) {
            return null;
        }
    }

    public String getCountryName(String ip) {
        if (!ready || reader == null) return null;
        String cached = countryNameCache.get(ip);
        if (cached != null) return cached;

        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse response = reader.city(ipAddress);
            String name = response.getCountry().getName();
            if (name != null) {
                countryNameCache.put(ip, name);
            }
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    public String getLocation(String ip) {
        if (!ready || reader == null) return null;
        String cached = locationCache.get(ip);
        if (cached != null) return cached;

        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse response = reader.city(ipAddress);
            String country = response.getCountry().getName();
            String city = response.getCity().getName();
            if (country == null) country = "Unknown";
            String location = (city != null && !city.isEmpty()) ? country + ", " + city : country;
            locationCache.put(ip, location);
            return location;
        } catch (Exception e) {
            log.warn("Failed to get location for IP {}: {}", ip, e.getMessage());
            return null;
        }
    }

    private void startCacheCleaner() {
        new BukkitRunnable() {
            @Override
            public void run() {
                locationCache.clear();
                countryCodeCache.clear();
                countryNameCache.clear();
            }
        }.runTaskTimerAsynchronously(plugin, 24L * 60L * 60L * 20L, 24L * 60L * 60L * 20L);
    }

    public boolean isReady() {
        return ready;
    }
}
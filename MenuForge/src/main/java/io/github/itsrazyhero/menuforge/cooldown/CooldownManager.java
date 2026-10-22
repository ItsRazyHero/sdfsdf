package io.github.itsrazyhero.menuforge.cooldown;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent per-player cooldowns keyed by a free-form string (e.g. "daily_gift"). */
public final class CooldownManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Long>> data = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public CooldownManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/cooldowns.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveIfDirty, 600L, 600L);
    }

    public long remaining(UUID uuid, String key) {
        Map<String, Long> map = data.get(uuid);
        if (map == null) return 0;
        Long until = map.get(key);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            map.remove(key);
            return 0;
        }
        return left;
    }

    public void set(UUID uuid, String key, long millis) {
        data.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()).put(key, System.currentTimeMillis() + millis);
        dirty = true;
    }

    public void reset(UUID uuid, String key) {
        Map<String, Long> map = data.get(uuid);
        if (map != null && map.remove(key) != null) dirty = true;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        for (String id : yaml.getKeys(false)) {
            var section = yaml.getConfigurationSection(id);
            if (section == null) continue;
            Map<String, Long> map = new ConcurrentHashMap<>();
            for (String key : section.getKeys(false)) {
                long until = section.getLong(key);
                if (until > now) map.put(key, until);
            }
            if (!map.isEmpty()) {
                try {
                    data.put(UUID.fromString(id), map);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        data.forEach((uuid, map) -> map.forEach((key, until) -> yaml.set(uuid + "." + key, until)));
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save cooldowns: " + e.getMessage());
        }
    }
}

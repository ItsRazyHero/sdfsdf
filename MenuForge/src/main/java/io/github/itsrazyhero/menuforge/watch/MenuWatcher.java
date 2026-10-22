package io.github.itsrazyhero.menuforge.watch;

import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Watches menus/ and textures/ and reloads automatically, so there is no /reload loop while editing by hand. */
public final class MenuWatcher implements Runnable {

    private final MenuForgePlugin plugin;
    private final Path menus, textures;
    private WatchService service;
    private Thread thread;
    private volatile long suppressUntil;
    private final AtomicBoolean menusPending = new AtomicBoolean(), texturesPending = new AtomicBoolean();

    public MenuWatcher(MenuForgePlugin plugin, Path menus, Path textures) {
        this.plugin = plugin;
        this.menus = menus;
        this.textures = textures;
    }

    public void start() {
        try {
            service = FileSystems.getDefault().newWatchService();
            menus.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            textures.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            thread = new Thread(this, "MenuForge-watcher");
            thread.setDaemon(true);
            thread.start();
        } catch (IOException e) {
            plugin.getLogger().warning("Auto-reload unavailable: " + e.getMessage());
        }
    }

    public void stop() {
        if (thread != null) thread.interrupt();
        try {
            if (service != null) service.close();
        } catch (IOException ignored) {
        }
    }

    /** Ignore events for a while (the plugin itself is writing files). */
    public void suppress(long millis) {
        suppressUntil = System.currentTimeMillis() + millis;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = service.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException | java.nio.file.ClosedWatchServiceException e) {
                return;
            }
            if (key == null) continue;
            boolean relevant = false;
            Path dir = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                String name = String.valueOf(event.context()).toLowerCase();
                if (dir.equals(menus) && name.endsWith(".yml")) {
                    menusPending.set(true);
                    relevant = true;
                } else if (dir.equals(textures) && name.endsWith(".png")) {
                    texturesPending.set(true);
                    relevant = true;
                }
            }
            key.reset();
            if (relevant && System.currentTimeMillis() > suppressUntil) {
                // debounce: editors write files in several steps
                Bukkit.getScheduler().runTaskLater(plugin, this::flush, 20L);
            }
        }
    }

    private void flush() {
        if (System.currentTimeMillis() <= suppressUntil) {
            menusPending.set(false);
            texturesPending.set(false);
            return;
        }
        if (texturesPending.getAndSet(false)) {
            plugin.rebuildPack(true);
            plugin.getLogger().info("Textures changed on disk - resource pack rebuilt.");
        }
        if (menusPending.getAndSet(false)) {
            int n = plugin.menus().loadAll();
            plugin.getLogger().info("Menus changed on disk - reloaded " + n + " menu(s).");
        }
    }
}

package io.github.itsrazyhero.menuforge;

import io.github.itsrazyhero.menuforge.bedrock.BedrockMenus;
import io.github.itsrazyhero.menuforge.command.MenuForgeCommand;
import io.github.itsrazyhero.menuforge.cooldown.CooldownManager;
import io.github.itsrazyhero.menuforge.gui.MenuListener;
import io.github.itsrazyhero.menuforge.hook.BedrockHook;
import io.github.itsrazyhero.menuforge.hook.VaultHook;
import io.github.itsrazyhero.menuforge.menu.MenuManager;
import io.github.itsrazyhero.menuforge.pack.PackBuilder;
import io.github.itsrazyhero.menuforge.placeholder.Placeholders;
import io.github.itsrazyhero.menuforge.watch.MenuWatcher;
import io.github.itsrazyhero.menuforge.web.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class MenuForgePlugin extends JavaPlugin {

    private Placeholders placeholders;
    private VaultHook vault;
    private BedrockHook bedrock;
    private CooldownManager cooldowns;
    private MenuManager menus;
    private BedrockMenus bedrockMenus;
    private PackBuilder pack;
    private WebServer web;
    private MenuWatcher watcher;
    private String token;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureToken();
        copyDefaults();

        vault = new VaultHook(getLogger());
        bedrock = new BedrockHook(getLogger());
        placeholders = new Placeholders(this);
        cooldowns = new CooldownManager(this);
        pack = new PackBuilder(this);
        web = new WebServer(this);
        menus = new MenuManager(this);
        bedrockMenus = new BedrockMenus(this);
        watcher = new MenuWatcher(this, menus.directory().toPath(), pack.texturesDir().toPath());

        pack.build();
        web.start();
        int loaded = menus.loadAll();
        if (getConfig().getBoolean("auto-reload", true)) watcher.start();

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        MenuForgeCommand command = new MenuForgeCommand(this);
        getCommand("menuforge").setExecutor(command);
        getCommand("menuforge").setTabCompleter(command);

        // hooks that load after us
        Bukkit.getScheduler().runTask(this, () -> {
            if (!vault.available()) vault = new VaultHook(getLogger());
            if (!placeholders.hasPapi()) placeholders.hookPapi();
        });
        getLogger().info("Loaded " + loaded + " menu(s).");
    }

    @Override
    public void onDisable() {
        if (watcher != null) watcher.stop();
        if (web != null) web.stop();
        if (cooldowns != null) cooldowns.saveIfDirty();
    }

    /** Rebuilds the pack from textures/ and optionally re-sends it to everyone online. */
    public void rebuildPack(boolean resend) {
        pack.build();
        if (resend && pack.hasTextures() && web.running()) {
            Bukkit.getOnlinePlayers().stream().filter(p -> !bedrock.isBedrock(p)).forEach(pack::send);
        }
    }

    private void ensureToken() {
        token = getConfig().getString("web.token", "");
        if (token == null || token.isBlank()) {
            token = new BigInteger(96, new SecureRandom()).toString(36);
            getConfig().set("web.token", token);
            saveConfig();
        }
    }

    /** First start: copy the bundled example menus so the server has something to show. */
    private void copyDefaults() {
        File dir = new File(getDataFolder(), "menus");
        if (dir.exists()) return;
        dir.mkdirs();
        for (String name : new String[]{"main", "daily_gifts", "shop", "warps"}) {
            try (InputStream in = getResource("templates/" + name + ".yml")) {
                if (in == null) continue;
                java.nio.file.Files.write(new File(dir, name + ".yml").toPath(), in.readAllBytes());
            } catch (IOException e) {
                getLogger().warning("Could not copy template " + name + ": " + e.getMessage());
            }
        }
        try (InputStream in = getResource("templates/README.txt")) {
            if (in != null) java.nio.file.Files.write(new File(getDataFolder(), "README.txt").toPath(),
                    new String(in.readAllBytes(), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    public String msg(String key) {
        return getConfig().getString("messages." + key, key);
    }

    public String token() {
        return token;
    }

    public Placeholders placeholders() {
        return placeholders;
    }

    public VaultHook vault() {
        return vault;
    }

    public BedrockHook bedrock() {
        return bedrock;
    }

    public CooldownManager cooldowns() {
        return cooldowns;
    }

    public MenuManager menus() {
        return menus;
    }

    public BedrockMenus bedrockMenus() {
        return bedrockMenus;
    }

    public PackBuilder pack() {
        return pack;
    }

    public WebServer web() {
        return web;
    }

    public MenuWatcher watcher() {
        return watcher;
    }
}

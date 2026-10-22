package io.github.itsrazyhero.menuforge.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import io.github.itsrazyhero.menuforge.menu.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Tiny embedded HTTP server: serves the no-code editor, the deploy API and the resource pack.
 * Routes: GET /  (editor), GET /pack.zip, GET /api/state, GET /api/template/{name},
 * GET /api/texture/{name}, GET /api/yaml/{menu}, POST /api/deploy, POST /api/open.
 */
public final class WebServer {

    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_-]{1,48}");

    private final MenuForgePlugin plugin;
    private HttpServer server;
    private JsonArray materials, sounds;

    public WebServer(MenuForgePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean running() {
        return server != null;
    }

    public int port() {
        return plugin.getConfig().getInt("web.port", 8085);
    }

    public String publicAddress() {
        String configured = plugin.getConfig().getString("web.public-address", "");
        if (configured != null && !configured.isBlank()) return configured.replaceAll("/+$", "");
        String ip = Bukkit.getIp();
        return "http://" + (ip == null || ip.isBlank() ? "localhost" : ip) + ":" + port();
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("web.enabled", true)) return;
        try {
            server = HttpServer.create(new InetSocketAddress(port()), 16);
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "MenuForge-web");
                t.setDaemon(true);
                return t;
            }));
            server.createContext("/", this::handle);
            server.start();
            plugin.getLogger().info("Web editor on " + publicAddress() + "  (token: " + plugin.token() + ")");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not start web server on port " + port() + ": " + e.getMessage());
            server = null;
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
        server = null;
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            if (method.equals("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (path.equals("/") || path.equals("/editor") || path.equals("/index.html")) {
                sendResource(ex, "web/editor.html", "text/html; charset=utf-8");
            } else if (path.equals("/pack.zip")) {
                File zip = plugin.pack().zipFile();
                if (!zip.exists()) send(ex, 404, "text/plain", "no pack".getBytes(StandardCharsets.UTF_8));
                else send(ex, 200, "application/zip", Files.readAllBytes(zip.toPath()));
            } else if (path.startsWith("/api/")) {
                if (!authorized(ex)) {
                    json(ex, 401, error("Bad or missing token"));
                    return;
                }
                api(ex, method, path.substring(5));
            } else {
                send(ex, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Web request failed: " + e);
            json(ex, 500, error(String.valueOf(e.getMessage())));
        }
    }

    private boolean authorized(HttpExchange ex) {
        String token = plugin.token();
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.equals("Bearer " + token)) return true;
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return false;
        for (String part : query.split("&")) {
            if (part.startsWith("token=") && URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8).equals(token)) return true;
        }
        return false;
    }

    private void api(HttpExchange ex, String method, String route) throws Exception {
        if (route.equals("state") && method.equals("GET")) {
            json(ex, 200, state());
        } else if (route.startsWith("template/") && method.equals("GET")) {
            String name = safe(route.substring(9));
            try (InputStream in = plugin.getResource("templates/" + name + ".yml")) {
                if (in == null) {
                    json(ex, 404, error("No such template"));
                    return;
                }
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                json(ex, 200, YamlJson.toJson(yaml));
            }
        } else if (route.startsWith("texture/") && method.equals("GET")) {
            File png = new File(plugin.pack().texturesDir(), safe(route.substring(8).replaceFirst("\\.png$", "")) + ".png");
            if (!png.exists()) send(ex, 404, "text/plain", new byte[0]);
            else send(ex, 200, "image/png", Files.readAllBytes(png.toPath()));
        } else if (route.startsWith("yaml/") && method.equals("GET")) {
            File file = new File(plugin.menus().directory(), safe(route.substring(5)) + ".yml");
            if (!file.exists()) send(ex, 404, "text/plain", new byte[0]);
            else send(ex, 200, "text/yaml; charset=utf-8", Files.readAllBytes(file.toPath()));
        } else if (route.equals("deploy") && method.equals("POST")) {
            JsonObject body = JsonParser.parseString(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            json(ex, 200, deploy(body));
        } else if (route.equals("open") && method.equals("POST")) {
            JsonObject body = JsonParser.parseString(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            String player = body.get("player").getAsString(), menuId = body.get("menu").getAsString();
            boolean ok = sync(() -> {
                var p = Bukkit.getPlayerExact(player);
                Menu menu = plugin.menus().get(menuId);
                return p != null && menu != null && plugin.menus().open(p, menu, new String[0], false);
            });
            json(ex, 200, ok ? ok() : error("Player offline or menu missing"));
        } else {
            json(ex, 404, error("Unknown route"));
        }
    }

    private JsonObject state() throws Exception {
        JsonObject out = ok();
        JsonObject menus = new JsonObject();
        File[] files = plugin.menus().directory().listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                menus.add(f.getName().replaceFirst("\\.yml$", ""), YamlJson.toJson(YamlConfiguration.loadConfiguration(f)));
            }
        }
        out.add("menus", menus);

        JsonArray textures = new JsonArray();
        plugin.pack().glyphs().forEach((name, glyph) -> {
            JsonObject t = new JsonObject();
            t.addProperty("name", name);
            t.addProperty("width", glyph.width());
            t.addProperty("height", glyph.height());
            t.addProperty("glyph", String.valueOf(glyph.ch()));
            t.addProperty("title", plugin.pack().composeLegacyTitle(name, ""));
            t.addProperty("titleMini", plugin.pack().composeTitle(name, ""));
            textures.add(t);
        });
        out.add("textures", textures);

        JsonArray templates = new JsonArray();
        try (InputStream in = plugin.getResource("templates/index.txt")) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isBlank()) templates.add(line.trim());
                }
            }
        }
        out.add("templates", templates);
        if (materials == null) {
            materials = new JsonArray();
            Arrays.stream(Material.values()).filter(m -> m.isItem() && !m.isLegacy() && !m.isAir())
                    .map(Enum::name).sorted().forEach(materials::add);
            sounds = new JsonArray();
            Registry.SOUND_EVENT.stream().map(s -> Registry.SOUND_EVENT.getKeyOrThrow(s).getKey()).sorted().forEach(sounds::add);
        }
        out.add("materials", materials);
        out.add("sounds", sounds);

        JsonObject server = new JsonObject();
        server.addProperty("version", Bukkit.getMinecraftVersion());
        server.addProperty("plugin", plugin.getPluginMeta().getVersion());
        server.addProperty("bedrock", plugin.bedrock().available());
        server.addProperty("vault", plugin.vault().available());
        server.addProperty("papi", plugin.placeholders().hasPapi());
        server.addProperty("packUrl", plugin.pack().packUrl());
        JsonArray players = new JsonArray();
        Bukkit.getOnlinePlayers().forEach(p -> players.add(p.getName()));
        server.add("players", players);
        out.add("server", server);
        return out;
    }

    /** {menus: {id: object}, textures: {name: base64 png}, deleteMenus: [], deleteTextures: []} */
    private JsonObject deploy(JsonObject body) throws Exception {
        plugin.watcher().suppress(10_000); // we are about to write files ourselves
        int written = 0;
        if (body.has("menus")) {
            for (var entry : body.getAsJsonObject("menus").entrySet()) {
                String id = safe(entry.getKey());
                YamlConfiguration yaml = YamlJson.toYaml(entry.getValue().getAsJsonObject());
                yaml.options().setHeader(List.of("MenuForge menu '" + id + "' - deployed from the web editor"));
                File file = new File(plugin.menus().directory(), id + ".yml");
                file.getParentFile().mkdirs();
                yaml.save(file);
                written++;
            }
        }
        if (body.has("deleteMenus")) {
            for (JsonElement e : body.getAsJsonArray("deleteMenus")) {
                new File(plugin.menus().directory(), safe(e.getAsString()) + ".yml").delete();
            }
        }
        boolean texturesChanged = false;
        if (body.has("textures")) {
            for (var entry : body.getAsJsonObject("textures").entrySet()) {
                String data = entry.getValue().getAsString();
                int comma = data.indexOf(',');
                byte[] png = Base64.getDecoder().decode(comma >= 0 ? data.substring(comma + 1) : data);
                Files.write(new File(plugin.pack().texturesDir(), safe(entry.getKey()) + ".png").toPath(), png);
                texturesChanged = true;
            }
        }
        if (body.has("deleteTextures")) {
            for (JsonElement e : body.getAsJsonArray("deleteTextures")) {
                texturesChanged |= new File(plugin.pack().texturesDir(), safe(e.getAsString()) + ".png").delete();
            }
        }
        boolean rebuild = texturesChanged;
        int menusLoaded = sync(() -> {
            plugin.watcher().suppress(3000);
            if (rebuild) plugin.rebuildPack(true);
            return plugin.menus().loadAll();
        });
        JsonObject out = ok();
        out.addProperty("written", written);
        out.addProperty("menus", menusLoaded);
        out.addProperty("packRebuilt", rebuild);
        return out;
    }

    private <T> T sync(java.util.concurrent.Callable<T> task) throws ExecutionException, InterruptedException {
        return Bukkit.getScheduler().callSyncMethod(plugin, task).get();
    }

    private static String safe(String id) {
        String s = id.toLowerCase(Locale.ROOT).trim();
        if (!SAFE_ID.matcher(s).matches()) throw new IllegalArgumentException("Bad id: " + id);
        return s;
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", message);
        return o;
    }

    private void json(HttpExchange ex, int status, JsonObject body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", YamlJson.GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private void sendResource(HttpExchange ex, String resource, String type) throws IOException {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                send(ex, 404, "text/plain", "missing".getBytes(StandardCharsets.UTF_8));
                return;
            }
            send(ex, 200, type, in.readAllBytes());
        }
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        } else {
            ex.close();
        }
    }

    @SuppressWarnings("unused")
    private static URI uri(String s) {
        return URI.create(s);
    }
}

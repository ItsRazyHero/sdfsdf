package io.github.itsrazyhero.menuforge.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts menu YAML to JSON for the editor and back, so the editor never has to parse YAML itself. */
public final class YamlJson {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private YamlJson() {}

    public static JsonObject toJson(ConfigurationSection section) {
        JsonObject out = new JsonObject();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            out.add(key, value instanceof ConfigurationSection sub ? toJson(sub) : toJsonValue(value));
        }
        return out;
    }

    private static JsonElement toJsonValue(Object value) {
        if (value == null) return com.google.gson.JsonNull.INSTANCE;
        if (value instanceof ConfigurationSection section) return toJson(section);
        if (value instanceof Map<?, ?> map) {
            JsonObject obj = new JsonObject();
            map.forEach((k, v) -> obj.add(String.valueOf(k), toJsonValue(v)));
            return obj;
        }
        if (value instanceof List<?> list) {
            JsonArray arr = new JsonArray();
            list.forEach(v -> arr.add(toJsonValue(v)));
            return arr;
        }
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        return new JsonPrimitive(String.valueOf(value));
    }

    public static YamlConfiguration toYaml(JsonObject json) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : json.entrySet()) {
            Object value = fromJson(entry.getValue());
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                yaml.createSection(entry.getKey(), typed);
            } else {
                yaml.set(entry.getKey(), value);
            }
        }
        return yaml;
    }

    public static Object fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            element.getAsJsonObject().entrySet().forEach(e -> map.put(e.getKey(), fromJson(e.getValue())));
            return map;
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            element.getAsJsonArray().forEach(e -> list.add(fromJson(e)));
            return list;
        }
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) {
            double d = p.getAsDouble();
            return d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE ? (Object) (int) d : (Object) d;
        }
        return p.getAsString();
    }
}

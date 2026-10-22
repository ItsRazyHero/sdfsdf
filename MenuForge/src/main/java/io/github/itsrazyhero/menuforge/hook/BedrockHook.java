package io.github.itsrazyhero.menuforge.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Talks to Floodgate (preferred) or Geyser-Spigot through reflection and builds Cumulus
 * SimpleForms, so the plugin compiles and runs without either of them on the classpath.
 */
public final class BedrockHook {

    /** A button of a simple form: label plus optional image (path inside the Bedrock client or URL). */
    public record Button(String text, String image) {}

    private final Logger log;
    private Object api;
    private Class<?> simpleForm, imageType;

    public BedrockHook(Logger log) {
        this.log = log;
        hook();
    }

    private void hook() {
        api = null;
        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
                api = Class.forName("org.geysermc.floodgate.api.FloodgateApi").getMethod("getInstance").invoke(null);
            } else if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
                api = Class.forName("org.geysermc.geyser.api.GeyserApi").getMethod("api").invoke(null);
            }
            if (api != null) {
                simpleForm = Class.forName("org.geysermc.cumulus.form.SimpleForm");
                imageType = Class.forName("org.geysermc.cumulus.util.FormImage$Type");
                log.info("Bedrock forms enabled through " + api.getClass().getSimpleName() + ".");
            }
        } catch (ReflectiveOperationException e) {
            log.warning("Bedrock API detected but could not be hooked: " + e);
            api = null;
        }
    }

    public boolean available() {
        return api != null;
    }

    public boolean isBedrock(Player player) {
        if (api == null) return false;
        try {
            // Floodgate: isFloodgatePlayer(UUID); Geyser: isBedrockPlayer(UUID)
            String name = api.getClass().getSimpleName().contains("Floodgate") ? "isFloodgatePlayer" : "isBedrockPlayer";
            return (boolean) invoke(api, name, player.getUniqueId());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Shows a simple form. onClick receives the button index, onClose runs when the form is dismissed.
     * Returns false if the form could not be built or sent.
     */
    public boolean sendSimpleForm(Player player, String title, String content, List<Button> buttons,
                                  Consumer<Integer> onClick, Runnable onClose) {
        if (api == null) return false;
        try {
            Object builder = simpleForm.getMethod("builder").invoke(null);
            invoke(builder, "title", title);
            invoke(builder, "content", content == null ? "" : content);
            for (Button btn : buttons) {
                if (btn.image() != null && !btn.image().isBlank()) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object type = Enum.valueOf((Class<? extends Enum>) imageType, btn.image().startsWith("http") ? "URL" : "PATH");
                    invoke(builder, "button", btn.text(), type, btn.image());
                } else {
                    invoke(builder, "button", btn.text());
                }
            }
            Consumer<Object> valid = response -> {
                try {
                    onClick.accept((int) response.getClass().getMethod("clickedButtonId").invoke(response));
                } catch (ReflectiveOperationException e) {
                    log.warning("Bedrock form response error: " + e);
                }
            };
            invoke(builder, "validResultHandler", valid);
            if (onClose != null) invoke(builder, "closedResultHandler", onClose);
            Object form = builder.getClass().getMethod("build").invoke(builder);
            invoke(api, "sendForm", player.getUniqueId(), form);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("Could not send Bedrock form: " + e);
            return false;
        }
    }

    /** Invokes the public method whose parameter types accept the given arguments (handles overloads). */
    private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] types = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < args.length && ok; i++) ok = args[i] == null || wrap(types[i]).isInstance(args[i]);
            if (ok) return m.invoke(target, args);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "/" + args.length);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        return Object.class;
    }
}

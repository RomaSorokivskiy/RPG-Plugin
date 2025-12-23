package ua.roma.roflrpg.services;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ua.roma.roflrpg.util.Msg;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight localization service.
 *
 * <p>Supports language files in {@code plugins/RoflRPG/lang/}.
 * The project ships with at least:
 * <ul>
 *   <li>{@code uk_UA.yaml}</li>
 *   <li>{@code en_US.yaml}</li>
 * </ul>
 *
 * <p>Any MiniMessage string can reference a translation key by using {@code @key.path}.
 * Example in a definition YAML:
 * <pre>
 * name: "@defs.classes.warrior.name"
 * </pre>
 */
public final class LangService {
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> bundles = new HashMap<>();

    /** Default language when player locale can't be detected. */
    private final String defaultLocale = "uk_UA";

    public LangService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        // Ship at least these two locales in resources.
        loadBundle("uk_UA");
        loadBundle("en_US");
    }

    private void loadBundle(String code) {
        File f = resolveLangFile(code);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);

        // Small one-time migration for old configs that still show bind-hint in actionbar.
        migrateHudActionbar(f, yml);

        bundles.put(code, yml);
    }

    /**
     * Prefer *.yaml, but keep backward compatibility with *.yml (older plugin versions).
     */
    private File resolveLangFile(String code) {
        File yaml = new File(plugin.getDataFolder(), "lang/" + code + ".yaml");
        File yml = new File(plugin.getDataFolder(), "lang/" + code + ".yml");

        // If user already has an old file, respect it.
        if (yaml.exists()) return yaml;
        if (yml.exists()) return yml;

        // Otherwise copy defaults from jar.
        ensure("lang/" + code + ".yaml");
        return yaml;
    }

    private void migrateHudActionbar(File file, YamlConfiguration yml) {
        String key = "hud.actionbar";
        String v = yml.getString(key, "");
        String low = v == null ? "" : v.toLowerCase(Locale.ROOT);

        boolean looksLikeOldHint =
                low.contains("swap hands") ||
                low.contains("слот 1-9") ||
                low.contains("skills: z") ||
                low.contains("скіли:") ||
                low.contains("slot 1-9");

        if (!looksLikeOldHint) return;

        String replacement =
                "<gray>Lvl <white>{level}</white> <dark_gray>|</dark_gray> " +
                "<gray>{race}</gray> <dark_gray>|</dark_gray> <gray>{class}</gray> " +
                "<dark_gray>|</dark_gray> <aqua>✦ {mana}/{maxMana}</aqua> " +
                "<dark_gray>|</dark_gray> <yellow>⚡ {stamina}/{maxStamina}</yellow> {cd}</gray>";

        yml.set(key, replacement);
        try {
            yml.save(file);
            plugin.getLogger().info("[Lang] Migrated " + file.getName() + " hud.actionbar (removed old bind hint)." );
        } catch (IOException ex) {
            plugin.getLogger().warning("[Lang] Failed to save migration for " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void ensure(String resPath) {
        File f = new File(plugin.getDataFolder(), resPath);
        if (f.exists()) return;
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        plugin.saveResource(resPath, false);
    }

    /**
     * Returns normalized locale code used by this plugin, e.g. {@code uk_UA} or {@code en_US}.
     */
    public String locale(Player p) {
        String raw = null;

        // Paper/Spigot expose player locale, but method name differs across versions.
        // Reflection keeps compilation stable if API changes.
        try {
            Method m = p.getClass().getMethod("locale");
            Object o = m.invoke(p);
            if (o instanceof String s) raw = s;
        } catch (Throwable ignored) {
        }

        if (raw == null || raw.isBlank()) return defaultLocale;

        String norm = raw.replace('-', '_').toLowerCase(Locale.ROOT);

        if (norm.startsWith("uk") || norm.contains("_ua") || norm.contains("uk_")) return "uk_UA";
        if (norm.startsWith("en") || norm.contains("en_")) return "en_US";

        // Fallback for any other locale.
        return defaultLocale;
    }

    private YamlConfiguration bundle(Player p) {
        String loc = locale(p);
        YamlConfiguration b = bundles.get(loc);
        if (b != null) return b;
        return bundles.getOrDefault(defaultLocale, null);
    }

    public String tr(Player p, String key) {
        if (key == null) return "";
        YamlConfiguration b = bundle(p);
        if (b != null) {
            String v = b.getString(key);
            if (v != null) return v;
        }
        // fallback to default locale
        YamlConfiguration def = bundles.get(defaultLocale);
        if (def != null) {
            String v = def.getString(key);
            if (v != null) return v;
        }
        return key;
    }

    public String tr(Player p, String key, Map<String, String> vars) {
        return Msg.fmt(tr(p, key), vars);
    }

    /** Resolves MiniMessage strings like "@key.path" through language files. */
    public String resolve(Player p, String maybeKey) {
        if (maybeKey == null) return "";
        if (maybeKey.startsWith("@")) return tr(p, maybeKey.substring(1));
        return maybeKey;
    }

    public List<String> resolveList(Player p, List<String> list) {
        if (list == null) return List.of();
        return list.stream().map(s -> resolve(p, s)).toList();
    }
}

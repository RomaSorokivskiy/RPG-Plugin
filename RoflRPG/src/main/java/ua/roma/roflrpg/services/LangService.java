package ua.roma.roflrpg.services;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ua.roma.roflrpg.util.Msg;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LangService {
    private final JavaPlugin plugin;

    private YamlConfiguration uk;
public LangService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
    ensure("lang/uk_UA.yml");
    File f = new File(plugin.getDataFolder(), "lang/uk_UA.yml");
    uk = YamlConfiguration.loadConfiguration(f);
    migrateUk(f, uk);
}

private void migrateUk(File file, YamlConfiguration yml) {
    // One-time migration: replace old bind hint actionbar with real HUD line.
    String key = "hud.actionbar";
    String v = yml.getString(key, "");
    String low = v == null ? "" : v.toLowerCase(Locale.ROOT);

    boolean looksLikeOldHint =
            low.contains("swap hands") ||
            low.contains("прив") ||
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
        plugin.getLogger().info("[Lang] Migrated uk_UA.yml hud.actionbar (removed old bind hint).");
    } catch (IOException ex) {
        plugin.getLogger().warning("[Lang] Failed to save migration for uk_UA.yml: " + ex.getMessage());
    }
}


    private void ensure(String resPath) {
        File f = new File(plugin.getDataFolder(), resPath);
        if (f.exists()) return;
        f.getParentFile().mkdirs();
        plugin.saveResource(resPath, false);
    }

    public String locale(Player p) {
        return "uk_UA";
    }

    private YamlConfiguration bundle(Player p) {
        return uk;
    }

    public String tr(Player p, String key) {
        if (key == null) return "";
        YamlConfiguration b = bundle(p);
        String v = b.getString(key);
        if (v != null) return v;
        // fallback to uk
        v = uk.getString(key);
        return v != null ? v : key;
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
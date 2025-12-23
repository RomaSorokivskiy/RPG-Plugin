package ua.roma.roflrpg;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ua.roma.roflrpg.api.RoflRPGApi;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.services.*;
import ua.roma.roflrpg.storage.SQLiteDataStore;
import ua.roma.roflrpg.ui.GuiListener;
import ua.roma.roflrpg.ui.GuiManager;
import ua.roma.roflrpg.ui.commands.*;

/**
 * Core RPG plugin.
 *
 * This plugin ...
 */
public final class RoflRPGPlugin extends JavaPlugin {

    private DefinitionRegistry defs;
    private SQLiteDataStore store;

    private LangService lang;
    private HudService hud;
    private RoflRPGApiImpl api;

    private ProfileService profiles;
    private StatsService stats;
    private ResourceService resources;
    private CinematicsService cinematics;
    private SkillService skills;
    private CosmeticsService cosmetics;
    private GuiManager gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Force-disable resources actionbar to avoid clashing with main HUD actionbar.
        // Old configs might have it enabled, which causes the 'Mana: ... Stam: ...' bar to overwrite the main HUD.
        if (getConfig().getBoolean("resources.actionbar", true)) {
            getConfig().set("resources.actionbar", false);
            saveConfig();
        }

        // --- Static data (YAML) ---
        lang = new LangService(this);
        lang.load();

        defs = new DefinitionRegistry(this);
        defs.loadAll();

        // --- Persistent storage ---
        store = new SQLiteDataStore(this);
        store.init();

        // --- Runtime services ---
        profiles = new ProfileService(this, store, defs);
        stats = new StatsService(this, defs, profiles);
        resources = new ResourceService(this, profiles);
        cinematics = new CinematicsService(this);

        // Public API (consumed by addons)
        api = new RoflRPGApiImpl();
        api.bind(profiles, stats, defs);
        getServer().getServicesManager().register(RoflRPGApi.class, api, this, ServicePriority.Normal);

        skills = new SkillService(this, defs, profiles, resources, cinematics, api);
        cosmetics = new CosmeticsService(this, defs, profiles);
        hud = new HudService(this, defs, profiles, skills, lang);
        gui = new GuiManager(this, defs, profiles, skills, lang);

        // --- Listeners ---
        getServer().getPluginManager().registerEvents(profiles, this);
        getServer().getPluginManager().registerEvents(skills, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this, gui, defs, profiles, stats), this);

        // --- Commands ---
        registerCommand("rpg", new RpgCommand(gui));
        registerCommand("race", new RaceCommand(gui));
        registerCommand("class", new ClassCommand(gui));
        registerCommand("talents", new TalentsCommand(gui));
        registerCommand("skills", new SkillsCommand(gui));
        registerCommand("rpgadmin", new AdminCommand(this, defs, profiles, stats, resources));

        // --- Start loops ---
        hud.start();
        resources.start();
        cosmetics.start();

        // Apply stats for online players on /reload
        getServer().getScheduler().runTask(this, () -> getServer().getOnlinePlayers().forEach(p -> {
            profiles.ensureLoaded(p);
            stats.applyAll(p);
            cosmetics.applyAura(p);
        }));

        getLogger().info("RoflRPG v" + getDescription().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        try {
            resources.stop();
            cosmetics.stop();
            profiles.flushAll();
            store.close();
        } catch (Exception ignored) {
        }
        getLogger().info("RoflRPG v" + getDescription().getVersion() + " disabled");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor exec) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) cmd.setExecutor(exec);
    }

    // ---- Small config helpers used across services ----

    public String uiPrefix() {
        return getConfig().getString("ui.prefix", "<gold>[RoflRPG]</gold> ");
    }

    public int xpToNext(int level) {
        double base = getConfig().getDouble("xp.baseToNext", 100.0);
        double growth = getConfig().getDouble("xp.growth", 1.25);
        return (int) Math.max(1, Math.round(base * Math.pow(growth, Math.max(0, level - 1))));
    }

    public int talentPointsPerLevel() {
        return getConfig().getInt("xp.talentPointsPerLevel", 1);
    }

    public boolean scaleEnabled() {
        return getConfig().getBoolean("scale.enabled", true);
    }

    public RoflRPGApi api() {
        return api;
    }

    public CinematicsService cinematics() {
        return cinematics;
    }
}

package ua.roma.roflrpgskills;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ua.roma.roflrpg.api.RoflRPGApi;
import ua.roma.roflrpgskills.builtin.BuiltinSkillHandlers;

public final class RoflRPGSkillsAddonPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        RoflRPGApi api = Bukkit.getServicesManager().load(RoflRPGApi.class);
        if (api == null) {
            getLogger().severe("RoflRPGApi not found. Is RoflRPG enabled?");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register handlers (legacy ids + namespaced aliases)
        BuiltinSkillHandlers.registerAll(api);

        getLogger().info("Registered RoflRPG skill handlers (addon)");
    }
}

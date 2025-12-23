package com.example.rofladdon;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;
import ua.roma.roflrpg.api.RoflRPGApi;

public final class RoflAddonExample extends JavaPlugin {

    @Override
    public void onEnable() {
        RoflRPGApi api = Bukkit.getServicesManager().load(RoflRPGApi.class);
        if (api == null) {
            getLogger().warning("RoflRPGApi not found (is RoflRPG enabled?)");
            return;
        }

        api.registerSkillHandler("example:blink", ctx -> {
            // Simple custom skill: blink forward + sound + particles
            ctx.dash(1.6, 0.05);
            ctx.particle("PORTAL", 30);
            ctx.sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        });

        getLogger().info("Registered custom skill handler: example:blink");
    }

    @Override
    public void onDisable() {
        RoflRPGApi api = Bukkit.getServicesManager().load(RoflRPGApi.class);
        if (api != null) api.unregisterSkillHandler("example:blink");
    }
}

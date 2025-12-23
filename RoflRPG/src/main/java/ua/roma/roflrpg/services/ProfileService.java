package ua.roma.roflrpg.services;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ua.roma.roflrpg.RoflRPGPlugin;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.model.PlayerProfile;
import ua.roma.roflrpg.storage.SQLiteDataStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileService implements Listener {
    private final RoflRPGPlugin plugin;
    private final SQLiteDataStore store;
    private final DefinitionRegistry defs;

    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public ProfileService(RoflRPGPlugin plugin, SQLiteDataStore store, DefinitionRegistry defs) {
        this.plugin = plugin;
        this.store = store;
        this.defs = defs;
    }

    public PlayerProfile get(Player p) {
        return cache.get(p.getUniqueId());
    }

    public PlayerProfile ensureLoaded(Player p) {
        return cache.computeIfAbsent(p.getUniqueId(), id -> {
            PlayerProfile prof = store.load(id, p.getName());
            if (!defs.hasRace(prof.raceId())) prof.raceId("human");
            if (!defs.hasClass(prof.classId())) prof.classId("guest");
            return prof;
        });
    }

    public void save(Player p) {
        PlayerProfile prof = cache.get(p.getUniqueId());
        if (prof != null) store.save(prof);
    }

    public void flushAll() {
        cache.values().forEach(store::save);
        cache.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerProfile prof = ensureLoaded(p);

        // ensure resources valid
        prof.maxMana(Math.max(100, prof.maxMana()));
        prof.maxStamina(Math.max(100, prof.maxStamina()));
        prof.mana(prof.mana());
        prof.stamina(prof.stamina());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        save(e.getPlayer());
    }
}

package ua.roma.roflrpg.services;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ua.roma.roflrpg.RoflRPGPlugin;
import ua.roma.roflrpg.defs.ResourceCost;
import ua.roma.roflrpg.defs.ResourceType;
import ua.roma.roflrpg.model.PlayerProfile;

/**
 * Mana/Stamina system.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Passive regeneration (server-driven timer)</li>
 *   <li>Spending resources for skills</li>
 * </ul>
 *
 * <p>UI is rendered by {@link HudService}. This class only mutates the stored values.
 */
public final class ResourceService {

    private final RoflRPGPlugin plugin;
    private final ProfileService profiles;

    private BukkitTask regenTask;

    public ResourceService(RoflRPGPlugin plugin, ProfileService profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
    }

    /**
     * Starts passive regeneration.
     *
     * <p>Configuration:
     * <ul>
     *   <li>resources.enabled (boolean)</li>
     *   <li>resources.tickPeriod (ticks)</li>
     *   <li>resources.manaRegenPerTick (double)</li>
     *   <li>resources.staminaRegenPerTick (double)</li>
     * </ul>
     */
    public void start() {
        if (!plugin.getConfig().getBoolean("resources.enabled", true)) return;
        stop();

        final int periodTicks = Math.max(1, plugin.getConfig().getInt("resources.tickPeriod", 20));
        final double manaRegen = plugin.getConfig().getDouble("resources.manaRegenPerTick", 2.0);
        final double staminaRegen = plugin.getConfig().getDouble("resources.staminaRegenPerTick", 2.5);

        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerProfile prof = profiles.ensureLoaded(p);
                regen(prof, manaRegen, staminaRegen);
            }
        }, periodTicks, periodTicks);
    }

    public void stop() {
        if (regenTask != null) regenTask.cancel();
        regenTask = null;
    }

    /**
     * Attempts to spend the given resource cost.
     *
     * @return true if the player has enough resources and the cost was applied.
     */
    public boolean trySpend(Player p, ResourceCost cost) {
        if (cost == null || cost.type() == ResourceType.NONE) return true;

        PlayerProfile prof = profiles.ensureLoaded(p);

        return switch (cost.type()) {
            case MANA -> {
                if (prof.mana() < cost.amount()) yield false;
                prof.mana(Math.max(0, prof.mana() - cost.amount()));
                yield true;
            }
            case STAMINA -> {
                if (prof.stamina() < cost.amount()) yield false;
                prof.stamina(Math.max(0, prof.stamina() - cost.amount()));
                yield true;
            }
            default -> true;
        };
    }

    private static void regen(PlayerProfile prof, double manaRegen, double staminaRegen) {
        prof.mana(prof.mana() + manaRegen);
        prof.stamina(prof.stamina() + staminaRegen);
    }
}

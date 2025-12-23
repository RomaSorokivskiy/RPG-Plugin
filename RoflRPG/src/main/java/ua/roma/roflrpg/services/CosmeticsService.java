package ua.roma.roflrpg.services;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ua.roma.roflrpg.RoflRPGPlugin;
import ua.roma.roflrpg.defs.AuraDef;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.defs.RaceDef;
import ua.roma.roflrpg.model.PlayerProfile;

import java.util.Locale;

public final class CosmeticsService {
    private final RoflRPGPlugin plugin;
    private final DefinitionRegistry defs;
    private final ProfileService profiles;

    private BukkitTask task;

    public CosmeticsService(RoflRPGPlugin plugin, DefinitionRegistry defs, ProfileService profiles) {
        this.plugin = plugin;
        this.defs = defs;
        this.profiles = profiles;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                applyAura(p);
            }
        }, 10L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public void applyAura(Player p) {
        PlayerProfile prof = profiles.ensureLoaded(p);
        RaceDef race = defs.race(prof.raceId());
        if (race == null || race.auraId() == null) return;
        AuraDef aura = defs.aura(race.auraId());
        if (aura == null) return;

        int period = Math.max(1, aura.periodTicks());
        if ((p.getTicksLived() % period) != 0) return;

        Particle particle;
        try { particle = Particle.valueOf(aura.particle().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return; }

        var loc = p.getLocation().add(0, 1.0, 0);

        try {
            switch (particle) {
                case BLOCK -> {
                    String bd = aura.blockData() != null ? aura.blockData() : "minecraft:stone";
                    BlockData data = Bukkit.createBlockData(bd);
                    p.getWorld().spawnParticle(particle, loc, 6, 0.25, 0.25, 0.25, 0.01, data);
                }
                case ITEM -> {
                    ItemStack item = new ItemStack(org.bukkit.Material.DIAMOND);
                    p.getWorld().spawnParticle(particle, loc, 4, 0.25, 0.25, 0.25, 0.01, item);
                }
                case DUST -> {
                    Particle.DustOptions opt = new Particle.DustOptions(parseColor(aura.color()), (float) aura.size());
                    p.getWorld().spawnParticle(particle, loc, 6, 0.25, 0.25, 0.25, 0.01, opt);
                }
                default -> p.getWorld().spawnParticle(particle, loc, 6, 0.25, 0.25, 0.25, 0.01);
            }
        } catch (Exception ignored) {}
    }

    private static Color parseColor(String s) {
        if (s == null || s.isBlank()) return Color.fromRGB(255, 255, 255);
        try {
            String[] parts = s.split(",");
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            return Color.fromRGB(clamp(r), clamp(g), clamp(b));
        } catch (Exception e) {
            return Color.fromRGB(255, 255, 255);
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}

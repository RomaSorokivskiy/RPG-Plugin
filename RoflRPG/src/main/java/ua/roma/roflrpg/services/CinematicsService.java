package ua.roma.roflrpg.services;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ua.roma.roflrpg.defs.SkillDef;

import java.util.*;

/**
 * Timeline-based cinematics engine.
 *
 * <p>Skills can define <code>data.timeline</code> in YAML as a list of steps.
 * Each step is a map with keys:
 * <ul>
 *   <li><b>phase</b>: cast / impact / expire</li>
 *   <li><b>at</b>: tick offset from the moment the phase is played</li>
 *   <li><b>type</b>: SOUND / PARTICLE / RING / SPIRAL / BEAM / PULSE / CAMERA_SHAKE / MESSAGE</li>
 *   <li><b>target</b> (optional): CASTER / TARGET / LOCATION</li>
 *   <li>type-specific parameters (sound, particle, radius, points, etc.)</li>
 * </ul>
 *
 * <p>This service is intentionally decoupled from gameplay logic.
 * SkillService calls <code>play(..., phase)</code> and the engine schedules all steps.
 */
public final class CinematicsService {

    private final JavaPlugin plugin;

    public CinematicsService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Plays cinematic timeline steps from SkillDef.data.timeline
     * Supported step fields:
     *  - phase: cast|impact|expire (default: cast)
     *  - at: ticks delay (default: 0)
     *  - type: SOUND|PARTICLE|RING|BEAM|SPIRAL|PULSE|TITLE|ACTIONBAR
     *  - target: CASTER|TARGET (default: CASTER; if TARGET but target==null -> caster)
     */
    public void play(Player caster, SkillDef skill, LivingEntity target, String phase) {
        if (skill == null) return;
        Object tlObj = skill.data() == null ? null : skill.data().get("timeline");
        if (!(tlObj instanceof List<?> list)) return;

        for (Object o : list) {
            if (!(o instanceof Map<?, ?> raw)) continue;

            Map<String, Object> step = new HashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) step.put(String.valueOf(e.getKey()), e.getValue());

            String stepPhase = str(step.getOrDefault("phase", "cast")).toLowerCase(Locale.ROOT);
            if (!stepPhase.equalsIgnoreCase(phase)) continue;

            int at = (int) Math.max(0, num(step.getOrDefault("at", 0)));
            new BukkitRunnable() {
                @Override public void run() {
                    runStep(caster, target, step);
                }
            }.runTaskLater(plugin, at);
        }
    }

    private void runStep(Player caster, LivingEntity target, Map<String, Object> step) {
        String type = str(step.getOrDefault("type", "NONE")).toUpperCase(Locale.ROOT);
        LivingEntity anchor = "TARGET".equalsIgnoreCase(str(step.getOrDefault("target", "CASTER"))) ? (target != null ? target : caster) : caster;

        switch (type) {
            case "SOUND" -> {
                Sound s = sound(step.get("sound"));
                float vol = (float) Math.max(0.0, num(step.getOrDefault("volume", 0.8)));
                float pitch = (float) Math.max(0.0, num(step.getOrDefault("pitch", 1.0)));
                if (s != null) anchor.getWorld().playSound(anchor.getLocation(), s, vol, pitch);
            }
            case "PARTICLE" -> {
                Particle p = particle(step.get("particle"));
                if (p == null) return;
                int count = (int) Math.max(1, num(step.getOrDefault("count", 12)));
                double spread = Math.max(0.0, num(step.getOrDefault("spread", 0.35)));
                double speed = Math.max(0.0, num(step.getOrDefault("speed", 0.01)));
                Location loc = anchor.getLocation().add(0, Math.max(0.0, num(step.getOrDefault("y", 1.0))), 0);
                anchor.getWorld().spawnParticle(p, loc, count, spread, spread, spread, speed);
            }
            case "RING" -> {
                Particle p = particle(step.get("particle"));
                if (p == null) p = Particle.CRIT;
                double radius = Math.max(0.2, num(step.getOrDefault("radius", 2.6)));
                int points = (int) Math.max(12, num(step.getOrDefault("points", 24)));
                double y = num(step.getOrDefault("y", 0.2));
                Location c = anchor.getLocation().add(0, y, 0);
                World w = anchor.getWorld();
                for (int i = 0; i < points; i++) {
                    double a = (Math.PI * 2.0) * (i / (double) points);
                    double x = Math.cos(a) * radius;
                    double z = Math.sin(a) * radius;
                    w.spawnParticle(p, c.clone().add(x, 0, z), 1, 0, 0, 0, 0);
                }
            }
            case "BEAM" -> {
                Particle p = particle(step.get("particle"));
                if (p == null) p = Particle.END_ROD;
                Location from = caster.getEyeLocation();
                Location to = (target != null ? target.getLocation().add(0, 1.0, 0) : caster.getLocation().add(caster.getLocation().getDirection().multiply(4)).add(0, 1.0, 0));
                int points = (int) Math.max(12, num(step.getOrDefault("points", 28)));
                beam(from, to, p, points);
            }
            case "SPIRAL" -> {
                Particle p = particle(step.get("particle"));
                // Paper 1.21.4+ removed legacy particle aliases like SPELL_WITCH.
                // Use the modern name.
                if (p == null) p = Particle.WITCH;
                double radius = Math.max(0.2, num(step.getOrDefault("radius", 1.2)));
                double height = Math.max(0.5, num(step.getOrDefault("height", 2.2)));
                int turns = (int) Math.max(1, num(step.getOrDefault("turns", 2)));
                int points = (int) Math.max(24, num(step.getOrDefault("points", 56)));
                Location base = anchor.getLocation().add(0, 0.1, 0);
                World w = anchor.getWorld();
                for (int i = 0; i < points; i++) {
                    double t = i / (double) points;
                    double a = (Math.PI * 2.0) * turns * t;
                    double y = height * t;
                    double x = Math.cos(a) * radius;
                    double z = Math.sin(a) * radius;
                    w.spawnParticle(p, base.clone().add(x, y, z), 1, 0, 0, 0, 0);
                }
            }
            case "PULSE" -> {
                            // Important: variables captured by BukkitRunnable must be effectively final.
                            // If we "fix up" the particle variable (e.g., null -> default) we must do it
                            // via a separate final variable.
                            Particle p0 = particle(step.get("particle"));
                            final Particle p = (p0 != null) ? p0 : Particle.ENCHANTED_HIT; // CRIT_MAGIC legacy -> ENCHANTED_HIT
                            final double start = Math.max(0.4, num(step.getOrDefault("startRadius", 1.0)));
                            final double end = Math.max(start, num(step.getOrDefault("endRadius", 4.0)));
                            final int pulses = (int) Math.max(1, num(step.getOrDefault("pulses", 3)));
                            final int every = (int) Math.max(1, num(step.getOrDefault("everyTicks", 6)));
                            final double y = num(step.getOrDefault("y", 0.2));
                            final int points = (int) Math.max(18, num(step.getOrDefault("points", 28)));

                            new BukkitRunnable() {
                                int i = 0;
                                @Override public void run() {
                                    if (i >= pulses || !caster.isOnline()) { cancel(); return; }
                                    double r = start + (end - start) * (i / (double) Math.max(1, pulses - 1));
                                    ring(anchor.getLocation().add(0, y, 0), p, r, points);
                                    i++;
                                }
                            }.runTaskTimer(plugin, 0L, every);
                        }case "TITLE" -> {
                String title = str(step.getOrDefault("title", ""));
                String subtitle = str(step.getOrDefault("subtitle", ""));
                int fadeIn = (int) Math.max(0, num(step.getOrDefault("fadeIn", 0)));
                int stay = (int) Math.max(0, num(step.getOrDefault("stay", 20)));
                int fadeOut = (int) Math.max(0, num(step.getOrDefault("fadeOut", 10)));
                caster.sendTitle(ChatColor.translateAlternateColorCodes('&', title), ChatColor.translateAlternateColorCodes('&', subtitle), fadeIn, stay, fadeOut);
            }
            case "ACTIONBAR" -> {
                String msg = str(step.getOrDefault("text", ""));
                caster.sendActionBar(ChatColor.translateAlternateColorCodes('&', msg));
            }
            default -> {}
        }
    }

    private void beam(Location from, Location to, Particle p, int points) {
        World w = from.getWorld();
        if (w == null) return;
        Vector dir = to.toVector().subtract(from.toVector());
        double len = dir.length();
        if (len <= 0.001) return;
        Vector step = dir.normalize().multiply(len / points);
        Location cur = from.clone();
        for (int i = 0; i < points; i++) {
            w.spawnParticle(p, cur, 1, 0, 0, 0, 0);
            cur.add(step);
        }
    }

    private void ring(Location c, Particle p, double radius, int points) {
        World w = c.getWorld();
        if (w == null) return;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0) * (i / (double) points);
            w.spawnParticle(p, c.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, 0, 0, 0, 0);
        }
    }

    private double num(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception ignored) { return 0.0; }
    }

    private String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private Particle particle(Object o) {
        if (o == null) return null;
        try { return Particle.valueOf(String.valueOf(o).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return null; }
    }

    private Sound sound(Object o) {
        if (o == null) return null;
        try { return Sound.valueOf(String.valueOf(o).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return null; }
    }
}

package ua.roma.roflrpgskills.builtin;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ua.roma.roflrpg.RoflRPGPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Small helper for visuals + simple combat math.
 *
 * This addon is aimed for Paper 1.21.x, but still avoids hard references to
 * enum constants that might not exist in some api jars (sounds/particles).
 */
public final class SkillFxUtil {
    private SkillFxUtil() {}

    // -------------------- Safe enum lookup --------------------

    public static Sound soundByName(String name, Sound fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Sound.valueOf(name); } catch (Exception ignored) { return fallback; }
    }

    public static Particle particleByName(String name, Particle fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Particle.valueOf(name); } catch (Exception ignored) { return fallback; }
    }

    /** Alias: some handlers use this name. */
    public static Particle particleOf(String name, Particle fallback) {
        return particleByName(name, fallback);
    }

    /** Alias: some handlers use this name. */
    public static Sound soundOf(String name, Sound fallback) {
        return soundByName(name, fallback);
    }

    public static void play(Player p, Sound s, float vol, float pitch) {
        if (p == null || s == null) return;
        try { p.playSound(p.getLocation(), s, vol, pitch); } catch (Exception ignored) {}
    }

    public static void playName(Player p, String soundName, float vol, float pitch, Sound fallback) {
        play(p, soundByName(soundName, fallback), vol, pitch);
    }

    /** Convenience overload: no explicit fallback. */
    public static void playName(Player p, String soundName, float vol, float pitch) {
        play(p, soundByName(soundName, null), vol, pitch);
    }

    // -------------------- Particles --------------------

    public static void particle(World w, Particle particle, Location at, int count,
                                double ox, double oy, double oz, double extra) {
        if (w == null || at == null || particle == null) return;
        try {
            w.spawnParticle(particle, at, count, ox, oy, oz, extra);
        } catch (Exception ignored) {}
    }

    public static void ring(Player p, Particle particle, double r, int points) {
        if (p == null || particle == null) return;
        ring(p.getWorld(), p.getLocation().clone().add(0, 0.1, 0), particle, r, points);
    }

    public static void ring(World w, Location center, Particle particle, double r, int points) {
        if (w == null || center == null || particle == null) return;
        for (int i = 0; i < points; i++) {
            double a = (i / (double) points) * Math.PI * 2;
            double x = Math.cos(a) * r;
            double z = Math.sin(a) * r;
            particle(w, particle, center.clone().add(x, 0, z), 1, 0, 0, 0, 0);
        }
    }

    public static void pulse(RoflRPGPlugin plugin, Player p, Particle particle, int pulses,
                             double start, double step, int intervalTicks) {
        new BukkitRunnable() {
            int n = 0;
            double r = start;
            @Override public void run() {
                if (p == null || !p.isOnline()) { cancel(); return; }
                ring(p, particle, r, 28);
                r += step;
                n++;
                if (n >= pulses) cancel();
            }
        }.runTaskTimer(plugin, 0, intervalTicks);
    }

    /** Spiral around a location (used by some class skills). */
    public static void spiral(RoflRPGPlugin plugin, Location base, Particle particle, int ticks, double radius) {
        spiral(plugin, base, particle, ticks, radius, 1.6 / Math.max(1, ticks));
    }

    /** Spiral around a location (used by some class skills). */
    public static void spiral(RoflRPGPlugin plugin, Location base, Particle particle, int ticks,
                              double radius, double heightStep) {
        if (plugin == null || base == null || particle == null) return;
        World w = base.getWorld();
        if (w == null) return;
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= ticks) { cancel(); return; }
                double a = t * 0.35;
                double y = t * heightStep;
                double x = Math.cos(a) * radius;
                double z = Math.sin(a) * radius;
                particle(w, particle, base.clone().add(x, y, z), 1, 0, 0, 0, 0);
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public static void spiral(RoflRPGPlugin plugin, Player p, Particle particle, int ticks, double radius) {
        if (p == null) return;
        spiral(plugin, p.getLocation().clone().add(0, 1.0, 0), particle, ticks, radius, 1.6 / Math.max(1, ticks));
    }

    public static void beam(Player p, Location to, Particle particle, int steps) {
        if (p == null || to == null || particle == null) return;
        beam(p.getWorld(), p.getEyeLocation().clone(), to, particle, steps);
    }

    public static void beam(World w, Location from, Location to, Particle particle, int steps) {
        if (w == null || from == null || to == null || particle == null) return;
        Vector dir = to.toVector().subtract(from.toVector());
        double len = dir.length();
        if (len < 0.001) return;
        dir.normalize();
        double step = len / Math.max(1, steps);

        Location cur = from.clone();
        for (int i = 0; i < steps; i++) {
            cur.add(dir.clone().multiply(step));
            particle(w, particle, cur, 1, 0, 0, 0, 0);
        }
    }

    // -------------------- Targets --------------------

    public static List<LivingEntity> areaTargets(Player caster, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        if (caster == null) return out;
        Location c = caster.getLocation();
        World w = caster.getWorld();
        if (w == null) return out;
        double r2 = radius * radius;
        for (LivingEntity le : w.getLivingEntities()) {
            if (le == caster) continue;
            if (!le.isValid() || le.isDead()) continue;
            if (le.getLocation().distanceSquared(c) <= r2) out.add(le);
        }
        return out;
    }

    public static LivingEntity rayTarget(Player caster, double range) {
        if (caster == null) return null;
        try {
            RayTraceResult res = caster.rayTraceEntities((int) Math.ceil(range));
            if (res == null) return null;
            if (res.getHitEntity() instanceof LivingEntity le) return le;
        } catch (Exception ignored) {}
        return null;
    }

    // -------------------- Combat helpers --------------------

    public static void damage(Player source, LivingEntity target, double amount) {
        if (target == null) return;
        try { target.damage(amount, source); } catch (Exception ignored) {}
    }

    public static void heal(Player p, double amount) {
        if (p == null) return;
        heal((LivingEntity) p, amount);
    }

    public static void heal(LivingEntity e, double amount) {
        if (e == null) return;
        double max = 20.0;
        try {
            // Paper 1.21.4+: Attribute is registry-backed and uses MAX_HEALTH.
            Attribute maxAttr = Attribute.MAX_HEALTH;
            var a = maxAttr != null ? e.getAttribute(maxAttr) : null;
            if (a != null) max = a.getValue();
        } catch (Exception ignored) {}
        double now = e.getHealth();
        e.setHealth(Math.min(max, now + amount));
    }

    /**
     * Some API versions renamed attributes. Resolve the max health attribute by name.
     */

    public static void knockback(Player source, LivingEntity target, double strength, double up) {
        if (source == null || target == null) return;
        Vector dir = target.getLocation().toVector().subtract(source.getLocation().toVector());
        if (dir.lengthSquared() < 0.0001) dir = source.getLocation().getDirection();
        dir.normalize().multiply(strength);
        dir.setY(Math.max(dir.getY(), up));
        try { target.setVelocity(target.getVelocity().add(dir)); } catch (Exception ignored) {}
    }

    public static void dash(Player p, double forward, double up) {
        if (p == null) return;
        Vector v = p.getLocation().getDirection().normalize().multiply(forward);
        v.setY(v.getY() + up);
        try { p.setVelocity(v); } catch (Exception ignored) {}
    }

    public static void addPotion(Player p, String potionType, int durationTicks, int amplifier) {
        addPotion((LivingEntity) p, potionType, durationTicks, amplifier);
    }

    public static void addPotion(LivingEntity e, String potionType, int durationTicks, int amplifier) {
        if (e == null || potionType == null) return;
        PotionEffectType type = potionTypeFromString(potionType);
        if (type == null) return;
        try { e.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, true, true)); } catch (Exception ignored) {}
    }

    private static PotionEffectType potionTypeFromString(String s) {
        if (s == null) return null;
        String key = s.trim().toUpperCase(Locale.ROOT);
        try {
            // Accept both SPEED and minecraft:speed
            if (key.contains(":")) key = key.substring(key.indexOf(':') + 1);
        } catch (Exception ignored) {}
        try {
            return PotionEffectType.getByName(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    // -------------------- Random --------------------

    public static int rnd(int minIncl, int maxIncl) {
        if (maxIncl < minIncl) { int t = minIncl; minIncl = maxIncl; maxIncl = t; }
        return ThreadLocalRandom.current().nextInt(minIncl, maxIncl + 1);
    }
}

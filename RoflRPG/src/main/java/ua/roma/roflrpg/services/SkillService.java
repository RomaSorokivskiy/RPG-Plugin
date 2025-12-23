package ua.roma.roflrpg.services;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ua.roma.roflrpg.RoflRPGPlugin;
import ua.roma.roflrpg.api.RoflRPGApi;
import ua.roma.roflrpg.api.SkillCastContext;
import ua.roma.roflrpg.api.SkillHandler;
import ua.roma.roflrpg.defs.ClassDef;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.defs.SkillDef;
import ua.roma.roflrpg.model.PlayerProfile;
import ua.roma.roflrpg.util.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Skill casting pipeline.
 *
 * <p>Flow:
 * <ol>
 *   <li>Input trigger (hotbar slot + trigger)</li>
 *   <li>Validate cooldown/GCD/level/cost</li>
 *   <li>Resolve a primary target (SELF/RAY) that is passed to addons</li>
 *   <li>Play cinematics timeline phases: cast -> impact -> expire</li>
 *   <li>Execute via a registered handler (addon) OR fall back to built-in effects</li>
 * </ol>
 *
 * <p>Handler ids:
 * <ul>
 *   <li><b>roflskills:effects</b> - handled by RoflRPGSkillsAddon</li>
 *   <li><b>effects</b> or empty - built-in legacy effect executor (kept for backwards compatibility)</li>
 * </ul>
 */
public final class SkillService implements Listener {

    private final RoflRPGPlugin plugin;
    private final DefinitionRegistry defs;
    private final ProfileService profiles;
    private final ResourceService resources;
    private final CinematicsService cinematics;
    private final RoflRPGApi api;

    // Cooldown state is kept in-memory; persistence is optional and can be implemented later.
    private final Map<UUID, Long> gcdUntil = new HashMap<>();
    private final Map<String, Map<UUID, Long>> cooldownUntil = new HashMap<>();

    public SkillService(RoflRPGPlugin plugin,
                        DefinitionRegistry defs,
                        ProfileService profiles,
                        ResourceService resources,
                        CinematicsService cinematics,
                        RoflRPGApi api) {
        this.plugin = plugin;
        this.defs = defs;
        this.profiles = profiles;
        this.resources = resources;
        this.cinematics = cinematics;
        this.api = api;
    }

    /**
     * Some servers want to disable certain input combos.
     * See config.yml: skills.enabledTriggers
     */
    public boolean isTriggerEnabled(SkillDef.Trigger t) {
        List<String> enabled = plugin.getConfig().getStringList("skills.enabledTriggers");
        if (enabled == null || enabled.isEmpty()) return true;
        for (String s : enabled) {
            if (s == null) continue;
            try {
                if (SkillDef.Trigger.valueOf(s.toUpperCase(Locale.ROOT)) == t) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public long cooldownLeftMs(Player p, String skillId) {
        long now = System.currentTimeMillis();
        Map<UUID, Long> map = cooldownUntil.get(skillId);
        if (map == null) return 0;
        long until = map.getOrDefault(p.getUniqueId(), 0L);
        return Math.max(0, until - now);
    }

    public long gcdLeftMs(Player p) {
        long now = System.currentTimeMillis();
        long until = gcdUntil.getOrDefault(p.getUniqueId(), 0L);
        return Math.max(0, until - now);
    }

    // ---------------------------------------------------------------------
    // Input bindings
    // ---------------------------------------------------------------------

    /**
     * Z trigger is bound to item swap. We cancel swapping if a skill was cast.
     */
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!isTriggerEnabled(SkillDef.Trigger.Z)) return;

        boolean casted = castByHotbarSlot(p, SkillDef.Trigger.Z);
        if (casted && plugin.getConfig().getBoolean("skills.z.cancelSwap", true)) {
            e.setCancelled(true);
        }
    }

    /**
     * Right/left click triggers.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK &&
                a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        boolean requireSprint = plugin.getConfig().getBoolean("skills.rightClick.requireSprint", false);

        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            if (p.isSprinting() && isTriggerEnabled(SkillDef.Trigger.CTRL_RIGHT_CLICK)) {
                castByHotbarSlot(p, SkillDef.Trigger.CTRL_RIGHT_CLICK);
            } else if (!requireSprint && isTriggerEnabled(SkillDef.Trigger.RIGHT_CLICK)) {
                castByHotbarSlot(p, SkillDef.Trigger.RIGHT_CLICK);
            }
        }

        if ((a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) && isTriggerEnabled(SkillDef.Trigger.LEFT_CLICK)) {
            castByHotbarSlot(p, SkillDef.Trigger.LEFT_CLICK);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        if (isTriggerEnabled(SkillDef.Trigger.SNEAK)) {
            castByHotbarSlot(e.getPlayer(), SkillDef.Trigger.SNEAK);
        }
    }

    /**
     * Hotbar slot picks the N-th skill with the same trigger in the current class.
     * Slot index must be within the list size (no wrap-around).
     */
    private boolean castByHotbarSlot(Player p, SkillDef.Trigger trigger) {
        PlayerProfile prof = profiles.ensureLoaded(p);
        ClassDef clazz = defs.clazz(prof.classId());
        if (clazz == null) return false;

        List<SkillDef> matching = new ArrayList<>();
        for (String sid : clazz.skillIds()) {
            SkillDef s = defs.skill(sid);
            if (s == null) continue;
            if (s.trigger() != trigger) continue;
            matching.add(s);
        }
        if (matching.isEmpty()) return false;

        int slot = p.getInventory().getHeldItemSlot();
        if (slot < 0 || slot >= matching.size()) return false;

        return cast(p, prof, matching.get(slot));
    }

    // ---------------------------------------------------------------------
    // Casting core
    // ---------------------------------------------------------------------

    private boolean cast(Player p, PlayerProfile prof, SkillDef s) {
        final long now = System.currentTimeMillis();

        if (isOnGcd(p, s, now)) return false;
        if (isOnCooldown(p, s, now)) return false;
        if (!meetsLevelRequirement(p, prof, s)) return false;
        if (!resources.trySpend(p, s.cost())) {
            Msg.send(p, plugin.uiPrefix(), "<red>Not enough " + s.cost().type().name().toLowerCase(Locale.ROOT) + ".</red>");
            return false;
        }

        applyCooldowns(p, s, now);

        // Resolve a primary target early (works for both built-in effects and addon handlers).
        SkillDef.Target targetSpec = (s.target() != null) ? s.target() : new SkillDef.Target(SkillDef.Target.Type.SELF, 0);
        LivingEntity primaryTarget = resolvePrimaryTarget(p, targetSpec);

        // Cinematics (timeline): cast phase.
        cinematics.play(p, s, primaryTarget, "cast");

        boolean handled = tryHandleByAddon(p, prof, s, primaryTarget);

        if (!handled) {
            executeBuiltinEffects(p, primaryTarget, targetSpec, s);
        }

        // Timeline phases after the main effect application.
        cinematics.play(p, s, primaryTarget, "impact");
        cinematics.play(p, s, primaryTarget, "expire");

        // Legacy fallback visuals/sound (timeline takes care of VFX/SFX).
        if (!hasTimeline(s)) {
            if (s.visuals() != null && s.visuals().particle() != null) {
                spawnParticleSafe(p, s.visuals().particle());
            }
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.2f);
        }

        return true;
    }

    private boolean isOnGcd(Player p, SkillDef s, long now) {
        // Global cooldown: if any previous skill started GCD, block all skills.
        Long gcd = gcdUntil.get(p.getUniqueId());
        return gcd != null && gcd > now;
    }

    private boolean isOnCooldown(Player p, SkillDef s, long now) {
        long until = cooldownUntil.computeIfAbsent(s.id(), k -> new HashMap<>())
                .getOrDefault(p.getUniqueId(), 0L);
        if (until <= now) return false;

        long left = Math.max(0, (until - now) / 1000);
        Msg.send(p, plugin.uiPrefix(), "<gray>Cooldown: " + left + "s</gray>");
        return true;
    }

    private boolean meetsLevelRequirement(Player p, PlayerProfile prof, SkillDef s) {
        if (prof.level() >= s.requiredLevel()) return true;
        Msg.send(p, plugin.uiPrefix(), "<red>Need level " + s.requiredLevel() + " for this skill.</red>");
        return false;
    }

    private void applyCooldowns(Player p, SkillDef s, long now) {
        long gcdMs = s.gcdTicks() * 50L;
        long cdMs = s.cooldownTicks() * 50L;

        if (gcdMs > 0) gcdUntil.put(p.getUniqueId(), now + gcdMs);
        if (cdMs > 0) cooldownUntil.computeIfAbsent(s.id(), k -> new HashMap<>()).put(p.getUniqueId(), now + cdMs);
    }

    private boolean tryHandleByAddon(Player caster, PlayerProfile prof, SkillDef s, LivingEntity primaryTarget) {
        String handlerId = s.handlerId();
        if (handlerId == null || handlerId.isBlank()) return false;

        // "effects" is a reserved built-in handler name (legacy configs).
        if ("effects".equalsIgnoreCase(handlerId)) return false;

        SkillHandler h = api.getSkillHandler(handlerId);
        if (h == null) {
            plugin.getLogger().warning("Unknown skill handler '" + handlerId + "' for skill '" + s.id() + "'. Falling back to built-in effects.");
            return false;
        }

        SkillCastContext ctx = new SkillCastContext(plugin, defs, profiles, resources, caster, prof, s, primaryTarget);
        h.cast(ctx);
        return true;
    }

    // ---------------------------------------------------------------------
    // Built-in (legacy) effects
    // ---------------------------------------------------------------------

    private void executeBuiltinEffects(Player caster, LivingEntity primaryTarget, SkillDef.Target targetSpec, SkillDef s) {
        if (s.effects() == null) return;

        for (Map<String, Object> eff : s.effects()) {
            String type = String.valueOf(eff.getOrDefault("type", "NONE")).toUpperCase(Locale.ROOT);
            switch (type) {
                case "HEAL", "HEAL_SELF" -> {
                    double amt = dbl(eff.getOrDefault("amount", 4.0));
                    heal(caster, amt);
                }
                case "HEAL_TARGET" -> {
                    double amt = dbl(eff.getOrDefault("amount", 4.0));
                    for (LivingEntity t : resolveTargets(caster, primaryTarget, targetSpec, true)) {
                        if (t == caster) heal(caster, amt);
                        else healEntity(t, amt);
                    }
                }
                case "DAMAGE" -> {
                    double amt = dbl(eff.getOrDefault("amount", 4.0));
                    for (LivingEntity t : resolveTargets(caster, primaryTarget, targetSpec, false)) {
                        if (t != caster) damage(caster, t, amt);
                    }
                }
                case "DAMAGE_RANDOM" -> {
                    int min = (int) Math.round(dbl(eff.getOrDefault("min", 2)));
                    int max = (int) Math.round(dbl(eff.getOrDefault("max", 6)));
                    if (max < min) {
                        int tmp = min;
                        min = max;
                        max = tmp;
                    }
                    double amt = ThreadLocalRandom.current().nextInt(min, max + 1);
                    for (LivingEntity t : resolveTargets(caster, primaryTarget, targetSpec, false)) {
                        if (t != caster) damage(caster, t, amt);
                    }
                }
                case "DASH" -> {
                    double str = dbl(eff.getOrDefault("strength", 1.0));
                    dash(caster, str);
                }
                case "POTION" -> {
                    for (LivingEntity t : resolveTargets(caster, primaryTarget, targetSpec, true)) {
                        applyPotion(t, eff);
                    }
                }
                case "KNOCKBACK" -> {
                    double strength = dbl(eff.getOrDefault("strength", 0.6));
                    for (LivingEntity t : resolveTargets(caster, primaryTarget, targetSpec, false)) {
                        if (t != caster) knockback(caster, t, strength);
                    }
                }
                case "CLEANSE" -> {
                    // Cleanse is intentionally limited to SELF/RAY in the base engine.
                    if (targetSpec.type() == SkillDef.Target.Type.RAY) {
                        if (primaryTarget != null) cleanse(primaryTarget);
                    } else {
                        cleanse(caster);
                    }
                }
                default -> {
                }
            }
        }
    }

    /**
     * Resolve primary target for addons/built-in effects.
     *
     * For CONE/AREA this returns null; those modes resolve their targets on demand.
     */
    private static LivingEntity resolvePrimaryTarget(Player caster, SkillDef.Target targetSpec) {
        return switch (targetSpec.type()) {
            case SELF -> caster;
            case RAY -> rayTraceTarget(caster, targetSpec.range());
            case CONE, AREA -> null;
        };
    }

    /**
     * Returns targets for built-in effects. Addons can implement their own targeting logic.
     *
     * @param includeSelf For some effects (heal/potion) SELF should be included depending on target mode.
     */
    private static List<LivingEntity> resolveTargets(Player caster,
                                                    LivingEntity primaryTarget,
                                                    SkillDef.Target targetSpec,
                                                    boolean includeSelf) {
        return switch (targetSpec.type()) {
            case SELF -> includeSelf ? List.of(caster) : List.of();
            case RAY -> {
                if (primaryTarget == null) yield List.of();
                if (includeSelf) yield List.of(primaryTarget);
                yield (primaryTarget == caster) ? List.of() : List.of(primaryTarget);
            }
            case CONE -> coneTargets(caster, targetSpec.range(), 70);
            case AREA -> areaTargets(caster, targetSpec.range());
        };
    }

    private static boolean hasTimeline(SkillDef s) {
        try {
            if (s == null || s.data() == null) return false;
            Object tl = s.data().get("timeline");
            if (tl instanceof List<?> list) return !list.isEmpty();
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Basic effect helpers
    // ---------------------------------------------------------------------

    private static void heal(Player p, double amt) {
        if (amt <= 0) return;
        var attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double max = attr.getValue();
        p.setHealth(Math.min(max, p.getHealth() + amt));
    }

    private static void healEntity(LivingEntity le, double amt) {
        if (amt <= 0) return;
        var attr = le.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double max = attr.getValue();
        le.setHealth(Math.min(max, le.getHealth() + amt));
    }

    private static void applyPotion(LivingEntity le, Map<String, Object> eff) {
        String raw = String.valueOf(eff.getOrDefault("effect", "SPEED"));
        PotionEffectType type = resolvePotionType(raw);
        if (type == null) return;

        int duration = (int) Math.round(dbl(eff.getOrDefault("durationTicks", 60)));
        int amplifier = (int) Math.round(dbl(eff.getOrDefault("amplifier", 0)));
        boolean ambient = Boolean.parseBoolean(String.valueOf(eff.getOrDefault("ambient", "false")));
        boolean particles = Boolean.parseBoolean(String.valueOf(eff.getOrDefault("particles", "true")));
        boolean icon = Boolean.parseBoolean(String.valueOf(eff.getOrDefault("icon", "true")));

        le.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
    }

    private static PotionEffectType resolvePotionType(String name) {
        if (name == null) return null;
        String n = name.trim().toUpperCase(Locale.ROOT);
        PotionEffectType direct = PotionEffectType.getByName(n);
        if (direct != null) return direct;

        // Map legacy names to modern enum ids.
        String alt = switch (n) {
            case "SLOW" -> "SLOWNESS";
            case "FAST_DIGGING" -> "HASTE";
            case "SLOW_DIGGING" -> "MINING_FATIGUE";
            case "INCREASE_DAMAGE" -> "STRENGTH";
            case "JUMP" -> "JUMP_BOOST";
            case "CONFUSION" -> "NAUSEA";
            case "DAMAGE_RESISTANCE" -> "RESISTANCE";
            case "HEAL" -> "INSTANT_HEALTH";
            case "HARM" -> "INSTANT_DAMAGE";
            default -> n;
        };
        return PotionEffectType.getByName(alt);
    }

    private static void cleanse(LivingEntity le) {
        String[] negativeNames = new String[]{
                "SLOW", "WEAKNESS", "POISON", "BLINDNESS", "NAUSEA", "WITHER", "HUNGER",
                "MINING_FATIGUE", "INSTANT_DAMAGE", "LEVITATION"
        };
        for (String n : negativeNames) {
            PotionEffectType t = PotionEffectType.getByName(n);
            if (t != null) le.removePotionEffect(t);
        }
    }

    private static void knockback(Player caster, LivingEntity target, double strength) {
        if (strength == 0) return;
        Vector dir = target.getLocation().toVector().subtract(caster.getLocation().toVector());
        if (dir.lengthSquared() < 0.0001) dir = caster.getLocation().getDirection();
        dir.normalize();
        target.setVelocity(target.getVelocity().add(dir.multiply(strength)));
    }

    private static void damage(Player caster, LivingEntity target, double amt) {
        target.damage(amt, caster);
    }

    private static void dash(Player p, double strength) {
        Vector dir = p.getLocation().getDirection().normalize().multiply(strength);
        dir.setY(Math.min(0.6, dir.getY() + 0.1));
        p.setVelocity(dir);
    }

    // ---------------------------------------------------------------------
    // Target helpers
    // ---------------------------------------------------------------------

    private static LivingEntity rayTraceTarget(Player p, double range) {
        var eye = p.getEyeLocation();
        var res = p.getWorld().rayTraceEntities(eye, eye.getDirection(), range, 0.25,
                e -> e != p && e instanceof LivingEntity);
        if (res == null || res.getHitEntity() == null) return null;
        return (LivingEntity) res.getHitEntity();
    }

    private static List<LivingEntity> areaTargets(Player p, double range) {
        List<LivingEntity> out = new ArrayList<>();
        for (var e : p.getNearbyEntities(range, range, range)) {
            if (e instanceof LivingEntity le && le != p) out.add(le);
        }
        return out;
    }

    private static List<LivingEntity> coneTargets(Player p, double range, double angleDeg) {
        Vector dir = p.getLocation().getDirection().normalize();
        double cos = Math.cos(Math.toRadians(angleDeg / 2.0));
        List<LivingEntity> out = new ArrayList<>();
        for (var e : p.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity le) || le == p) continue;
            Vector to = le.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();
            if (dir.dot(to) >= cos) out.add(le);
        }
        return out;
    }

    // ---------------------------------------------------------------------
    // Utils
    // ---------------------------------------------------------------------

    private static void spawnParticleSafe(Player p, String particleName) {
        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
            p.getWorld().spawnParticle(particle, p.getLocation().add(0, 1.0, 0), 8, 0.25, 0.25, 0.25, 0.01);
        } catch (Exception ignored) {
        }
    }

    private static double dbl(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}

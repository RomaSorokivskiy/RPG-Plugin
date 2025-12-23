package ua.roma.roflrpgskills;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import ua.roma.roflrpg.api.SkillCastContext;
import ua.roma.roflrpg.defs.SkillDef;
import ua.roma.roflrpgskills.builtin.SkillFxUtil;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Data-driven effects executor.
 *
 * Reads {@code effects: ...} from the skill definition and applies them based on the configured targeting.
 * This is intentionally compatible with the effects format used in RoflRPG's built-in skills.yml.
 */
public final class EffectsEngine {
    private EffectsEngine() {}

    public static void castByDef(SkillCastContext ctx) {
        SkillDef s = ctx.skill();
        List<Map<String, Object>> effects = s.effects();
        if (effects == null || effects.isEmpty()) return;

        Player caster = ctx.caster();
        LivingEntity rayTarget = ctx.target();

        SkillDef.Target target = s.target();
        SkillDef.Target.Type targetType = (target != null && target.type() != null) ? target.type() : SkillDef.Target.Type.SELF;
        double range = (target != null) ? target.range() : 0.0;

        for (Map<String, Object> e : effects) {
            String type = str(e.get("type")).toUpperCase(Locale.ROOT);
            switch (type) {
                case "HEAL" -> {
                    double amount = dbl(e.get("amount"));
                    SkillFxUtil.heal(caster, amount);
                }
                case "HEAL_SELF" -> {
                    double amount = dbl(e.get("amount"));
                    SkillFxUtil.heal(caster, amount);
                }
                case "HEAL_TARGET" -> {
                    double amount = dbl(e.get("amount"));
                    if (targetType == SkillDef.Target.Type.SELF) {
                        SkillFxUtil.heal(caster, amount);
                    } else if (targetType == SkillDef.Target.Type.RAY) {
                        if (rayTarget != null) {
                            SkillFxUtil.heal(rayTarget, amount);
                        } else {
                            // "miss" behavior from legacy engine: half heal on self
                            SkillFxUtil.heal(caster, amount / 2.0);
                        }
                    } else {
                        for (LivingEntity le : resolveMultiTargets(caster, targetType, range)) {
                            SkillFxUtil.heal(le, amount);
                        }
                    }
                }
                case "DAMAGE" -> {
                    double amount = dbl(e.get("amount"));
                    if (targetType == SkillDef.Target.Type.SELF) {
                        SkillFxUtil.damage(caster, caster, amount);
                    } else if (targetType == SkillDef.Target.Type.RAY) {
                        if (rayTarget != null) SkillFxUtil.damage(caster, rayTarget, amount);
                    } else {
                        for (LivingEntity le : resolveMultiTargets(caster, targetType, range)) {
                            SkillFxUtil.damage(caster, le, amount);
                        }
                    }
                }
                case "DAMAGE_RANDOM" -> {
                    double min = dbl(e.get("min"));
                    double max = dbl(e.get("max"));
                    double amount = min + (Math.max(0.0, max - min) * ThreadLocalRandom.current().nextDouble());

                    if (targetType == SkillDef.Target.Type.SELF) {
                        SkillFxUtil.damage(caster, caster, amount);
                    } else if (targetType == SkillDef.Target.Type.RAY) {
                        if (rayTarget != null) SkillFxUtil.damage(caster, rayTarget, amount);
                    } else {
                        for (LivingEntity le : resolveMultiTargets(caster, targetType, range)) {
                            SkillFxUtil.damage(caster, le, amount);
                        }
                    }
                }
                case "POTION" -> {
                    String effectName = str(firstNonNull(e.get("effect"), e.get("potion")));
                    int duration = intv(firstNonNull(e.get("durationTicks"), e.get("duration")));
                    int amplifier = intv(firstNonNull(e.get("amplifier"), 0));

                    String normalized = normalizePotionName(effectName);
                    if (normalized == null || duration <= 0) break;

                    if (targetType == SkillDef.Target.Type.SELF) {
                        SkillFxUtil.addPotion(caster, normalized, duration, amplifier);
                    } else if (targetType == SkillDef.Target.Type.RAY) {
                        if (rayTarget != null) SkillFxUtil.addPotion(rayTarget, normalized, duration, amplifier);
                    } else {
                        for (LivingEntity le : resolveMultiTargets(caster, targetType, range)) {
                            SkillFxUtil.addPotion(le, normalized, duration, amplifier);
                        }
                    }
                }
                case "KNOCKBACK" -> {
                    double strength = dbl(e.get("strength"));
                    if (strength <= 0) break;

                    if (targetType == SkillDef.Target.Type.RAY) {
                        if (rayTarget != null) SkillFxUtil.knockback(caster, rayTarget, strength, 0.12);
                    } else {
                        for (LivingEntity le : resolveMultiTargets(caster, targetType, range)) {
                            SkillFxUtil.knockback(caster, le, strength, 0.12);
                        }
                    }
                }
                case "DASH" -> {
                    double strength = dbl(e.get("strength"));
                    if (strength > 0) {
                        SkillFxUtil.dash(caster, strength, 0.10);
                    }
                }
                case "CLEANSE" -> {
                    if (targetType == SkillDef.Target.Type.SELF) {
                        cleanse(caster);
                    } else if (targetType == SkillDef.Target.Type.RAY) {
                        if (rayTarget != null) cleanse(rayTarget);
                    } else {
                        for (LivingEntity le : resolveMultiTargets(caster, targetType, range)) {
                            cleanse(le);
                        }
                    }
                }
                default -> {
                    // ignore unknown effect types (future-proof)
                }
            }
        }
    }

    private static List<LivingEntity> resolveMultiTargets(Player caster, SkillDef.Target.Type targetType, double range) {
        if (range <= 0) return List.of();

        return switch (targetType) {
            case CONE -> coneTargets(caster, range, 70.0);
            case AREA -> SkillFxUtil.areaTargets(caster, range);
            default -> List.of();
        };
    }

    private static List<LivingEntity> coneTargets(Player caster, double range, double halfAngleDeg) {
        // Similar to the legacy cone targeting in the base plugin.
        var origin = caster.getLocation();
        var dir = origin.getDirection();
        double maxAngleRad = Math.toRadians(halfAngleDeg);

        List<LivingEntity> out = new ArrayList<>();
        for (var e : caster.getWorld().getNearbyEntities(origin, range, range, range)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le == caster) continue;

            var to = le.getLocation().toVector().subtract(origin.toVector());
            if (to.lengthSquared() < 0.01) continue;

            double angle = dir.angle(to.normalize());
            if (angle <= maxAngleRad) out.add(le);
        }
        return out;
    }

    private static void cleanse(LivingEntity le) {
        for (PotionEffectType t : NEGATIVE_POTIONS) {
            if (le.hasPotionEffect(t)) le.removePotionEffect(t);
        }
    }

    private static final Set<PotionEffectType> NEGATIVE_POTIONS = Set.of(
            PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS,
            PotionEffectType.HUNGER,
            PotionEffectType.WEAKNESS,
            PotionEffectType.POISON,
            PotionEffectType.WITHER
    );

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static String normalizePotionName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String name = raw.trim().toUpperCase(Locale.ROOT);

        // Fast path
        if (PotionEffectType.getByName(name) != null) return name;

        // Legacy aliases used by many configs
        String mapped = switch (name) {
            case "SLOW" -> "SLOWNESS";
            case "FAST_DIGGING" -> "HASTE";
            case "SLOW_DIGGING" -> "MINING_FATIGUE";
            case "INCREASE_DAMAGE" -> "STRENGTH";
            case "DAMAGE_RESISTANCE" -> "RESISTANCE";
            case "JUMP" -> "JUMP_BOOST";
            case "CONFUSION" -> "NAUSEA";
            case "HEAL" -> "INSTANT_HEALTH";
            case "HARM" -> "INSTANT_DAMAGE";
            default -> name;
        };

        return PotionEffectType.getByName(mapped) != null ? mapped : null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int intv(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double dbl(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}

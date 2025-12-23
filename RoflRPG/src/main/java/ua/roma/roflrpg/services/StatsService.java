package ua.roma.roflrpg.services;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import ua.roma.roflrpg.RoflRPGPlugin;
import ua.roma.roflrpg.defs.ClassDef;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.defs.RaceDef;
import ua.roma.roflrpg.defs.TalentDef;
import ua.roma.roflrpg.model.PlayerProfile;
import ua.roma.roflrpg.model.StatKey;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Applies derived stats (race + class + talents) onto Bukkit attributes.
 *
 * Stat model:
 *  - addStats: flat additive values
 *  - multipliers: scalar additive values (ADD_SCALAR operation)
 */
public final class StatsService {

    private final RoflRPGPlugin plugin;
    private final DefinitionRegistry defs;
    private final ProfileService profiles;

    public StatsService(RoflRPGPlugin plugin, DefinitionRegistry defs, ProfileService profiles) {
        this.plugin = plugin;
        this.defs = defs;
        this.profiles = profiles;
    }

    /**
     * Recomputes and applies all derived stats for the given player.
     */
    public void applyAll(Player p) {
        PlayerProfile prof = profiles.ensureLoaded(p);

        RaceDef race = defs.race(prof.raceId());
        ClassDef clazz = defs.clazz(prof.classId());
        if (race == null || clazz == null) return;

        Map<StatKey, Double> add = new EnumMap<>(StatKey.class);
        Map<StatKey, Double> mult = new EnumMap<>(StatKey.class);

        merge(add, race.addStats());
        merge(mult, race.multipliers());
        merge(add, clazz.addStats());
        merge(mult, clazz.multipliers());

        // Talents: ranks stack.
        int maxMana = 100;
        int maxStamina = 100;

        for (var te : prof.talentRanks().entrySet()) {
            String tid = te.getKey();
            int rank = Math.max(0, te.getValue());
            if (rank <= 0) continue;

            TalentDef t = defs.talent(tid);
            if (t == null) continue;

            if (t.addStats() != null) {
                for (var e : t.addStats().entrySet()) {
                    add.merge(e.getKey(), e.getValue() * rank, Double::sum);
                }
            }

            if (t.multipliers() != null) {
                for (var e : t.multipliers().entrySet()) {
                    // Simple approximation: sum multipliers per rank.
                    mult.merge(e.getKey(), e.getValue() * rank, Double::sum);
                }
            }

            if (t.maxManaAdd() != null) maxMana += t.maxManaAdd() * rank;
            if (t.maxStaminaAdd() != null) maxStamina += t.maxStaminaAdd() * rank;
        }

        prof.maxMana(maxMana);
        prof.maxStamina(maxStamina);

        // --- Core attribute mapping ---
        // Paper 1.21.4+ switched Attribute from an enum with GENERIC_* constants
        // to a registry-backed interface with shorter names (MAX_HEALTH, etc.).
        apply(p, Attribute.MAX_HEALTH,
                new NamespacedKey(plugin, "roflrpg_hp_add"),
                add.getOrDefault(StatKey.MAX_HEALTH, 0.0),
                AttributeModifier.Operation.ADD_NUMBER);

        apply(p, Attribute.MOVEMENT_SPEED,
                new NamespacedKey(plugin, "roflrpg_speed_mul"),
                mult.getOrDefault(StatKey.MOVEMENT_SPEED, 0.0),
                AttributeModifier.Operation.ADD_SCALAR);

        apply(p, Attribute.ATTACK_DAMAGE,
                new NamespacedKey(plugin, "roflrpg_dmg_mul"),
                mult.getOrDefault(StatKey.ATTACK_DAMAGE, 0.0),
                AttributeModifier.Operation.ADD_SCALAR);

        apply(p, Attribute.ARMOR,
                new NamespacedKey(plugin, "roflrpg_armor_add"),
                add.getOrDefault(StatKey.ARMOR, 0.0),
                AttributeModifier.Operation.ADD_NUMBER);

        apply(p, Attribute.LUCK,
                new NamespacedKey(plugin, "roflrpg_luck_add"),
                add.getOrDefault(StatKey.LUCK, 0.0),
                AttributeModifier.Operation.ADD_NUMBER);

        // Optional additive parts.
        double speedAdd = add.getOrDefault(StatKey.MOVEMENT_SPEED, 0.0);
        if (speedAdd != 0) {
            apply(p, Attribute.MOVEMENT_SPEED,
                    new NamespacedKey(plugin, "roflrpg_speed_add"),
                    speedAdd,
                    AttributeModifier.Operation.ADD_NUMBER);
        } else {
            remove(p, Attribute.MOVEMENT_SPEED, new NamespacedKey(plugin, "roflrpg_speed_add"));
        }

        double dmgAdd = add.getOrDefault(StatKey.ATTACK_DAMAGE, 0.0);
        if (dmgAdd != 0) {
            apply(p, Attribute.ATTACK_DAMAGE,
                    new NamespacedKey(plugin, "roflrpg_dmg_add"),
                    dmgAdd,
                    AttributeModifier.Operation.ADD_NUMBER);
        } else {
            remove(p, Attribute.ATTACK_DAMAGE, new NamespacedKey(plugin, "roflrpg_dmg_add"));
        }

        // Scale attribute exists in 1.21+
        if (plugin.scaleEnabled()) {
            double sc = race.scale();
            apply(p, Attribute.SCALE,
                    new NamespacedKey(plugin, "roflrpg_scale"),
                    sc - 1.0,
                    AttributeModifier.Operation.ADD_SCALAR);
        } else {
            remove(p, Attribute.SCALE, new NamespacedKey(plugin, "roflrpg_scale"));
        }

        // Sync current health with new max.
        double maxHp = Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue();
        if (p.getHealth() > maxHp) p.setHealth(maxHp);
    }

    private static void merge(Map<StatKey, Double> into, Map<StatKey, Double> add) {
        if (add == null) return;
        for (var e : add.entrySet()) into.merge(e.getKey(), e.getValue(), Double::sum);
    }

    private static void apply(Player p, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;

        // Remove previous modifier with the same key (idempotent reapplies).
        // Bukkit returns an unmodifiable view for getModifiers(), so we remove via API.
        for (var m : java.util.List.copyOf(inst.getModifiers())) {
            if (key.equals(m.getKey())) inst.removeModifier(m);
        }

        if (amount == 0) return;
        inst.addModifier(new AttributeModifier(key, amount, op));
    }

    private static void remove(Player p, Attribute attr, NamespacedKey key) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        for (var m : java.util.List.copyOf(inst.getModifiers())) {
            if (key.equals(m.getKey())) inst.removeModifier(m);
        }
    }
}

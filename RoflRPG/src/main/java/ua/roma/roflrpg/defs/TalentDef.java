package ua.roma.roflrpg.defs;

import org.bukkit.Material;
import ua.roma.roflrpg.model.StatKey;

import java.util.List;
import java.util.Map;

public record TalentDef(
        String id,
        String nameMm,
        List<String> loreMm,
        Material icon,
        Map<StatKey, Double> addStats,
        Map<StatKey, Double> multipliers,
        /** Maximum rank for a normal (upgradable) talent. Default: 1. */
        Integer maxRank,
        /** Talent points needed per rank. Default: 1. */
        Integer pointsPerRank,
        Integer maxManaAdd,
        Integer maxStaminaAdd
) {}

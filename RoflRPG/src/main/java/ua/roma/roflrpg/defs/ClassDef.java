package ua.roma.roflrpg.defs;

import org.bukkit.Material;
import ua.roma.roflrpg.model.StatKey;

import java.util.List;
import java.util.Map;

public record ClassDef(
        String id,
        String nameMm,
        String role,
        List<String> loreMm,
        Material icon,
        Map<StatKey, Double> addStats,
        Map<StatKey, Double> multipliers,
        List<String> skillIds,
        List<String> talents
) {}

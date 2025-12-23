package ua.roma.roflrpg.defs;

import org.bukkit.Material;
import ua.roma.roflrpg.model.StatKey;

import java.util.List;
import java.util.Map;

public record RaceDef(
        String id,
        String nameMm,
        List<String> loreMm,
        Material icon,
        double scale,
        Map<StatKey, Double> addStats,
        Map<StatKey, Double> multipliers,
        String auraId
) {}

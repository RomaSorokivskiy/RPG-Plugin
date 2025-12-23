package ua.roma.roflrpg.defs;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record SkillDef(
        String id,
        String nameMm,
        Material icon,
        Trigger trigger,
        int cooldownTicks,
        int gcdTicks,
        ResourceCost cost,
        Target target,
        int requiredLevel,
        String handlerId,
        Map<String, Object> data,
        List<Map<String, Object>> effects,
        Visuals visuals
) {
    public enum Trigger { Z, RIGHT_CLICK, CTRL_RIGHT_CLICK, LEFT_CLICK, SNEAK }

    public record Target(Type type, double range) {
        public enum Type { SELF, RAY, CONE, AREA }
    }

    public record Visuals(String particle) {}
}

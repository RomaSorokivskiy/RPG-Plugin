package ua.roma.roflrpg.api;

@FunctionalInterface
public interface SkillHandler {
    void cast(SkillCastContext ctx);
}

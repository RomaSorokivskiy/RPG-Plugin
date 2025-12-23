package ua.roma.roflrpgskills.builtin;

import ua.roma.roflrpg.api.RoflRPGApi;
import ua.roma.roflrpgskills.EffectsEngine;

/**
 * Built-in handler registrations for this addon.
 *
 * Backward compatible:
 *  - legacy handler ids: effects, class_*
 *
 * Recommended:
 *  - namespaced ids: roflskills:effects, roflskills:class_*
 */
public final class BuiltinSkillHandlers {
    private static final String NS = "roflskills:";

    private BuiltinSkillHandlers() {}

    public static void registerAll(RoflRPGApi api) {
        // Data-driven engine
        register(api, "effects", EffectsEngine::castByDef);

        // Legacy class handler ids (old configs). All of them currently delegate to the same effects engine.
        register(api, "class_dev_freak", EffectsEngine::castByDef);
        register(api, "class_unix_crush", EffectsEngine::castByDef);
        register(api, "class_gacha_woman", EffectsEngine::castByDef);
        register(api, "class_antimag_mid", EffectsEngine::castByDef);
        register(api, "class_bmw_boy", EffectsEngine::castByDef);
        register(api, "class_egg_lover", EffectsEngine::castByDef);
        register(api, "class_shaybtron", EffectsEngine::castByDef);
        register(api, "class_hospital_man", EffectsEngine::castByDef);
        register(api, "class_furor", EffectsEngine::castByDef);
        register(api, "class_passatik", EffectsEngine::castByDef);
        register(api, "class_volynsky_rozlyv", EffectsEngine::castByDef);
        register(api, "class_micra_hatch", EffectsEngine::castByDef);
    }

    private static void register(RoflRPGApi api, String id, ua.roma.roflrpg.api.SkillHandler handler) {
        api.registerSkillHandler(id, handler);
        api.registerSkillHandler(NS + id, handler);
    }
}

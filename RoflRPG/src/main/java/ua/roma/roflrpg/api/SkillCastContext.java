package ua.roma.roflrpg.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import ua.roma.roflrpg.RoflRPGPlugin;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.defs.SkillDef;
import ua.roma.roflrpg.model.PlayerProfile;
import ua.roma.roflrpg.services.ProfileService;
import ua.roma.roflrpg.services.ResourceService;
import ua.roma.roflrpg.services.CinematicsService;

public final class SkillCastContext {
    private final RoflRPGPlugin plugin;
    private final DefinitionRegistry defs;
    private final ProfileService profiles;
    private final ResourceService resources;

    private final Player player;
    private final PlayerProfile profile;
    private final SkillDef skill;
    private final LivingEntity primaryTarget;

    public SkillCastContext(RoflRPGPlugin plugin,
                            DefinitionRegistry defs,
                            ProfileService profiles,
                            ResourceService resources,
                            Player player,
                            PlayerProfile profile,
                            SkillDef skill,
                            LivingEntity primaryTarget) {
        this.plugin = plugin;
        this.defs = defs;
        this.profiles = profiles;
        this.resources = resources;
        this.player = player;
        this.profile = profile;
        this.skill = skill;
        this.primaryTarget = primaryTarget;
    }

    public RoflRPGPlugin plugin() { return plugin; }

    public DefinitionRegistry defs() { return defs; }

    public ProfileService profiles() { return profiles; }

    public ResourceService resources() { return resources; }

    public Player player() { return player; }

    /** Alias для старих хендлерів, які викликають ctx.caster() */
    public Player caster() { return player; }

    public PlayerProfile profile() { return profile; }

    public SkillDef skill() { return skill; }

    public CinematicsService cinematics() { return plugin.cinematics(); }

    public void timeline(String phase) { plugin.cinematics().play(player, skill, primaryTarget, phase); }
    public void timeline(String phase, LivingEntity t) { plugin.cinematics().play(player, skill, t, phase); }

    public LivingEntity target() { return primaryTarget; }
}

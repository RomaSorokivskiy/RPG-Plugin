package ua.roma.roflrpg.services;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ua.roma.roflrpg.api.RoflRPGApi;
import ua.roma.roflrpg.api.SkillHandler;
import ua.roma.roflrpg.api.events.BranchUnlockEvent;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.defs.TalentDef;
import ua.roma.roflrpg.model.PlayerProfile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete implementation of the public API exposed by the core plugin.
 *
 * Notes:
 * - Skill handlers are registered by id and used by the skill system to resolve execution.
 * - Talents/branches are stored in the player profile; a separate plugin can unlock branches
 *   (e.g., "after killing a mob") by calling {@link #unlockBranch(Player, String)}.
 */
public final class RoflRPGApiImpl implements RoflRPGApi {

    private final Map<String, SkillHandler> skillHandlers = new ConcurrentHashMap<>();

    private ProfileService profiles;
    private StatsService stats;
    private DefinitionRegistry defs;

    public void bind(ProfileService profiles, StatsService stats, DefinitionRegistry defs) {
        this.profiles = profiles;
        this.stats = stats;
        this.defs = defs;
    }

    // ------------------------------------------------------------
    // Skill handlers
    // ------------------------------------------------------------

    @Override
    public void registerSkillHandler(String id, SkillHandler handler) {
        if (id == null || id.isBlank() || handler == null) return;
        skillHandlers.put(id, handler);
    }

    @Override
    public void unregisterSkillHandler(String id) {
        if (id == null || id.isBlank()) return;
        skillHandlers.remove(id);
    }

    @Override
    public SkillHandler getSkillHandler(String id) {
        if (id == null || id.isBlank()) return null;
        return skillHandlers.get(id);
    }

    // ------------------------------------------------------------
    // Talents
    // ------------------------------------------------------------

    @Override
    public int getTalentRank(Player player, String talentId) {
        if (player == null || talentId == null || talentId.isBlank()) return 0;
        return profile(player).talentRank(talentId);
    }

    @Override
    public boolean grantTalentRank(Player player, String talentId, int ranks) {
        if (player == null || talentId == null || talentId.isBlank()) return false;
        if (ranks <= 0) return false;

        TalentDef def = (defs == null) ? null : defs.talent(talentId);
        if (def == null) return false;

        PlayerProfile p = profile(player);

        final int maxRank = Math.max(0, def.maxRank());
        final int pointsPerRank = Math.max(0, def.pointsPerRank());
        if (maxRank == 0) return false;

        int current = p.talentRank(talentId);
        if (current >= maxRank) return false;

        int granted = 0;
        for (int i = 0; i < ranks; i++) {
            if (current + granted >= maxRank) break;
            if (pointsPerRank > 0 && p.talentPoints() < pointsPerRank) break;

            if (pointsPerRank > 0) {
                p.talentPoints(p.talentPoints() - pointsPerRank);
            }
            granted++;
        }

        if (granted <= 0) return false;

        p.setTalentRank(talentId, current + granted);

        // Persist + re-apply derived stats.
        if (profiles != null) profiles.save(player);
        if (stats != null) stats.applyAll(player);
        return true;
    }

    // ------------------------------------------------------------
    // Branch unlocks (prepared for a separate "special tree" plugin)
    // ------------------------------------------------------------

    @Override
    public boolean unlockBranch(Player player, String branchId) {
        if (player == null || branchId == null || branchId.isBlank()) return false;

        PlayerProfile p = profile(player);
        boolean changed = !p.hasBranch(branchId);
        if (changed) {
            p.unlockBranch(branchId);

            // Notify other plugins (e.g. quests, achievements)
            Bukkit.getPluginManager().callEvent(new BranchUnlockEvent(player, branchId));
        }

        if (changed && profiles != null) {
            profiles.save(player);
        }
        return changed;
    }

    @Override
    public boolean hasBranch(Player player, String branchId) {
        if (player == null || branchId == null || branchId.isBlank()) return false;
        return profile(player).hasBranch(branchId);
    }

    // ------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------

    private PlayerProfile profile(Player player) {
        if (profiles == null) {
            // Core plugin should bind before exposing API; keep it safe in case of misuse.
            throw new IllegalStateException("ProfileService not bound to API");
        }
        return profiles.ensureLoaded(player);
    }
}

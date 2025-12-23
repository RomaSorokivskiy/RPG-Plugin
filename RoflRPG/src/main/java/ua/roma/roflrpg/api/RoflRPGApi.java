package ua.roma.roflrpg.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Public API for addons.
 *
 * Addon plugins can obtain it via Bukkit ServicesManager:
 *   RoflRPGApi api = Bukkit.getServicesManager().load(RoflRPGApi.class);
 */
public interface RoflRPGApi {
    void registerSkillHandler(String id, SkillHandler handler);
    void unregisterSkillHandler(String id);

    @Nullable SkillHandler getSkillHandler(String id);

    /** Normal (upgradable) talents: returns current rank, 0 means locked. */
    int getTalentRank(Player player, String talentId);

    /** Grants +1 rank of a normal talent without spending points. */
    boolean grantTalentRank(Player player, String talentId, int delta);

    /**
     * Special branch unlocks for the future "kill-to-unlock" progression plugin.
     * This core plugin just stores the state and exposes it via the API.
     */
    boolean unlockBranch(Player player, String branchId);
    boolean hasBranch(Player player, String branchId);
}

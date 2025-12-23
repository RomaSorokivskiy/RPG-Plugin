package ua.roma.roflrpg.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player unlocks a special (future) branch.
 *
 * This is meant as a stable hook for a separate "branch" plugin.
 */
public final class BranchUnlockEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String branchId;

    public BranchUnlockEvent(@NotNull Player player, @NotNull String branchId) {
        this.player = player;
        this.branchId = branchId;
    }

    public @NotNull Player getPlayer() { return player; }
    public @NotNull String getBranchId() { return branchId; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

package ua.roma.roflrpg.ui.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ua.roma.roflrpg.RoflRPGPlugin;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.model.PlayerProfile;
import ua.roma.roflrpg.services.ProfileService;
import ua.roma.roflrpg.services.ResourceService;
import ua.roma.roflrpg.services.StatsService;
import ua.roma.roflrpg.util.Msg;

public final class AdminCommand implements CommandExecutor {
    private final RoflRPGPlugin plugin;
    private final DefinitionRegistry defs;
    private final ProfileService profiles;
    private final StatsService stats;
    private final ResourceService resources;

    public AdminCommand(RoflRPGPlugin plugin, DefinitionRegistry defs, ProfileService profiles, StatsService stats, ResourceService resources) {
        this.plugin = plugin;
        this.defs = defs;
        this.profiles = profiles;
        this.stats = stats;
        this.resources = resources;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Msg.send(sender, plugin.uiPrefix(), "<gray>Usage:</gray> /rpgadmin reload | givexp <p> <amt> | setlevel <p> <lvl> | respec <p>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                defs.loadAll();
                resources.start();
                Msg.send(sender, plugin.uiPrefix(), "<green>Reloaded.</green>");
            }
            case "givexp" -> {
                if (args.length < 3) return true;
                Player p = Bukkit.getPlayer(args[1]);
                if (p == null) return true;
                long amt = Long.parseLong(args[2]);
                PlayerProfile prof = profiles.ensureLoaded(p);
                prof.xp(prof.xp() + Math.max(0, amt));
                profiles.save(p);
                Msg.send(sender, plugin.uiPrefix(), "<green>XP added.</green>");
            }
            case "setlevel" -> {
                if (args.length < 3) return true;
                Player p = Bukkit.getPlayer(args[1]);
                if (p == null) return true;
                int lvl = Integer.parseInt(args[2]);
                PlayerProfile prof = profiles.ensureLoaded(p);
                prof.level(Math.max(1, lvl));
                prof.talentPoints((prof.level() - 1) * plugin.talentPointsPerLevel());
                profiles.save(p);
                stats.applyAll(p);
                Msg.send(sender, plugin.uiPrefix(), "<green>Level set.</green>");
            }
            case "respec" -> {
                if (args.length < 2) return true;
                Player p = Bukkit.getPlayer(args[1]);
                if (p == null) return true;
                PlayerProfile prof = profiles.ensureLoaded(p);
                prof.clearTalents();
                prof.talentPoints((prof.level() - 1) * plugin.talentPointsPerLevel());
                profiles.save(p);
                stats.applyAll(p);
                Msg.send(sender, plugin.uiPrefix(), "<green>Talents reset.</green>");
            }
        }
        return true;
    }
}

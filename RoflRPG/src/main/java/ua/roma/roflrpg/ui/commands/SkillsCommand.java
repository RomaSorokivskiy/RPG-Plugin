package ua.roma.roflrpg.ui.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ua.roma.roflrpg.ui.GuiManager;
import ua.roma.roflrpg.util.Msg;

public final class SkillsCommand implements CommandExecutor {
    private final GuiManager gui;

    public SkillsCommand(GuiManager gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "", "<red>Players only.</red>");
            return true;
        }
        gui.openSkills(p);
        return true;
    }
}

package ua.roma.roflrpg.ui.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ua.roma.roflrpg.ui.GuiManager;

public final class RpgCommand implements CommandExecutor {
    private final GuiManager gui;
    public RpgCommand(GuiManager gui) { this.gui = gui; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) gui.openMain(p);
        return true;
    }
}

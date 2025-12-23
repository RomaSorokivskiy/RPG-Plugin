package ua.roma.roflrpg.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    public static Component mm(String s) {
        return MM.deserialize(s == null ? "" : s);
    }

    public static void send(CommandSender to, String prefix, String messageMm) {
        to.sendMessage(mm(prefix + messageMm));
    }

    public static void send(Player to, String messageMm) {
        to.sendMessage(mm(messageMm));
    }

    public static void actionbar(Player p, String messageMm) {
        p.sendActionBar(mm(messageMm));
    }

    public static String fmt(String template, Map<String, String> vars) {
        if (template == null || vars == null || vars.isEmpty()) return template == null ? "" : template;
        String out = template;
        for (var e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }
}

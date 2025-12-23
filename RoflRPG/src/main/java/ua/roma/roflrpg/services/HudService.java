package ua.roma.roflrpg.services;

import org.bukkit.entity.Player;
import ua.roma.roflrpg.RoflRPGPlugin;
import ua.roma.roflrpg.defs.ClassDef;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.defs.SkillDef;
import ua.roma.roflrpg.model.PlayerProfile;
import ua.roma.roflrpg.util.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HudService {
    private final RoflRPGPlugin plugin;
    private final DefinitionRegistry defs;
    private final ProfileService profiles;
    private final SkillService skills;
    private final LangService lang;

    public HudService(RoflRPGPlugin plugin, DefinitionRegistry defs, ProfileService profiles, SkillService skills, LangService lang) {
        this.plugin = plugin;
        this.defs = defs;
        this.profiles = profiles;
        this.skills = skills;
        this.lang = lang;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("hud.enabled", true)) return;

        int period = Math.max(1, plugin.getConfig().getInt("hud.periodTicks", 10));
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("hud.actionbar", true)) return;

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                PlayerProfile prof = profiles.ensureLoaded(p);

                String raceName = "-";
                var race = defs.race(prof.raceId());
                if (race != null) raceName = stripTags(lang.resolve(p, race.nameMm()));

                String className = "-";
                var clazz = defs.clazz(prof.classId());
                if (clazz != null) className = stripTags(lang.resolve(p, clazz.nameMm()));

                // Cooldowns for CTRL+RMB skills in slots 1..9
                List<String> cds = new ArrayList<>();
                if (clazz != null) {
                    List<SkillDef> slotSkills = skillsByTrigger(clazz, SkillDef.Trigger.Z);
                    int slots = Math.max(1, plugin.getConfig().getInt("hud.slots", 9));
                    for (int i = 0; i < Math.min(slots, slotSkills.size()); i++) {
                        SkillDef s = slotSkills.get(i);
                        long left = skills.cooldownLeftMs(p, s.id());
                        if (left <= 0) {
                            cds.add("<green>" + (i + 1) + ":✓</green>");
                        } else {
                            long sec = Math.max(1, (left + 999) / 1000);
                            cds.add("<yellow>" + (i + 1) + ":" + sec + "s</yellow>");
                        }
                    }
                }

                String cdPart = cds.isEmpty() ? "" : (" <dark_gray>|</dark_gray> <gold>CD</gold> " + String.join(" ", cds));

                String line = lang.tr(p, "hud.actionbar", Map.of(
                        "level", String.valueOf(prof.level()),
                        "race", raceName,
                        "class", className,
                        "mana", String.valueOf((int) Math.round(prof.mana())),
                        "maxMana", String.valueOf((int) Math.round(prof.maxMana())),
                        "stamina", String.valueOf((int) Math.round(prof.stamina())),
                        "maxStamina", String.valueOf((int) Math.round(prof.maxStamina())),
                        "cd", cdPart
                ));

                Msg.actionbar(p, line);
            }
        }, 10L, period);
    }

    private List<SkillDef> skillsByTrigger(ClassDef clazz, SkillDef.Trigger trigger) {
        List<SkillDef> out = new ArrayList<>();
        for (String sid : clazz.skillIds()) {
            SkillDef s = defs.skill(sid);
            if (s != null && s.trigger() == trigger) out.add(s);
        }
        return out;
    }

    // quick remove MM tags for compact HUD labels
    private static String stripTags(String mm) {
        if (mm == null) return "";
        return mm.replaceAll("<[^>]+>", "").replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }
}

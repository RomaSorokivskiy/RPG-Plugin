package ua.roma.roflrpg.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ua.roma.roflrpg.RoflRPGPlugin;
import ua.roma.roflrpg.defs.ClassDef;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.defs.RaceDef;
import ua.roma.roflrpg.defs.TalentDef;
import ua.roma.roflrpg.model.PlayerProfile;
import ua.roma.roflrpg.services.ProfileService;
import ua.roma.roflrpg.services.StatsService;
import ua.roma.roflrpg.util.ItemUtil;
import ua.roma.roflrpg.util.Msg;

public final class GuiListener implements Listener {

    private final RoflRPGPlugin plugin;
    private final GuiManager gui;
    private final DefinitionRegistry defs;
    private final ProfileService profiles;
    private final StatsService stats;

    public GuiListener(RoflRPGPlugin plugin, GuiManager gui, DefinitionRegistry defs, ProfileService profiles, StatsService stats) {
        this.plugin = plugin;
        this.gui = gui;
        this.defs = defs;
        this.profiles = profiles;
        this.stats = stats;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType().isAir()) return;

        String guiTypeStr = ItemUtil.getStringTag(it, gui.KEY_GUI);
        if (guiTypeStr == null) return;

        e.setCancelled(true);

        GuiManager.GuiType type;
        try { type = GuiManager.GuiType.valueOf(guiTypeStr); }
        catch (Exception ex) { return; }

        String id = ItemUtil.getStringTag(it, gui.KEY_ID);
        PlayerProfile prof = profiles.ensureLoaded(p);

        switch (type) {
            case MAIN -> gui.openMain(p);
            case RACES -> {
                if (id == null) { gui.openRaces(p); return; }
                RaceDef r = defs.race(id);
                if (r == null) return;
                prof.raceId(id);
                profiles.save(p);
                stats.applyAll(p);
                Msg.send(p, plugin.uiPrefix(), "<green>Race selected.</green>");
                gui.openRaces(p);
            }
            case CLASSES -> {
                if (id == null) { gui.openClasses(p); return; }
                ClassDef c = defs.clazz(id);
                if (c == null) return;
                prof.classId(id);
                profiles.save(p);
                stats.applyAll(p);
                Msg.send(p, plugin.uiPrefix(), "<green>Class selected.</green>");
                gui.openClasses(p);
            }
            case TALENTS -> {
                if (id == null) { gui.openTalents(p); return; }
                TalentDef t = defs.talent(id);
                if (t == null) return;
                int rank = prof.talentRank(id);
                int maxRank = t.maxRank() == null ? 1 : Math.max(1, t.maxRank());
                if (rank >= maxRank) return;

                int cost = t.pointsPerRank() == null ? 1 : Math.max(1, t.pointsPerRank());
                if (prof.talentPoints() < cost) {
                    Msg.send(p, plugin.uiPrefix(), "<red>Not enough talent points.</red>");
                    return;
                }

                prof.setTalentRank(id, rank + 1);
                prof.talentPoints(prof.talentPoints() - cost);
                profiles.save(p);
                stats.applyAll(p);
                Msg.send(p, plugin.uiPrefix(), rank == 0 ? "<green>Talent unlocked.</green>" : "<green>Talent upgraded.</green>");
                gui.openTalents(p);
            }
            case SKILLS -> gui.openSkills(p);
        }
    }
}

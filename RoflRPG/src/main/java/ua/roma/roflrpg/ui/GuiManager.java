package ua.roma.roflrpg.ui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ua.roma.roflrpg.RoflRPGPlugin;
import ua.roma.roflrpg.defs.ClassDef;
import ua.roma.roflrpg.defs.DefinitionRegistry;
import ua.roma.roflrpg.defs.RaceDef;
import ua.roma.roflrpg.defs.SkillDef;
import ua.roma.roflrpg.defs.TalentDef;
import ua.roma.roflrpg.model.PlayerProfile;
import ua.roma.roflrpg.services.LangService;
import ua.roma.roflrpg.services.ProfileService;
import ua.roma.roflrpg.services.SkillService;
import ua.roma.roflrpg.util.ItemUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuiManager {

    public enum GuiType { MAIN, RACES, CLASSES, TALENTS, SKILLS }

    private final RoflRPGPlugin plugin;
    private final DefinitionRegistry defs;
    private final ProfileService profiles;
    private final SkillService skills;
    private final LangService lang;

    public final NamespacedKey KEY_GUI;
    public final NamespacedKey KEY_ID;

    public GuiManager(RoflRPGPlugin plugin, DefinitionRegistry defs, ProfileService profiles, SkillService skills, LangService lang) {
        this.plugin = plugin;
        this.defs = defs;
        this.profiles = profiles;
        this.skills = skills;
        this.lang = lang;
        this.KEY_GUI = new NamespacedKey(plugin, "gui");
        this.KEY_ID = new NamespacedKey(plugin, "id");
    }

    public void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, lang.tr(p, "gui.main.title"));

        inv.setItem(11, tagGui(ItemUtil.icon(Material.PLAYER_HEAD,
                lang.tr(p, "gui.main.race.name"),
                List.of(lang.tr(p, "gui.main.race.lore1"))
        ), GuiType.RACES, null));

        inv.setItem(13, tagGui(ItemUtil.icon(Material.IRON_SWORD,
                lang.tr(p, "gui.main.class.name"),
                List.of(lang.tr(p, "gui.main.class.lore1"))
        ), GuiType.CLASSES, null));

        inv.setItem(15, tagGui(ItemUtil.icon(Material.NETHER_STAR,
                lang.tr(p, "gui.main.skills.name"),
                List.of(lang.tr(p, "gui.main.skills.lore1"))
        ), GuiType.SKILLS, null));

        inv.setItem(22, tagGui(ItemUtil.icon(Material.ENCHANTED_BOOK,
                lang.tr(p, "gui.main.talents.name"),
                List.of(lang.tr(p, "gui.main.talents.lore1"))
        ), GuiType.TALENTS, null));

        p.openInventory(inv);
    }

    public void openRaces(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, lang.tr(p, "gui.races.title"));
        PlayerProfile prof = profiles.ensureLoaded(p);

        int slot = 0;
        for (RaceDef r : iterRaces()) {
            List<String> lore = new ArrayList<>();
            lore.addAll(lang.resolveList(p, r.loreMm()));
            lore.add("");
            lore.add("<gray>ID: " + r.id() + "</gray>");
            lore.add(prof.raceId().equalsIgnoreCase(r.id()) ? "<green>Selected</green>" : "<yellow>Click to select</yellow>");

            ItemStack it = ItemUtil.icon(r.icon(), lang.resolve(p, r.nameMm()), lore);
            inv.setItem(slot++, tagGui(it, GuiType.RACES, r.id()));
            if (slot >= inv.getSize()) break;
        }

        inv.setItem(49, tagGui(ItemUtil.icon(Material.BARRIER,
                lang.tr(p, "gui.back.name"),
                List.of(lang.tr(p, "gui.back.lore1"))
        ), GuiType.MAIN, null));

        p.openInventory(inv);
    }

    public void openClasses(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, lang.tr(p, "gui.classes.title"));
        PlayerProfile prof = profiles.ensureLoaded(p);

        int slot = 0;
        for (ClassDef c : iterClasses()) {
            List<String> lore = new ArrayList<>();
            lore.addAll(lang.resolveList(p, c.loreMm()));
            lore.add("");
            lore.add("<gray>Role: " + c.role() + "</gray>");
            lore.add("<gray>ID: " + c.id() + "</gray>");
            lore.add(prof.classId().equalsIgnoreCase(c.id()) ? "<green>Selected</green>" : "<yellow>Click to select</yellow>");

            ItemStack it = ItemUtil.icon(c.icon(), lang.resolve(p, c.nameMm()), lore);
            inv.setItem(slot++, tagGui(it, GuiType.CLASSES, c.id()));
            if (slot >= inv.getSize()) break;
        }

        inv.setItem(49, tagGui(ItemUtil.icon(Material.BARRIER,
                lang.tr(p, "gui.back.name"),
                List.of(lang.tr(p, "gui.back.lore1"))
        ), GuiType.MAIN, null));

        p.openInventory(inv);
    }

    public void openTalents(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, lang.tr(p, "gui.talents.title"));
        PlayerProfile prof = profiles.ensureLoaded(p);
        ClassDef clazz = defs.clazz(prof.classId());
        if (clazz == null) {
            openMain(p);
            return;
        }

        int slot = 0;
        for (String tid : clazz.talents()) {
            TalentDef t = defs.talent(tid);
            if (t == null) continue;

            List<String> lore = new ArrayList<>();
            lore.addAll(lang.resolveList(p, t.loreMm()));
            lore.add("");
            lore.add("<gray>ID: " + t.id() + "</gray>");

            int rank = prof.talentRank(t.id());
            int maxRank = (t.maxRank() == null ? 1 : Math.max(1, t.maxRank()));
            int cost = (t.pointsPerRank() == null ? 1 : Math.max(1, t.pointsPerRank()));

            lore.add("<gray>Rank: </gray><white>" + rank + "/" + maxRank + "</white>");
            if (rank <= 0) {
                lore.add("<yellow>Click to unlock</yellow>");
                lore.add("<gray>Cost: </gray><white>" + cost + "</white>");
            } else if (rank < maxRank) {
                lore.add("<yellow>Click to upgrade</yellow>");
                lore.add("<gray>Cost: </gray><white>" + cost + "</white>");
            } else {
                lore.add("<green>Maxed</green>");
            }
            lore.add("<gray>" + lang.tr(p, "gui.talents.points") + ": " + prof.talentPoints() + "</gray>");

            ItemStack it = ItemUtil.icon(t.icon(), lang.resolve(p, t.nameMm()), lore);
            inv.setItem(slot++, tagGui(it, GuiType.TALENTS, t.id()));
            if (slot >= inv.getSize()) break;
        }

        inv.setItem(49, tagGui(ItemUtil.icon(Material.BARRIER,
                lang.tr(p, "gui.back.name"),
                List.of(lang.tr(p, "gui.back.lore1"))
        ), GuiType.MAIN, null));

        p.openInventory(inv);
    }

    public void openSkills(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, lang.tr(p, "gui.skills.title"));
        PlayerProfile prof = profiles.ensureLoaded(p);
        ClassDef clazz = defs.clazz(prof.classId());
        if (clazz == null) {
            openMain(p);
            return;
        }

        // Top row 0..8: HOTBAR skills (Trigger.Z)
        List<SkillDef> hotbar = new ArrayList<>();
        for (String sid : clazz.skillIds()) {
            SkillDef s = defs.skill(sid);
            if (s != null && s.trigger() == SkillDef.Trigger.Z) hotbar.add(s);
        }

        for (int i = 0; i < 9; i++) {
            if (i >= hotbar.size()) {
                inv.setItem(i, ItemUtil.icon(Material.GRAY_STAINED_GLASS_PANE,
                        "<gray>" + (i + 1) + "</gray>",
                        List.of("<dark_gray>-</dark_gray>", "<gray>" + lang.tr(p, "gui.skills.emptySlot") + "</gray>")
                ));
                continue;
            }

            SkillDef s = hotbar.get(i);
            inv.setItem(i, skillIcon(p, s, i + 1));
        }

        // Rest skills below (everything except Trigger.Z)
        List<SkillDef> rest = new ArrayList<>();
        for (String sid : clazz.skillIds()) {
            SkillDef s = defs.skill(sid);
            if (s != null && s.trigger() != SkillDef.Trigger.Z) rest.add(s);
        }

        int base = 9; // start from second row
        for (int i = 0; i < rest.size() && (base + i) < 45; i++) {
            SkillDef s = rest.get(i);
            inv.setItem(base + i, skillIcon(p, s, -1));
        }

        inv.setItem(49, tagGui(ItemUtil.icon(Material.BARRIER,
                lang.tr(p, "gui.back.name"),
                List.of(lang.tr(p, "gui.back.lore1"))
        ), GuiType.MAIN, null));

        p.openInventory(inv);
    }

    private ItemStack skillIcon(Player p, SkillDef s, int slotNumberOrMinus1) {
        long leftMs = skills.cooldownLeftMs(p, s.id());
        long leftSec = leftMs <= 0 ? 0 : Math.max(1, (leftMs + 999) / 1000);

        List<String> lore = new ArrayList<>();
        if (slotNumberOrMinus1 > 0) {
            lore.add("<gray>" + lang.tr(p, "gui.skills.slot") + ": <yellow>" + slotNumberOrMinus1 + "</yellow></gray>");
        }
        lore.add("<gray>" + lang.tr(p, "gui.skills.requiredLevel") + ": <yellow>" + s.requiredLevel() + "</yellow></gray>");
        lore.add("<gray>" + lang.tr(p, "gui.skills.cooldown") + ": <white>" + ticksToSec(s.cooldownTicks()) + "s</white></gray>");
        lore.add("<gray>" + lang.tr(p, "gui.skills.gcd") + ": <white>" + ticksToSec(s.gcdTicks()) + "s</white></gray>");
        lore.add("<gray>" + lang.tr(p, "gui.skills.remaining") + ": " + (leftSec == 0 ? "<green>✓</green>" : "<yellow>" + leftSec + "s</yellow>") + "</gray>");
        lore.add("<gray>" + lang.tr(p, "gui.skills.cost") + ": <white>" + s.cost().amount() + " " + s.cost().type().name().toLowerCase(Locale.ROOT) + "</white></gray>");
        lore.add("<gray>" + lang.tr(p, "gui.skills.target") + ": <white>" + s.target().type().name() + "</white></gray>");
        lore.add("");
        lore.addAll(describeEffects(s));
        lore.add("");
        lore.add("<dark_gray>ID: " + s.id() + "</dark_gray>");
        if (s.handlerId() != null && !s.handlerId().equalsIgnoreCase("effects")) {
            lore.add("<dark_gray>Handler: " + s.handlerId() + "</dark_gray>");
        }

        return ItemUtil.icon(s.icon(), lang.resolve(p, s.nameMm()), lore);
    }

    private static int ticksToSec(int ticks) {
        return Math.max(0, (int) Math.round(ticks / 20.0));
    }

    private static List<String> describeEffects(SkillDef s) {
        List<String> out = new ArrayList<>();
        out.add("<gray><white>Effects</white></gray>");
        for (var eff : s.effects()) {
            String type = String.valueOf(eff.getOrDefault("type", "NONE")).toUpperCase(Locale.ROOT);
            switch (type) {
                case "HEAL", "HEAL_SELF" ->
                        out.add("<green>• Heal</green> <gray>+" + eff.getOrDefault("amount", "4") + "</gray>");
                case "HEAL_TARGET" ->
                        out.add("<green>• Heal target</green> <gray>+" + eff.getOrDefault("amount", "4") + "</gray>");
                case "DAMAGE" ->
                        out.add("<red>• Damage</red> <gray>-" + eff.getOrDefault("amount", "4") + "</gray>");
                case "DAMAGE_RANDOM" ->
                        out.add("<red>• Random damage</red> <gray>" + eff.getOrDefault("min", "2") + "…"
                                + eff.getOrDefault("max", "6") + "</gray>");
                case "DASH" ->
                        out.add("<aqua>• Dash</aqua> <gray>x" + eff.getOrDefault("strength", "1.0") + "</gray>");
                case "POTION" ->
                        out.add("<light_purple>• Effect</light_purple> <gray>" + eff.getOrDefault("effect", "SPEED")
                                + " " + eff.getOrDefault("amplifier", "0") + "</gray>");
                case "KNOCKBACK" ->
                        out.add("<yellow>• Knockback</yellow> <gray>x" + eff.getOrDefault("strength", "0.6") + "</gray>");
                case "CLEANSE" ->
                        out.add("<white>• Cleanse</white> <gray>negatives</gray>");
                default -> { /* ignore */ }
            }
        }
        return out;
    }

    private ItemStack tagGui(ItemStack it, GuiType gui, String idOrNull) {
        ItemUtil.withStringTag(it, KEY_GUI, gui.name());
        if (idOrNull != null) ItemUtil.withStringTag(it, KEY_ID, idOrNull);
        return it;
    }

    @SuppressWarnings("unchecked")
    private Iterable<RaceDef> iterRaces() {
        Object r = defs.races();
        if (r instanceof Map<?, ?> m) return (Iterable<RaceDef>) m.values();
        if (r instanceof Iterable<?> it) return (Iterable<RaceDef>) it;
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Iterable<ClassDef> iterClasses() {
        Object c = defs.classes();
        if (c instanceof Map<?, ?> m) return (Iterable<ClassDef>) m.values();
        if (c instanceof Iterable<?> it) return (Iterable<ClassDef>) it;
        return List.of();
    }
}

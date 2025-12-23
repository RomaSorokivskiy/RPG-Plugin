package ua.roma.roflrpg.defs;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ua.roma.roflrpg.model.StatKey;

import java.io.File;
import java.util.*;

public final class DefinitionRegistry {
    private final JavaPlugin plugin;

    private final Map<String, RaceDef> races = new LinkedHashMap<>();
    private final Map<String, ClassDef> classes = new LinkedHashMap<>();
    private final Map<String, SkillDef> skills = new LinkedHashMap<>();
    private final Map<String, TalentDef> talents = new LinkedHashMap<>();
    private final Map<String, AuraDef> auras = new LinkedHashMap<>();

    public DefinitionRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        ensure("races.yml");
        ensure("classes.yml");
        ensure("skills.yml");
        migrateSkillsCinematics();
        ensure("talents.yml");
        ensure("cosmetics.yml");

        loadRaces();
        loadClasses();
        loadSkills();
        loadTalents();
        loadCosmetics();
    }

    private void ensure(String res) {
        File f = new File(plugin.getDataFolder(), res);
        if (!f.exists()) plugin.saveResource(res, false);
    }

    /**
     * Auto-injects a default cinematic timeline into existing <code>skills.yml</code> entries.
     *
     * <p>Why it exists: many servers already have an older <code>plugins/RoflRPG/skills.yml</code>. Without this
     * migration you would need to delete the file to see new timeline effects.
     *
     * <p>Rules:
     * <ul>
     *   <li>If <code>data.timeline</code> already exists, we do nothing.</li>
     *   <li>Otherwise, we generate a small default timeline based on skill id prefix and target type.</li>
     * </ul>
     */
    private void migrateSkillsCinematics() {
        try {
            File f = new File(plugin.getDataFolder(), "skills.yml");
            if (!f.exists()) return;

            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection root = y.getConfigurationSection("skills");
            if (root == null) return;

            boolean changed = false;

            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;

                boolean skillChanged = false;

                // Migrate legacy handler id to the namespaced addon handler.
                // This keeps older servers working after switching to the addon-based EffectsEngine.
                String handler = String.valueOf(s.getString("handler", "")).trim();
                boolean hasEffects = s.isList("effects") || s.isConfigurationSection("effects") || s.contains("effects");
                if (handler.isEmpty()) {
                    // If the skill already uses YAML effects, prefer routing through the addon.
                    if (hasEffects) {
                        s.set("handler", "roflskills:effects");
                        skillChanged = true;
                    }
                } else if (handler.equalsIgnoreCase("effects")) {
                    s.set("handler", "roflskills:effects");
                    skillChanged = true;
                }

                // Auto-inject a timeline only if it doesn't exist yet.
                if (!s.isList("data.timeline")) {
                    String targetType = "SELF";
                    ConfigurationSection t = s.getConfigurationSection("target");
                    if (t != null) targetType = String.valueOf(t.getString("type", "SELF")).toUpperCase(Locale.ROOT);

                    List<Map<String, Object>> tl = defaultTimelineFor(id, targetType);
                    if (tl != null && !tl.isEmpty()) {
                        s.set("data.timeline", tl);
                        skillChanged = true;
                    }
                }

                if (skillChanged) changed = true;
            }

            if (changed) y.save(f);
        } catch (Exception ignored) {
        }
    }

    /**
     * Default timeline generator used only by {@link #migrateSkillsCinematics()}.
     *
     * <p>In this project we use a simple convention where skill ids are prefixed,
     * for example: <code>warrior_cleave</code> => prefix <code>warrior</code>.
     * That allows giving each "class" its own particle/sound palette.
     */
    private List<Map<String, Object>> defaultTimelineFor(String skillId, String targetType) {
        String prefix = skillId == null
                ? ""
                : (skillId.contains("_") ? skillId.substring(0, skillId.indexOf("_")) : skillId);

        Map<String, String> particleBy = Map.ofEntries(
                Map.entry("warrior", "CRIT"),
                Map.entry("mage", "FLAME"),
                Map.entry("rogue", "CLOUD")
        );

        Map<String, String> soundBy = Map.ofEntries(
                Map.entry("warrior", "ENTITY_PLAYER_ATTACK_SWEEP"),
                Map.entry("mage", "ENTITY_BLAZE_SHOOT"),
                Map.entry("rogue", "ENTITY_ENDERMAN_TELEPORT")
        );

        // Paper 1.21.4+ removed CRIT_MAGIC; ENCHANTED_HIT is the closest vanilla replacement.
        String particle = particleBy.getOrDefault(prefix, "ENCHANTED_HIT");
        String sound = soundBy.getOrDefault(prefix, "ENTITY_PLAYER_ATTACK_SWEEP");
        String impactTarget = ("RAY".equalsIgnoreCase(targetType) || "CONE".equalsIgnoreCase(targetType) || "AREA".equalsIgnoreCase(targetType))
                ? "TARGET"
                : "CASTER";

        List<Map<String, Object>> tl = new ArrayList<>();
        tl.add(step("cast", 0, "SOUND", Map.of("sound", sound, "volume", 0.8, "pitch", 1.2, "target", "CASTER")));
        tl.add(step("cast", 0, "SPIRAL", Map.of("particle", particle, "radius", 1.1, "height", 2.2, "turns", 2, "points", 56, "target", "CASTER")));
        tl.add(step("impact", 1, "RING", Map.of("particle", particle, "radius", 2.6, "points", 28, "y", 0.2, "target", impactTarget)));
        tl.add(step("impact", 1, "BEAM", Map.of("particle", "END_ROD", "points", 26, "target", impactTarget)));
        tl.add(step("expire", 12, "PULSE", Map.of("particle", particle, "startRadius", 1.0, "endRadius", 3.4, "pulses", 3, "everyTicks", 6, "points", 26, "target", impactTarget)));
        return tl;
    }

    private Map<String, Object> step(String phase, int at, String type, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phase", phase);
        m.put("at", at);
        m.put("type", type);
        if (extra != null) m.putAll(extra);
        return m;
    }

    public boolean hasRace(String id) { return races.containsKey(id); }
    public boolean hasClass(String id) { return classes.containsKey(id); }
    public RaceDef race(String id) { return races.get(id); }
    public ClassDef clazz(String id) { return classes.get(id); }
    public SkillDef skill(String id) { return skills.get(id); }
    public TalentDef talent(String id) { return talents.get(id); }
    public AuraDef aura(String id) { return auras.get(id); }

    public Collection<RaceDef> races() { return races.values(); }
    public Collection<ClassDef> classes() { return classes.values(); }
    public Collection<TalentDef> talents() { return talents.values(); }

    private void loadRaces() {
        races.clear();
        File f = new File(plugin.getDataFolder(), "races.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("races");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            String name = s.getString("name", id);
            List<String> lore = s.getStringList("lore");
            Material icon = material(s.getString("icon", "STONE"));
            double scale = s.getDouble("scale", 1.0);

            Map<StatKey, Double> add = readStats(s.getConfigurationSection("addStats"));
            Map<StatKey, Double> mult = readStats(s.getConfigurationSection("multipliers"));

            String aura = null;
            ConfigurationSection cos = s.getConfigurationSection("cosmetics");
            if (cos != null) aura = cos.getString("aura");

            races.put(id, new RaceDef(id, name, lore, icon, scale, add, mult, aura));
        }
    }

    private void loadClasses() {
        classes.clear();
        File f = new File(plugin.getDataFolder(), "classes.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("classes");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            String name = s.getString("name", id);
            String role = s.getString("role", "NONE");
            List<String> lore = s.getStringList("lore");
            Material icon = material(s.getString("icon", "STONE"));

            Map<StatKey, Double> add = readStats(s.getConfigurationSection("addStats"));
            Map<StatKey, Double> mult = readStats(s.getConfigurationSection("multipliers"));

            List<String> skillIds = s.getStringList("skills");
            List<String> talentIds = s.getStringList("talents");

            classes.put(id, new ClassDef(id, name, role, lore, icon, add, mult, skillIds, talentIds));
        }
    }

    @SuppressWarnings("unchecked")
    private void loadSkills() {
        skills.clear();
        File f = new File(plugin.getDataFolder(), "skills.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("skills");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            String name = s.getString("name", id);
            Material icon = material(s.getString("icon", "STICK"));

            SkillDef.Trigger trigger = SkillDef.Trigger.valueOf(s.getString("trigger", "RIGHT_CLICK").toUpperCase(Locale.ROOT));
            int cd = s.getInt("cooldownTicks", 60);
            int gcd = s.getInt("gcdTicks", 8);

            ResourceCost cost = ResourceCost.none();
            ConfigurationSection c = s.getConfigurationSection("cost");
            if (c != null) {
                ResourceType t = ResourceType.valueOf(c.getString("type", "NONE").toUpperCase(Locale.ROOT));
                double amt = c.getDouble("amount", 0);
                cost = new ResourceCost(t, amt);
            }

            SkillDef.Target target = new SkillDef.Target(SkillDef.Target.Type.SELF, 0);
            ConfigurationSection t = s.getConfigurationSection("target");
            if (t != null) {
                SkillDef.Target.Type tt = SkillDef.Target.Type.valueOf(t.getString("type", "SELF").toUpperCase(Locale.ROOT));
                double range = t.getDouble("range", 0);
                target = new SkillDef.Target(tt, range);
            }

            int reqLevel = Math.max(1, s.getInt("requiredLevel", 1));
            String handlerId = s.getString("handler", "effects");
            ConfigurationSection dataSec = s.getConfigurationSection("data");
            Map<String, Object> data = dataSec == null ? Map.of() : new LinkedHashMap<>(dataSec.getValues(true));

            List<Map<?, ?>> rawEffects = (List<Map<?, ?>>) s.getList("effects", List.of());
            List<Map<String, Object>> effects = new ArrayList<>();
            for (Map<?, ?> m : rawEffects) {
                Map<String, Object> clean = new HashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) clean.put(String.valueOf(e.getKey()), e.getValue());
                effects.add(clean);
            }

            String particle = null;
            ConfigurationSection v = s.getConfigurationSection("visuals");
            if (v != null) particle = v.getString("particle");
            SkillDef.Visuals visuals = new SkillDef.Visuals(particle);

            skills.put(id, new SkillDef(id, name, icon, trigger, cd, gcd, cost, target, reqLevel, handlerId, data, effects, visuals));
        }
    }

    private void loadTalents() {
        talents.clear();
        File f = new File(plugin.getDataFolder(), "talents.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("talents");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            String name = s.getString("name", id);
            Material icon = material(s.getString("icon", "PAPER"));
            List<String> lore = s.getStringList("lore");

            Map<StatKey, Double> add = readStats(s.getConfigurationSection("addStats"));
            Map<StatKey, Double> mult = readStats(s.getConfigurationSection("multipliers"));

            Integer maxManaAdd = null;
            Integer maxStaminaAdd = null;
            ConfigurationSection r = s.getConfigurationSection("resources");
            if (r != null) {
                if (r.contains("max_mana_add")) maxManaAdd = r.getInt("max_mana_add");
                if (r.contains("max_stamina_add")) maxStaminaAdd = r.getInt("max_stamina_add");
            }

            int maxRank = Math.max(1, s.getInt("max_rank", 1));
            int pointsPerRank = Math.max(1, s.getInt("points_per_rank", 1));

            talents.put(id, new TalentDef(id, name, lore, icon, add, mult, maxRank, pointsPerRank, maxManaAdd, maxStaminaAdd));
        }
    }

    private void loadCosmetics() {
        auras.clear();
        File f = new File(plugin.getDataFolder(), "cosmetics.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("auras");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            String type = s.getString("type", "AURA");
            String particle = s.getString("particle", "CLOUD");
            String color = s.getString("color");
            double size = s.getDouble("size", 1.0);
            int period = s.getInt("periodTicks", 10);
            String blockData = s.getString("blockData");

            auras.put(id, new AuraDef(id, type, particle, color, size, period, blockData));
        }
    }

    private static Material material(String name) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return Material.STONE; }
    }

    private static Map<StatKey, Double> readStats(ConfigurationSection s) {
        Map<StatKey, Double> out = new EnumMap<>(StatKey.class);
        if (s == null) return out;
        for (String key : s.getKeys(false)) {
            StatKey sk = mapStat(key);
            if (sk != null) out.put(sk, s.getDouble(key, 0.0));
        }
        return out;
    }

    private static StatKey mapStat(String k) {
        String key = k.toUpperCase(Locale.ROOT);
        return switch (key) {
            case "MAX_HEALTH", "HEALTH", "HP" -> StatKey.MAX_HEALTH;
            case "MOVEMENT_SPEED", "SPEED" -> StatKey.MOVEMENT_SPEED;
            case "ATTACK_DAMAGE", "DAMAGE" -> StatKey.ATTACK_DAMAGE;
            case "ARMOR" -> StatKey.ARMOR;
            case "LUCK" -> StatKey.LUCK;
            default -> null;
        };
    }
}

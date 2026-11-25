package at.dumphey.itemeditor.engine;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles the catalog once per reload into the engine's existing enchantment index. */
public final class EnchantCatalog {
    private static final String RESOURCE = "custom-enchantments.yml";
    private static final int MAX_LEVEL = 16;

    private final TriggerEngine engine;
    private final CatalogMining mining;
    private final CatalogWorkQueue work;
    private final CatalogEffects effects;
    private final CatalogCombat combat;
    private final VanillaEnchantAcquisition acquisition;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    EnchantCatalog(TriggerEngine engine) {
        this.engine = engine;
        this.work = new CatalogWorkQueue(engine.plugin());
        this.mining = new CatalogMining(engine);
        this.effects = new CatalogEffects(engine, work);
        this.combat = new CatalogCombat(engine, effects, work);
        this.acquisition = new VanillaEnchantAcquisition(engine, this);
    }

    boolean isSecondaryDamage() {
        return combat.isSecondaryDamage();
    }

    public long discardedWork() {
        return work.discarded();
    }

    void startAcquisition() {
        acquisition.start();
    }

    void stop() {
        acquisition.stop();
        work.stop();
        mining.stop();
        effects.stop();
        combat.stop();
    }

    List<Rule> load() {
        work.loadLimits();
        final File file = new File(engine.plugin().getDataFolder(), RESOURCE);
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (!file.exists()) {
                engine.plugin().saveResource(RESOURCE, false);
            }
            yaml.load(file);
            upgrade(file, yaml);
        } catch (Exception ex) {
            engine.addError(RESOURCE + ": " + ex.getMessage());
            entries.clear();
            return Collections.emptyList();
        }
        return compile(yaml);
    }

    private void upgrade(File file, YamlConfiguration yaml) {
        final ConfigurationSection userEntries = yaml.getConfigurationSection("enchantments");
        if (yaml.getInt("catalog-version") >= 4 || userEntries == null) {
            return;
        }
        final YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream stream = engine.plugin().getResource(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("missing bundled catalog");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                bundled.load(reader);
            }
            final ConfigurationSection defaults = bundled.getConfigurationSection("enchantments");
            final boolean missingEntries = yaml.getInt("catalog-version") < 3;
            for (String id : defaults.getKeys(false)) {
                if (!userEntries.contains(id)) {
                    if (missingEntries) userEntries.set(id, Cfg.plain(defaults.get(id)));
                    continue;
                }
                final ConfigurationSection configured = userEntries.getConfigurationSection(id);
                final ConfigurationSection original = defaults.getConfigurationSection(id);
                if (configured == null || original == null) continue;
                if (!configured.contains("rarity")) configured.set("rarity", original.get("rarity"));
                if (!configured.contains("level-rarities")) {
                    final ConfigurationSection configuredLevels = configured.getConfigurationSection("levels");
                    final ConfigurationSection defaultLevels = original.getConfigurationSection("level-rarities");
                    if (configuredLevels == null || defaultLevels == null) continue;
                    final Map<String, Object> applicable = new LinkedHashMap<>();
                    for (String level : defaultLevels.getKeys(false)) {
                        if (configuredLevels.contains(level)) applicable.put(level, defaultLevels.get(level));
                    }
                    if (!applicable.isEmpty()) configured.set("level-rarities", applicable);
                }
            }
            yaml.set("catalog-version", 4);
            final Map<String, Object> raw = Cfg.map(Cfg.plain(yaml));
            DefinitionFiles.write(file, raw);
        } catch (Exception ex) {
            engine.addError(RESOURCE + " upgrade: " + ex.getMessage());
        }
    }

    List<Rule> compile(ConfigurationSection yaml) {
        entries.clear();
        final List<Rule> rules = new ArrayList<>();
        final ConfigurationSection catalog = yaml.getConfigurationSection("enchantments");
        if (catalog == null) {
            engine.addError(RESOURCE + ": missing 'enchantments' section");
            return rules;
        }

        for (String id : catalog.getKeys(false)) {
            final ParseContext pc = new ParseContext(engine, RESOURCE).child(id);
            try {
                final Map<String, Object> map = Cfg.map(Cfg.plain(catalog.get(id)));
                if (!ItemTags.isValidId(id) || map == null) {
                    throw pc.error("expected a valid lowercase enchantment ID and a configuration section");
                }
                if (map.get("enabled") != null && !Cfg.bool(map.get("enabled"), pc.child("enabled"))) {
                    continue;
                }

                final String name = String.valueOf(map.getOrDefault("name", id));
                final Map<String, Object> levels = Cfg.map(map.remove("levels"));
                final Object rarityValue = map.remove("rarity");
                final EnchantRarity rarity = rarityValue == null ? EnchantRarity.COMMON
                        : EnchantRarity.parse(rarityValue, pc.child("rarity"));
                final Object levelRarityValue = map.remove("level-rarities");
                final Map<String, Object> levelRarities = Cfg.map(levelRarityValue);
                if (levelRarityValue != null && levelRarities == null) {
                    throw pc.child("level-rarities").error("expected a level-to-rarity map");
                }
                map.remove("name");
                final Object alternate = map.remove("alternate-trigger");
                if (levels == null || levels.isEmpty() || levels.size() > MAX_LEVEL) {
                    throw pc.child("levels").error("expected 1 to " + MAX_LEVEL + " consecutive levels");
                }
                if (map.containsKey("actions") || map.containsKey("item") || map.containsKey("enchant")
                        || Cfg.list(map.get("materials")).isEmpty()) {
                    throw pc.error("provide materials and actions inside levels; item/enchant bindings are automatic");
                }

                final RuleAction[] actions = new RuleAction[levels.size() + 1];
                final EnchantRarity[] rarityByLevel = new EnchantRarity[actions.length];
                if (levelRarities != null) {
                    for (String level : levelRarities.keySet()) {
                        if (!levels.containsKey(level)) {
                            throw pc.child("level-rarities").child(level).error("level is not defined");
                        }
                    }
                }
                map.put("enchant", id);
                map.put("actions", levels.get("1"));
                final Rule base = RuleLoader.compile(engine, "catalog." + id, RESOURCE, map);
                for (int level = 1; level < actions.length; level++) {
                    final ParseContext levelContext = pc.child("levels").child(String.valueOf(level));
                    final Object tier = levelRarities == null ? null : levelRarities.get(String.valueOf(level));
                    rarityByLevel[level] = tier == null ? rarity
                            : EnchantRarity.parse(tier, pc.child("level-rarities").child(String.valueOf(level)));
                    final RuleAction[] compiled = level == 1 ? base.actions()
                            : Actions.parseAll(levels.get(String.valueOf(level)), levelContext);
                    if (compiled.length == 0) {
                        throw levelContext.error("expected at least one action");
                    }
                    actions[level] = Actions.sequence(compiled);
                }
                Rule alternateRule = null;
                if (alternate != null) {
                    map.put("trigger", alternate);
                    alternateRule = RuleLoader.compile(engine, "catalog." + id + ".alternate", RESOURCE, map)
                            .withLevelActions(actions);
                }
                entries.put(id, new Entry(id, name, rarity, rarityByLevel, base));
                rules.add(base.withLevelActions(actions));
                if (alternateRule != null) {
                    rules.add(alternateRule);
                }
            } catch (ConfigException | RuntimeException ex) {
                engine.addError(pc.path() + ": " + ex.getMessage());
            }
        }
        return rules;
    }

    public Entry get(String id) {
        return entries.get(id);
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public static final class Entry {
        private final String id;
        private final String name;
        private final EnchantRarity rarity;
        private final EnchantRarity[] rarityByLevel;
        private final Rule rule;

        Entry(String id, String name, EnchantRarity rarity, EnchantRarity[] rarityByLevel, Rule rule) {
            this.id = id;
            this.name = name;
            this.rarity = rarity;
            this.rarityByLevel = rarityByLevel;
            this.rule = rule;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public int maxLevel() {
            return rarityByLevel.length - 1;
        }

        EnchantRarity rarity() {
            return rarity;
        }

        EnchantRarity rarity(int level) {
            return rarityByLevel[level];
        }

        public String rarityKey() {
            return rarity.key();
        }

        public String rarityKey(int level) {
            return rarity(level).key();
        }

        java.util.Set<Material> materials() {
            return rule.getMaterials();
        }

        public boolean accepts(Material material) {
            return rule.getMaterials().contains(material);
        }
    }

    public boolean apply(ItemStack item, String id, int level) {
        final Entry entry = get(id);
        if (item == null || level < 0 || (level > 0
                && (entry == null || level > entry.maxLevel() || !entry.accepts(item.getType())))) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        engine.tags().setEnchant(meta, id, level);
        return item.setItemMeta(meta);
    }
}

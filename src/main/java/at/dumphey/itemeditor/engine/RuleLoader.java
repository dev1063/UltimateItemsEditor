package at.dumphey.itemeditor.engine;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns rules/*.yml (and the YAML map of a single rule or skill) into {@link Rule}s, reporting every mistake with its path. */
final class RuleLoader {

    private static final String[] RULE_KEYS = {"enabled", "trigger", "item", "enchant", "materials", "slots", "cooldown",
            "cooldown-message", "run-when-cancelled", "interval", "conditions", "actions"};
    /** A skill belongs to its item: no item/enchant/materials of its own, but it may have a name and description. */
    private static final String[] SKILL_KEYS = {"enabled", "name", "description", "trigger", "slots", "cooldown",
            "cooldown-message", "run-when-cancelled", "interval", "conditions", "actions"};

    private final TriggerEngine engine;
    private int disabledCount;

    RuleLoader(TriggerEngine engine) {
        this.engine = engine;
    }

    int disabledCount() {
        return disabledCount;
    }

    List<Rule> loadAll(File folder) {
        final List<Rule> rules = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        final List<File> files = new ArrayList<>();
        collect(folder, files, 0);
        Collections.sort(files);
        final String base = folder.getParentFile() == null ? "" : folder.getParentFile().getPath();
        for (File file : files) {
            final String name = file.getPath().startsWith(base) ? file.getPath().substring(base.length()).replace('\\', '/') : file.getName();
            final String source = name.startsWith("/") ? name.substring(1) : name;
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            final ConfigurationSection section = yaml.getConfigurationSection("rules");
            if (section == null) {
                engine.addError(source + ": no 'rules:' section found (is the YAML valid?)");
                continue;
            }
            for (String id : section.getKeys(false)) {
                final String key = id.toLowerCase();
                final Map<String, Object> cfg = Cfg.map(Cfg.plain(section.get(id)));
                if (cfg == null) {
                    engine.addError(source + " > rules." + id + ": a rule must be a section with 'trigger' and 'actions'");
                    continue;
                }
                if (Boolean.FALSE.equals(cfg.get("enabled")) || "false".equalsIgnoreCase(String.valueOf(cfg.get("enabled")))) {
                    disabledCount++;
                    continue;
                }
                if (!ids.add(key)) {
                    engine.addError(source + " > rules." + id + ": the id '" + key + "' is already used by another rule");
                    continue;
                }
                try {
                    rules.add(compile(engine, key, source, cfg));
                } catch (ConfigException e) {
                    engine.addError(e.getMessage());
                } catch (RuntimeException e) {
                    engine.addError(source + " > rules." + id + ": internal error while reading the rule: " + e);
                }
            }
        }
        return rules;
    }

    private static void collect(File dir, List<File> out, int depth) {
        final File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (f.isDirectory() && depth < 3) {
                collect(f, out, depth + 1);
            } else if (f.isFile() && f.getName().toLowerCase().endsWith(".yml")) {
                out.add(f);
            }
        }
    }

    /** Compiles one rule from its (plain) YAML map. */
    static Rule compile(TriggerEngine engine, String id, String source, Map<String, Object> cfg) throws ConfigException {
        return compile(engine, id, source + " > rules." + id, source, cfg, null, null);
    }

    /**
     * @param path          where the rule is in its file, for error messages
     * @param boundItem     a skill: the custom item it belongs to (then the item/enchant/materials keys are not allowed)
     * @param boundMaterials a skill: the materials its item has (and has ever had)
     */
    static Rule compile(TriggerEngine engine, String id, String path, String source, Map<String, Object> cfg,
                        String boundItem, Set<Material> boundMaterials) throws ConfigException {
        final ParseContext pc = new ParseContext(engine, path);
        final boolean skill = boundItem != null;
        Cfg.checkKeys(cfg, pc, skill ? SKILL_KEYS : RULE_KEYS);

        if (cfg.get("trigger") == null) {
            throw pc.error("missing 'trigger' (one of: " + triggerNames() + ")");
        }
        final TriggerType trigger = TriggerType.parse(String.valueOf(cfg.get("trigger")));
        if (trigger == null) {
            throw pc.child("trigger").error("unknown trigger '" + cfg.get("trigger") + "' (one of: " + triggerNames() + ")");
        }

        String item = boundItem;
        String enchant = null;
        Set<Material> materials = boundMaterials;
        if (!skill) {
            item = idOf(cfg.get("item"), pc.child("item"));
            enchant = idOf(cfg.get("enchant"), pc.child("enchant"));
            if (item != null && enchant != null) {
                throw pc.error("use either 'item' or 'enchant', not both (add the other as a condition)");
            }
            if (cfg.get("materials") != null) {
                final EnumSet<Material> set = EnumSet.noneOf(Material.class);
                for (Object o : Cfg.list(cfg.get("materials"))) {
                    final Material m = Material.matchMaterial(String.valueOf(o));
                    if (m == null) {
                        throw pc.child("materials").error("unknown material '" + o + "'");
                    }
                    set.add(m);
                }
                if (set.isEmpty()) {
                    throw pc.child("materials").error("no material given");
                }
                materials = Collections.unmodifiableSet(set);
            }
        }

        int slots = trigger.isSubjectBased() ? Slots.HANDS : Slots.M_HAND;
        if (cfg.get("slots") != null) {
            slots = Slots.parse(cfg.get("slots"), pc.child("slots"));
            if (trigger.isSubjectBased() && (slots & Slots.HANDS) == 0) {
                throw pc.child("slots").error(trigger + " is about the item in the hand: use hand and/or off_hand");
            }
        }

        long cooldown = 0;
        if (cfg.get("cooldown") != null) {
            cooldown = TimeParser.parseMillis(cfg.get("cooldown"), 1000, pc.child("cooldown"));
        }
        Template cooldownMessage = null;
        if (cfg.get("cooldown-message") != null) {
            cooldownMessage = Template.compile(String.valueOf(cfg.get("cooldown-message")), true);
        }

        final boolean ignoreCancelled = cfg.get("run-when-cancelled") == null
                || !Cfg.bool(cfg.get("run-when-cancelled"), pc.child("run-when-cancelled"));

        int period = 1;
        if (cfg.get("interval") != null) {
            if (trigger != TriggerType.PERIODIC) {
                throw pc.child("interval").error("only valid with trigger PERIODIC");
            }
            final long ms = TimeParser.parseMillis(cfg.get("interval"), 1000, pc.child("interval"));
            period = (int) Math.max(1, Math.round(ms / (double) (engine.periodicBaseTicks() * TimeParser.TICK_MS)));
        }

        final Condition conditions = Conditions.parseAll(cfg.get("conditions"), pc.child("conditions"));

        if (cfg.get("actions") == null) {
            throw pc.error("missing 'actions'");
        }
        final RuleAction[] actions = Actions.parseAll(cfg.get("actions"), pc.child("actions"));
        if (actions.length == 0) {
            throw pc.child("actions").error("needs at least one action");
        }

        final String displayName = skill && cfg.get("name") != null ? String.valueOf(cfg.get("name")) : null;
        final String description = skill && cfg.get("description") != null ? String.valueOf(cfg.get("description")) : null;
        return new Rule(id, source, trigger, item, enchant, materials, slots, conditions, actions, cooldown,
                cooldownMessage, ignoreCancelled, period, displayName, description);
    }

    private static String idOf(Object value, ParseContext pc) throws ConfigException {
        if (value == null) {
            return null;
        }
        final String id = ItemTags.normalize(String.valueOf(value));
        if (!ItemTags.isValidId(id)) {
            throw pc.error("'" + value + "' is not a valid id (use a-z, 0-9, _ and -, up to 48 characters)");
        }
        return id;
    }

    private static String triggerNames() {
        final StringBuilder sb = new StringBuilder();
        for (TriggerType t : TriggerType.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(t.name());
        }
        return sb.toString();
    }
}

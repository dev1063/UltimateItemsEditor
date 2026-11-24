package at.dumphey.itemeditor.engine;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Custom items with skills. Each one is a file, {@code customitems/<id>.yml}: a copy of the item plus its skills.
 *
 * <p>A skill is a rule bound to the item's id, so it runs on the same engine (indexed by item id, errors isolated,
 * cooldowns per player) and costs nothing when the item is not involved. Skills are compiled with the materials the
 * item has and has ever had, which lets the engine skip every item of any other material without reading it.
 *
 * <p>Items already given are not stored with their skills: they only carry the id. Editing a skill therefore
 * applies to every existing copy, and deleting the item makes them ordinary items again.
 */
public final class CustomItemManager {

    private static final String[] KEYS = {"item", "materials", "skills"};
    private static final List<String> SKILL_OPTIONS = Collections.unmodifiableList(Arrays.asList("trigger", "slots",
            "cooldown", "cooldown-message", "run-when-cancelled", "interval", "enabled", "name", "description"));
    /** Options a command may remove with "none". */
    private static final List<String> REMOVABLE = Arrays.asList("slots", "cooldown", "cooldown-message",
            "run-when-cancelled", "interval", "enabled", "name", "description");

    private final TriggerEngine engine;
    private final JavaPlugin plugin;
    private final Map<String, CustomItem> byId = new TreeMap<>();
    private final List<String> errors = new ArrayList<>();
    private boolean enabled = true;

    CustomItemManager(TriggerEngine engine) {
        this.engine = engine;
        this.plugin = engine.plugin();
    }

    /** What compiling a file produced: the item (with every skill that is valid) and one error per broken skill. */
    private static final class Compiled {
        private final CustomItem item;
        private final List<String> errors = new ArrayList<>();
        private final Map<String, String> skillErrors = new LinkedHashMap<>();

        private Compiled(CustomItem item) {
            this.item = item;
        }
    }

    // ------------------------------------------------------------------ lifecycle

    void load() {
        shutdown();
        enabled = plugin.getConfig().getBoolean("customitems.enabled", true);
        if (!enabled) {
            return;
        }
        final File folder = folder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        final List<Rule> rules = new ArrayList<>();
        final File[] files = folder.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                final String name = file.getName();
                if (!file.isFile() || !name.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                    continue;
                }
                final String id = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
                if (!ItemTags.isValidId(id)) {
                    report("customitems/" + name + ": the file name is not a valid id (a-z, 0-9, _ and -)");
                    continue;
                }
                try {
                    final Compiled compiled = compile(id, DefinitionFiles.read(file));
                    for (String error : compiled.errors) {
                        report(error);
                    }
                    byId.put(id, compiled.item);
                    rules.addAll(compiled.item.getRules());
                } catch (ConfigException e) {
                    report(e.getMessage());
                } catch (RuntimeException e) {
                    report("customitems/" + name + ": internal error while reading the file: " + e);
                }
            }
        }
        if (engine.isEnabled()) {
            engine.addRules(rules);
        }
        plugin.getLogger().info("Custom items: " + byId.size() + " loaded with " + rules.size() + " skill(s), "
                + errors.size() + " error(s).");
    }

    void shutdown() {
        byId.clear();
        errors.clear();
    }

    private void report(String message) {
        errors.add(message);
        engine.addError(message);
    }

    // ------------------------------------------------------------------ queries

    public boolean isEnabled() {
        return enabled;
    }

    public CustomItem get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<CustomItem> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public static List<String> skillOptions() {
        return SKILL_OPTIONS;
    }

    // ------------------------------------------------------------------ management

    public CustomItem create(String id, ItemStack item) throws ConfigException, IOException {
        id = ItemTags.normalize(id);
        final ParseContext pc = new ParseContext(engine, "customitems/" + id + ".yml");
        if (!ItemTags.isValidId(id)) {
            throw pc.error("'" + id + "' is not a valid id (a-z, 0-9, _ and -)");
        }
        if (byId.containsKey(id)) {
            throw pc.error("already exists");
        }
        final ItemStack clean = clean(item, pc);
        final Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("item", DefinitionFiles.encode(clean));
        raw.put("materials", new ArrayList<Object>(Collections.singletonList(clean.getType().name())));
        raw.put("skills", new LinkedHashMap<String, Object>());
        return store(id, raw, null);
    }

    /** Replaces the item. Skills and already-given copies are kept; the old materials stay accepted. */
    public CustomItem update(String id, ItemStack item) throws ConfigException, IOException {
        final CustomItem current = require(id);
        final ParseContext pc = new ParseContext(engine, "customitems/" + current.getId() + ".yml");
        final ItemStack clean = clean(item, pc);
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        raw.put("item", DefinitionFiles.encode(clean));
        final List<Object> mats = new ArrayList<>(Cfg.list(raw.get("materials")));
        if (!mats.contains(clean.getType().name())) {
            mats.add(clean.getType().name());
        }
        raw.put("materials", mats);
        return store(current.getId(), raw, null);
    }

    public boolean delete(String id) throws IOException {
        final String clean = ItemTags.normalize(id);
        if (byId.remove(clean) == null) {
            return false;
        }
        Files.deleteIfExists(fileOf(clean).toPath());
        engine.removeRules(r -> sourceOf(clean).equals(r.getSource()));
        return true;
    }

    /** Adds a skill with its first action, e.g. addSkill("fire_sword", "ignite", "ATTACK", "ignite", "4s"). */
    public CustomItem addSkill(String itemId, String skillId, String trigger, String actionName, String actionValue)
            throws ConfigException, IOException {
        return addSkill(itemId, skillId, trigger, actionName, (Object) typed(actionValue));
    }

    public CustomItem addSkill(String itemId, String skillId, String trigger, String actionName, Object actionValue)
            throws ConfigException, IOException {
        final CustomItem current = require(itemId);
        final String skill = skillKey(skillId, current);
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        final Map<String, Object> skills = skillsOf(raw);
        if (skills.containsKey(skill)) {
            throw new ConfigException("the skill '" + skill + "' already exists (remove it or use /ie customitem skill action)");
        }
        final Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("trigger", trigger.toUpperCase(Locale.ROOT));
        cfg.put("actions", new ArrayList<Object>(Collections.singletonList(entry(actionName, actionValue))));
        skills.put(skill, cfg);
        return store(current.getId(), raw, skill);
    }

    public CustomItem removeSkill(String itemId, String skillId) throws ConfigException, IOException {
        final CustomItem current = require(itemId);
        final String skill = skillId.toLowerCase(Locale.ROOT);
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        if (skillsOf(raw).remove(skill) == null) {
            throw new ConfigException("skill '" + skillId + "' not found");
        }
        return store(current.getId(), raw, null);
    }

    /** Appends {@code name: value} to the skill's "actions" or "conditions". */
    public CustomItem addEntry(String itemId, String skillId, String list, String name, String value)
            throws ConfigException, IOException {
        return addEntry(itemId, skillId, list, name, (Object) typed(value));
    }

    public CustomItem addEntry(String itemId, String skillId, String list, String name, Object value)
            throws ConfigException, IOException {
        final CustomItem current = require(itemId);
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        final Map<String, Object> cfg = skillOf(raw, skillId);
        final String listKey = listName(list);
        final ParseContext context = new ParseContext(engine, sourceOf(current.getId())).child(skillId).child(listKey);
        if (listKey.equals("conditions")) {
            engine.parseCondition(name, value, context);
        } else {
            engine.parseAction(name, value, context);
        }
        final List<Object> entries = Cfg.list(cfg.get(listKey));
        entries.add(entry(name, value));
        cfg.put(listKey, entries);
        return store(current.getId(), raw, skillId.toLowerCase(Locale.ROOT));
    }

    /** Removes the entry number {@code number} (1-based). A skill must keep at least one action. */
    public CustomItem removeEntry(String itemId, String skillId, String list, int number)
            throws ConfigException, IOException {
        return removeEntry(itemId, skillId, list, number, false);
    }

    /** The GUI can keep an actionless skill as a disabled draft. */
    public CustomItem removeEntry(String itemId, String skillId, String list, int number, boolean keepDraft)
            throws ConfigException, IOException {
        final CustomItem current = require(itemId);
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        final Map<String, Object> cfg = skillOf(raw, skillId);
        final String listKey = listName(list);
        final List<Object> entries = Cfg.list(cfg.get(listKey));
        if (number < 1 || number > entries.size()) {
            throw new ConfigException("there is no " + listKey + " entry #" + number);
        }
        entries.remove(number - 1);
        if (entries.isEmpty()) {
            cfg.remove(listKey);
            if (keepDraft && listKey.equals("actions")) {
                cfg.put("enabled", false);
            }
        } else {
            cfg.put(listKey, entries);
        }
        return store(current.getId(), raw, skillId.toLowerCase(Locale.ROOT));
    }

    /** option: trigger, slots, cooldown, cooldown-message, run-when-cancelled, interval, enabled, name, description. */
    public CustomItem setSkillOption(String itemId, String skillId, String option, String value)
            throws ConfigException, IOException {
        final CustomItem current = require(itemId);
        final String opt = option.toLowerCase(Locale.ROOT);
        if (!SKILL_OPTIONS.contains(opt)) {
            throw new ConfigException("unknown option '" + option + "' (" + String.join(", ", SKILL_OPTIONS) + ")");
        }
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        final Map<String, Object> cfg = skillOf(raw, skillId);
        if (value.equalsIgnoreCase("none") && REMOVABLE.contains(opt)) {
            cfg.remove(opt);
        } else if (opt.equals("slots") && value.contains(",")) {
            final List<Object> slots = new ArrayList<>();
            for (String part : value.split(",")) {
                slots.add(part.trim());
            }
            cfg.put(opt, slots);
        } else if (opt.equals("trigger")) {
            cfg.put(opt, value.toUpperCase(Locale.ROOT));
        } else {
            cfg.put(opt, typed(value));
        }
        return store(current.getId(), raw, skillId.toLowerCase(Locale.ROOT));
    }

    /** Gives a player {@code amount} of the item, marked with its id. Returns how many were dropped (full inventory). */
    public int give(CustomItem item, Player target, int amount) {
        final ItemStack stack = item.template().clone();
        final ItemMeta meta = stack.getItemMeta();
        engine.tags().setItemId(meta, item.getId());
        stack.setItemMeta(meta);
        return Inventories.give(target, stack, amount);
    }

    // ------------------------------------------------------------------ internals

    private CustomItem require(String id) throws ConfigException {
        final CustomItem item = get(id);
        if (item == null) {
            throw new ConfigException("custom item '" + id + "' not found");
        }
        return item;
    }

    private String skillKey(String skillId, CustomItem item) throws ConfigException {
        final String skill = skillId.toLowerCase(Locale.ROOT);
        if (!ItemTags.isValidId(skill)) {
            throw new ConfigException("'" + skillId + "' is not a valid skill id (a-z, 0-9, _ and -)");
        }
        return skill;
    }

    private static String listName(String list) throws ConfigException {
        final String l = list.toLowerCase(Locale.ROOT);
        if (l.startsWith("action")) {
            return "actions";
        }
        if (l.startsWith("condition")) {
            return "conditions";
        }
        throw new ConfigException("expected 'actions' or 'conditions'");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> skillsOf(Map<String, Object> raw) {
        Object skills = raw.get("skills");
        if (!(skills instanceof Map)) {
            skills = new LinkedHashMap<String, Object>();
            raw.put("skills", skills);
        }
        return (Map<String, Object>) skills;
    }

    private static Map<String, Object> skillOf(Map<String, Object> raw, String skillId) throws ConfigException {
        final Map<String, Object> cfg = Cfg.map(skillsOf(raw).get(skillId.toLowerCase(Locale.ROOT)));
        if (cfg == null) {
            throw new ConfigException("skill '" + skillId + "' not found");
        }
        return cfg;
    }

    private static Map<String, Object> entry(String name, Object value) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(name.toLowerCase(Locale.ROOT), Cfg.plain(value));
        return entry;
    }

    /** true/false, whole numbers and decimals keep their type (as they would in the YAML); anything else is text. */
    static Object typed(String value) {
        final String v = value.trim();
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            return Boolean.valueOf(v.toLowerCase(Locale.ROOT));
        }
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException ignored) {
            // not an integer
        }
        try {
            return Double.valueOf(v);
        } catch (NumberFormatException ignored) {
            return v;
        }
    }

    /**
     * Validates and saves. With {@code strictSkill} the change is rejected when that skill does not compile, so a
     * command can never save a broken skill (other skills' problems do not block it). Nothing is written on failure.
     */
    private CustomItem store(String id, Map<String, Object> raw, String strictSkill) throws ConfigException, IOException {
        final Compiled compiled = compile(id, raw);
        if (strictSkill != null && compiled.skillErrors.containsKey(strictSkill)) {
            throw new ConfigException(compiled.skillErrors.get(strictSkill));
        }
        DefinitionFiles.write(fileOf(id), raw);
        byId.put(id, compiled.item);
        final String source = sourceOf(id);
        engine.removeRules(r -> source.equals(r.getSource()));
        if (engine.isEnabled()) {
            engine.addRules(compiled.item.getRules());
        }
        return compiled.item;
    }

    private Compiled compile(String id, Map<String, Object> raw) throws ConfigException {
        final String source = sourceOf(id);
        final ParseContext pc = new ParseContext(engine, source);
        Cfg.checkKeys(raw, pc, KEYS);

        if (raw.get("item") == null) {
            throw pc.error("missing 'item' (create custom items with /ie customitem create)");
        }
        final ItemStack template;
        try {
            template = DefinitionFiles.decode(String.valueOf(raw.get("item")));
        } catch (Exception e) {
            throw pc.child("item").error("the item data could not be read (" + e + ")");
        }
        if (template == null || template.getType().isAir()) {
            throw pc.child("item").error("the item is empty");
        }
        final EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        materials.add(template.getType());
        for (Object o : Cfg.list(raw.get("materials"))) {
            final Material m = Material.matchMaterial(String.valueOf(o));
            if (m == null) {
                throw pc.child("materials").error("unknown material '" + o + "'");
            }
            materials.add(m);
        }
        final Set<Material> accepted = Collections.unmodifiableSet(EnumSet.copyOf(materials));

        final Map<String, Object> skills = raw.get("skills") == null ? new LinkedHashMap<String, Object>() : Cfg.map(raw.get("skills"));
        if (skills == null) {
            throw pc.child("skills").error("must be a section: skill-id: { trigger: ..., actions: [...] }");
        }

        final List<Rule> rules = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final Map<String, String> skillErrors = new LinkedHashMap<>();
        final Set<String> seen = new java.util.HashSet<>();
        int disabled = 0;
        for (Map.Entry<String, Object> entry : skills.entrySet()) {
            final String skillId = entry.getKey().toLowerCase(Locale.ROOT);
            final String path = source + " > skills." + entry.getKey();
            String problem = null;
            if (!ItemTags.isValidId(skillId)) {
                problem = path + ": not a valid skill id (a-z, 0-9, _ and -, up to 48 characters)";
            } else if (!seen.add(skillId)) {
                problem = path + ": the skill id '" + skillId + "' is used twice";
            } else {
                final Map<String, Object> cfg = Cfg.map(entry.getValue());
                if (cfg == null) {
                    problem = path + ": a skill must be a section with 'trigger' and 'actions'";
                } else if (Boolean.FALSE.equals(cfg.get("enabled")) || "false".equalsIgnoreCase(String.valueOf(cfg.get("enabled")))) {
                    disabled++;
                    continue;
                } else {
                    try {
                        rules.add(RuleLoader.compile(engine, id + "." + skillId, path, source, cfg, id, accepted));
                    } catch (ConfigException e) {
                        problem = e.getMessage();
                    } catch (RuntimeException e) {
                        problem = path + ": internal error while reading the skill: " + e;
                    }
                }
            }
            if (problem != null) {
                errors.add(problem);
                skillErrors.put(skillId, problem);
            }
        }
        final Compiled compiled = new Compiled(new CustomItem(id, raw, template, materials, rules, disabled));
        compiled.errors.addAll(errors);
        compiled.skillErrors.putAll(skillErrors);
        return compiled;
    }

    private ItemStack clean(ItemStack item, ParseContext pc) throws ConfigException {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) {
            throw pc.error("hold an item in your main hand");
        }
        final ItemStack copy = item.clone();
        copy.setAmount(1);
        final ItemMeta meta = copy.getItemMeta();
        engine.tags().setItemId(meta, null); // the id is stamped when the item is given
        copy.setItemMeta(meta);
        return copy;
    }

    private File folder() {
        return new File(plugin.getDataFolder(), "customitems");
    }

    private File fileOf(String id) {
        return new File(folder(), id + ".yml");
    }

    private static String sourceOf(String id) {
        return "customitems/" + id + ".yml";
    }
}

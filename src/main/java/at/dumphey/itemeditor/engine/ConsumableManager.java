package at.dumphey.itemeditor.engine;

import at.dumphey.itemeditor.styler.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Consumable items: items that, when clicked, run console commands (and any other action) and are used up.
 *
 * <p>Each consumable is one file, {@code consumables/<id>.yml}, holding a copy of the item, the commands and the
 * options. Cost on the server:
 * <ul>
 *   <li>no consumables -> no listener at all; otherwise exactly one PlayerInteractEvent listener;</li>
 *   <li>a click is discarded with one EnumSet lookup on the item's material, before the item's meta is touched;</li>
 *   <li>only items of a consumable material have their persistent data read, one string;</li>
 *   <li>commands, conditions and actions are compiled when the files load.</li>
 * </ul>
 * The item is taken before actions run. A synchronous failure before any command dispatch refunds it.
 */
public final class ConsumableManager {

    private static final String COOLDOWN_PREFIX = "consumable:";
    private static final String[] KEYS = {"item", "materials", "click", "amount", "cooldown", "run-when-cancelled",
            "conditions", "commands", "actions"};
    private static final List<String> OPTIONS = Collections.unmodifiableList(
            Arrays.asList("click", "amount", "cooldown", "run-when-cancelled"));

    private final TriggerEngine engine;
    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final Map<String, Consumable> byId = new TreeMap<>();
    private final EnumSet<Material> materials = EnumSet.noneOf(Material.class);
    private final List<String> errors = new ArrayList<>();

    private Listener listener;
    private boolean enabled = true;
    private Supplier<LanguageManager> language = () -> null;

    ConsumableManager(TriggerEngine engine) {
        this.engine = engine;
        this.plugin = engine.plugin();
        this.key = new NamespacedKey(plugin, "consumable");
    }

    public void setLanguageSource(Supplier<LanguageManager> language) {
        this.language = language;
    }

    // ------------------------------------------------------------------ lifecycle

    void load() {
        shutdown();
        enabled = plugin.getConfig().getBoolean("consumables.enabled", true);
        if (!enabled) {
            return;
        }
        final File folder = folder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
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
                    errors.add("consumables/" + name + ": the file name is not a valid id (a-z, 0-9, _ and -)");
                    continue;
                }
                try {
                    byId.put(id, compile(id, DefinitionFiles.read(file)));
                } catch (ConfigException e) {
                    errors.add(e.getMessage());
                } catch (RuntimeException e) {
                    errors.add("consumables/" + name + ": internal error while reading the file: " + e);
                }
            }
        }
        rebuildMaterials();
        syncListener();
        plugin.getLogger().info("Consumables: " + byId.size() + " loaded, " + errors.size() + " error(s).");
        for (int i = 0; i < Math.min(errors.size(), 10); i++) {
            plugin.getLogger().warning("Consumable error: " + errors.get(i));
        }
    }

    void shutdown() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        byId.clear();
        materials.clear();
        errors.clear();
    }

    private void syncListener() {
        final boolean needed = enabled && !byId.isEmpty();
        if (needed && listener == null) {
            final Listener l = new Listener() {
            };
            Bukkit.getPluginManager().registerEvent(PlayerInteractEvent.class, l, EventPriority.HIGH, (x, event) -> {
                if (event instanceof PlayerInteractEvent) {
                    onInteract((PlayerInteractEvent) event);
                }
            }, plugin, false); // cancelled clicks are decided per consumable (run-when-cancelled)
            listener = l;
        } else if (!needed && listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    private void rebuildMaterials() {
        materials.clear();
        for (Consumable c : byId.values()) {
            materials.addAll(c.getMaterials());
        }
    }

    // ------------------------------------------------------------------ using an item

    private void onInteract(PlayerInteractEvent e) {
        final ItemStack item = e.getItem();
        // The overwhelmingly common case: an item that is not a consumable material. Nothing else is touched.
        if (item == null || !materials.contains(item.getType())) {
            return;
        }
        final String id = idOf(item);
        if (id == null) {
            return;
        }
        final Consumable consumable = byId.get(id);
        if (consumable == null) {
            return; // the consumable was deleted: this is an ordinary item now
        }
        final ClickKind kind = ClickKind.of(e.getAction());
        if (kind == null || (consumable.clickMask() & kind.bit()) == 0) {
            return; // another click (e.g. left click on a right-click item): leave the event alone
        }
        if (!consumable.isRunWhenCancelled() && TriggerContext.isInteractionCancelled(e)) {
            return; // a protection plugin said no
        }
        if (!consumable.isUsable()) {
            return;
        }
        use(e, consumable, kind);
    }

    private void use(PlayerInteractEvent e, Consumable c, ClickKind kind) {
        final Player player = e.getPlayer();
        final EquipmentSlot slot = e.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;

        final TriggerContext ctx = new TriggerContext(); // only built when a consumable is really used
        ctx.engine = engine;
        ctx.type = TriggerType.INTERACT;
        ctx.event = e;
        ctx.player = player;
        ctx.click = kind;
        ctx.block = e.getClickedBlock();
        ctx.item = e.getItem();
        ctx.slot = slot;
        ctx.itemId = c.getId();

        if (c.conditions() != null && !c.conditions().test(ctx)) {
            return;
        }

        e.setCancelled(true); // A matching activation owns the click, including cooldown and amount checks.
        final UUID uuid = player.getUniqueId();
        final String cooldownKey = COOLDOWN_PREFIX + c.getId();
        final ItemStack stack = player.getInventory().getItem(slot);
        if (stack == null || !c.getId().equals(idOf(stack))) {
            return; // the item changed under us (another plugin): never consume something else
        }
        if (stack.getAmount() < c.getAmount()) {
            send(player, "consumable-not-enough", "%amount%", String.valueOf(c.getAmount()));
            return;
        }

        if (c.getCooldownMs() > 0 && !engine.cooldowns().tryUse(uuid, cooldownKey, c.getCooldownMs())) {
            final long remaining = engine.cooldowns().remaining(uuid, cooldownKey);
            send(player, "consumable-cooldown", "%seconds%", String.format(Locale.ROOT, "%.1f", remaining / 1000.0));
            return;
        }

        // Take the item first: whatever the actions do (or fail to do), it can never be used twice.
        final int left = stack.getAmount() - c.getAmount();
        if (left <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            stack.setAmount(left);
        }

        try {
            c.action().execute(ctx);
        } catch (RuntimeException ex) {
            final boolean refund = ctx.commandsDispatched == 0;
            if (refund) {
                Inventories.give(player, stack, c.getAmount());
                engine.cooldowns().set(uuid, cooldownKey, 0);
            }
            send(player, refund ? "consumable-failed" : "consumable-partial-failure");
            plugin.getLogger().log(Level.WARNING, "Consumable '" + c.getId() + "' failed for " + player.getName()
                    + (refund ? "; the item was returned" : "; a command had already been dispatched"), ex);
        }
    }

    private void send(Player player, String messageKey, String... pairs) {
        final LanguageManager lang = language.get();
        if (lang != null) {
            player.sendMessage(lang.prefixed(messageKey, pairs));
        }
    }

    /** The consumable id an item carries, or null. Cheap to call only for consumable materials. */
    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        final ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private ItemStack stamped(Consumable c) {
        final ItemStack stack = c.template().clone();
        final ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, c.getId());
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------------ management (commands)

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isListening() {
        return listener != null;
    }

    public Consumable get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Consumable> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public static List<String> options() {
        return OPTIONS;
    }

    /** Creates a consumable from an item (the copy is stored, so later changes to your item do not matter). */
    public Consumable create(String id, ItemStack item) throws ConfigException, IOException {
        return create(id, item, Collections.emptyList(), Collections.emptyList());
    }

    /** Saves a shop or crate reward only after its command and feedback pass the normal compiler. */
    public Consumable createReward(String id, ItemStack item, String command, String message, boolean effects)
            throws ConfigException, IOException {
        if (command == null || command.trim().isEmpty()) {
            throw new ConfigException("a shop reward needs a command");
        }
        final List<Object> actions = new ArrayList<>(3);
        if (message != null && !message.trim().isEmpty()) {
            actions.add(Collections.singletonMap("message", message));
        }
        if (effects) {
            actions.add(Collections.singletonMap("sound", "entity.player.levelup"));
            actions.add(Collections.singletonMap("particle", "happy_villager"));
        }
        return create(id, item, Collections.singletonList(command), actions);
    }

    private Consumable create(String id, ItemStack item, List<String> commands, List<Object> actions)
            throws ConfigException, IOException {
        if (!enabled) {
            throw new ConfigException("consumables.enabled is false");
        }
        id = ItemTags.normalize(id);
        final ParseContext pc = new ParseContext(engine, "consumables/" + id + ".yml");
        if (!ItemTags.isValidId(id)) {
            throw pc.error("'" + id + "' is not a valid id (a-z, 0-9, _ and -)");
        }
        if (byId.containsKey(id) || fileOf(id).exists()) {
            throw pc.error("already exists");
        }
        final ItemStack clean = clean(item, pc);
        final Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("item", DefinitionFiles.encode(clean));
        raw.put("materials", new ArrayList<Object>(Collections.singletonList(clean.getType().name())));
        raw.put("click", "right");
        raw.put("amount", 1);
        raw.put("cooldown", "1s");
        raw.put("commands", new ArrayList<>(commands));
        if (!actions.isEmpty()) {
            raw.put("actions", new ArrayList<>(actions));
        }
        return store(id, raw);
    }

    /** Replaces the item of a consumable. Items given before still work (their material stays accepted). */
    public Consumable update(String id, ItemStack item) throws ConfigException, IOException {
        final Consumable current = require(id);
        final ParseContext pc = new ParseContext(engine, "consumables/" + current.getId() + ".yml");
        final ItemStack clean = clean(item, pc);
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        raw.put("item", DefinitionFiles.encode(clean));
        final List<Object> mats = new ArrayList<>(Cfg.list(raw.get("materials")));
        if (!mats.contains(clean.getType().name())) {
            mats.add(clean.getType().name());
        }
        raw.put("materials", mats);
        return store(current.getId(), raw);
    }

    public boolean delete(String id) throws IOException {
        final String clean = ItemTags.normalize(id);
        if (byId.remove(clean) == null) {
            return false;
        }
        Files.deleteIfExists(fileOf(clean).toPath());
        rebuildMaterials();
        syncListener();
        return true;
    }

    /** @return the number of the new command (1-based) */
    public int addCommand(String id, String command) throws ConfigException, IOException {
        final Consumable current = require(id);
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        final List<Object> commands = new ArrayList<>(Cfg.list(raw.get("commands")));
        commands.add(command);
        raw.put("commands", commands);
        store(current.getId(), raw);
        return commands.size();
    }

    public boolean removeCommand(String id, int number) throws ConfigException, IOException {
        final Consumable current = require(id);
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        final List<Object> commands = new ArrayList<>(Cfg.list(raw.get("commands")));
        if (number < 1 || number > commands.size()) {
            return false;
        }
        commands.remove(number - 1);
        raw.put("commands", commands);
        store(current.getId(), raw);
        return true;
    }

    public int addAction(String id, String type, String value) throws ConfigException, IOException {
        return addEntry(id, "actions", type, value);
    }

    public boolean removeAction(String id, int number) throws ConfigException, IOException {
        return removeEntry(id, "actions", number);
    }

    public int addEffect(String id, String type, Object value) throws ConfigException, IOException {
        return addEntry(id, "actions", type, value);
    }

    public int addCondition(String id, String type, Object value) throws ConfigException, IOException {
        return addEntry(id, "conditions", type, value);
    }

    public boolean removeCondition(String id, int number) throws ConfigException, IOException {
        return removeEntry(id, "conditions", number);
    }

    private int addEntry(String id, String list, String type, Object value) throws ConfigException, IOException {
        final Consumable current = require(id);
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        final List<Object> entries = Cfg.list(raw.get(list));
        entries.add(Collections.singletonMap(type.toLowerCase(Locale.ROOT), Cfg.plain(value)));
        raw.put(list, entries);
        store(current.getId(), raw);
        return entries.size();
    }

    private boolean removeEntry(String id, String list, int number) throws ConfigException, IOException {
        final Consumable current = require(id);
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        final List<Object> entries = Cfg.list(raw.get(list));
        if (number < 1 || number > entries.size()) {
            return false;
        }
        entries.remove(number - 1);
        raw.put(list, entries);
        store(current.getId(), raw);
        return true;
    }

    /** option: click, amount, cooldown or run-when-cancelled. The value is validated before anything is saved. */
    public void setOption(String id, String option, String value) throws ConfigException, IOException {
        final Consumable current = require(id);
        final String opt = option.toLowerCase(Locale.ROOT);
        if (!OPTIONS.contains(opt)) {
            throw new ConfigException("unknown option '" + option + "' (" + String.join(", ", OPTIONS) + ")");
        }
        final Map<String, Object> raw = DefinitionFiles.copy(current.raw());
        Object typed = value;
        if (opt.equals("amount")) {
            try {
                typed = Integer.valueOf(value.trim());
            } catch (NumberFormatException ignored) {
                // left as text: the compiler reports it
            }
        } else if (opt.equals("run-when-cancelled")) {
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                typed = Boolean.valueOf(value.toLowerCase(Locale.ROOT));
            }
        }
        raw.put(opt, typed);
        store(current.getId(), raw);
    }

    /** Gives a player {@code amount} of the consumable. Returns how many items did not fit and were dropped. */
    public int give(Consumable consumable, Player target, int amount) {
        return Inventories.give(target, stamped(consumable), amount);
    }

    /** Returns a fresh tagged item for a shop, crate or external reward bridge. */
    public ItemStack createRewardItem(String id) {
        final Consumable consumable = enabled ? get(id) : null;
        return consumable == null || !consumable.isUsable() ? null : stamped(consumable);
    }

    private Consumable require(String id) throws ConfigException {
        final Consumable c = get(id);
        if (c == null) {
            throw new ConfigException("consumable '" + id + "' not found");
        }
        return c;
    }

    private Consumable store(String id, Map<String, Object> raw) throws ConfigException, IOException {
        final Consumable compiled = compile(id, raw); // validates first: nothing is written if it is wrong
        DefinitionFiles.write(fileOf(id), raw);
        byId.put(id, compiled);
        rebuildMaterials();
        syncListener();
        return compiled;
    }

    private ItemStack clean(ItemStack item, ParseContext pc) throws ConfigException {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) {
            throw pc.error("hold an item in your main hand");
        }
        final ItemStack copy = item.clone();
        copy.setAmount(1);
        final ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().remove(key); // never store the mark of another consumable
        copy.setItemMeta(meta);
        return copy;
    }

    // ------------------------------------------------------------------ compiling and files

    private Consumable compile(String id, Map<String, Object> raw) throws ConfigException {
        final ParseContext pc = new ParseContext(engine, "consumables/" + id + ".yml");
        Cfg.checkKeys(raw, pc, KEYS);

        if (raw.get("item") == null) {
            throw pc.error("missing 'item' (create consumables with /ie consumable create)");
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

        final EnumSet<Material> mats = EnumSet.noneOf(Material.class);
        mats.add(template.getType());
        for (Object o : Cfg.list(raw.get("materials"))) {
            final Material m = Material.matchMaterial(String.valueOf(o));
            if (m == null) {
                throw pc.child("materials").error("unknown material '" + o + "'");
            }
            mats.add(m);
        }

        int clickMask = 0;
        final Object click = raw.get("click") == null ? "right" : raw.get("click");
        for (Object o : Cfg.list(click)) {
            final int mask = ClickKind.maskOf(String.valueOf(o));
            if (mask == 0) {
                throw pc.child("click").error("unknown click '" + o + "' (left, right, any, left-air, left-block, right-air, right-block)");
            }
            clickMask |= mask;
        }

        final double amountValue = Cfg.number(raw.get("amount") == null ? 1 : raw.get("amount"), pc.child("amount"));
        if (amountValue < 1 || amountValue > 64 || amountValue != Math.rint(amountValue)) {
            throw pc.child("amount").error("must be a whole number from 1 to 64");
        }
        final long cooldown = raw.get("cooldown") == null ? 1000L : TimeParser.parseMillis(raw.get("cooldown"), 1000, pc.child("cooldown"));
        final boolean whenCancelled = raw.get("run-when-cancelled") != null
                && Cfg.bool(raw.get("run-when-cancelled"), pc.child("run-when-cancelled"));

        final Condition conditions = Conditions.parseAll(raw.get("conditions"), pc.child("conditions"));

        final List<RuleAction> actions = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final List<Object> commandList = Cfg.list(raw.get("commands"));
        for (int i = 0; i < commandList.size(); i++) {
            final Object command = commandList.get(i);
            actions.add(engine.parseAction("command", command, pc.child("commands").index(i)));
            commands.add(String.valueOf(command));
        }
        final RuleAction[] extra = Actions.parseAll(raw.get("actions"), pc.child("actions"));
        actions.addAll(Arrays.asList(extra));

        return new Consumable(id, raw, template, mats, clickMask, (int) amountValue, cooldown, whenCancelled, conditions,
                Actions.sequence(actions.toArray(new RuleAction[0])), commands, extra.length);
    }

    private File folder() {
        return new File(plugin.getDataFolder(), "consumables");
    }

    private File fileOf(String id) {
        return new File(folder(), id + ".yml");
    }
}

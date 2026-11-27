package at.dumphey.itemeditor.engine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Triggers, conditions and actions.
 *
 * <ul>
 *   <li>Nothing is registered until a rule needs it: with no rules there is no listener and no task.</li>
 *   <li>One Bukkit listener per trigger type that is in use, added/removed on reload.</li>
 *   <li>Rules are indexed by trigger and by the custom item / enchantment they are bound to, so an event only looks
 *       at the rules that can apply to the items involved.</li>
 *   <li>A rule that throws is isolated; after {@code triggers.max-rule-errors} errors it is switched off.</li>
 * </ul>
 */
public final class TriggerEngine {

    private static final int MAX_DEPTH = 4;
    private static final int MAX_ERRORS_KEPT = 200;

    /** The rules of one trigger type, indexed for fast lookup. */
    private static final class Bucket {
        private final List<Rule> global = new ArrayList<>();
        private final Map<String, List<Rule>> byItem = new HashMap<>();
        private final Map<String, List<Rule>> byEnchant = new HashMap<>();
        private int slotMask;
        private boolean bound;
        /** Union of the materials of the bound rules; ignored when {@link #unfiltered}. */
        private final EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        /** True when some bound rule accepts any material: then no item can be skipped by material. */
        private boolean unfiltered;
        private int unfilteredSlots;
        private final Map<EquipmentSlot, EnumSet<Material>> materialsBySlot = new EnumMap<>(EquipmentSlot.class);

        private boolean accepts(EquipmentSlot slot, int bit, Material type) {
            if ((unfilteredSlots & bit) != 0) return true;
            final EnumSet<Material> allowed = materialsBySlot.get(slot);
            return allowed != null && allowed.contains(type);
        }

        private boolean isEmpty() {
            return global.isEmpty() && !bound;
        }
    }

    private final JavaPlugin plugin;
    private final ItemTags tags;
    private final CooldownManager cooldowns = new CooldownManager();
    private final Map<String, ConditionFactory> conditionFactories = new TreeMap<>();
    private final Map<String, ActionFactory> actionFactories = new TreeMap<>();
    private final Map<TriggerType, TriggerSource> sources = new EnumMap<>(TriggerType.class);
    private final Map<TriggerType, Listener> listeners = new EnumMap<>(TriggerType.class);
    private final Map<String, Rule> rules = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final TriggerContext[] pool = new TriggerContext[MAX_DEPTH];
    private final ConsumableManager consumables;
    private final CustomItemManager customItems;
    private final MythicMobLookup mythicMobs;
    private final List<Runnable> confirmedHits = new ArrayList<>();
    private BukkitTask confirmedHitTask;
    private long hitGeneration;

    private Bucket[] buckets = new Bucket[TriggerType.values().length];
    private BukkitTask periodicTask;
    private final List<Player> periodicPlayers = new ArrayList<>();
    private int periodicCursor;
    private int periodicPhase;
    private int periodicSlices = 8;
    private int depth;
    private long periodicTick;

    private boolean enabled = true;
    private int maxRuleErrors = 5;
    private int periodicBaseTicks = 20;
    private int disabledInFiles;

    // statistics (all on the main thread)
    private long eventsHandled;
    private long rulesEvaluated;
    private long rulesFired;
    private long ruleErrors;

    private final boolean customCommands;
    private final boolean customEnchantments;
    private final EnchantCatalog enchantCatalog;

    public TriggerEngine(JavaPlugin plugin) {
        this(plugin, true, true);
    }

    public TriggerEngine(JavaPlugin plugin, boolean customCommands, boolean customEnchantments) {
        this(plugin, customCommands, customEnchantments, customCommands);
    }

    public TriggerEngine(JavaPlugin plugin, boolean customCommands, boolean customEnchantments, boolean consumablesEnabled) {
        this.plugin = plugin;
        this.customCommands = customCommands;
        this.customEnchantments = customEnchantments;
        this.tags = new ItemTags(plugin);
        this.consumables = consumablesEnabled ? new ConsumableManager(this) : null;
        this.customItems = customCommands ? new CustomItemManager(this) : null;
        this.mythicMobs = new MythicMobLookup(plugin);
        Conditions.registerDefaults(this);
        Actions.registerDefaults(this);
        this.enchantCatalog = customEnchantments ? new EnchantCatalog(this) : null;
        for (TriggerSource source : TriggerSources.defaults()) {
            sources.put(source.type(), source);
        }
    }

    // ------------------------------------------------------------------ extension points

    /** Adds (or replaces) a condition type usable in rules as {@code name: value}. */
    public void registerCondition(String name, ConditionFactory factory) {
        conditionFactories.put(name.toLowerCase(), factory);
    }

    /** Adds (or replaces) an action type usable in rules as {@code name: value}. */
    public void registerAction(String name, ActionFactory factory) {
        actionFactories.put(name.toLowerCase(), factory);
    }

    /** Replaces how a trigger type reads its Bukkit event (for example to support another event class). */
    public void registerSource(TriggerSource source) {
        sources.put(source.type(), source);
    }

    public Condition parseCondition(String name, Object value, ParseContext pc) throws ConfigException {
        final ConditionFactory factory = conditionFactories.get(name.toLowerCase());
        if (factory == null) {
            throw pc.error("unknown condition '" + name + "' (available: " + String.join(", ", conditionFactories.keySet()) + ")");
        }
        try {
            return factory.create(Cfg.plain(value), pc);
        } catch (ConfigException e) {
            throw e;
        } catch (RuntimeException e) {
            throw pc.error("invalid value: " + e);
        }
    }

    public RuleAction parseAction(String name, Object value, ParseContext pc) throws ConfigException {
        final ActionFactory factory = actionFactories.get(name.toLowerCase());
        if (factory == null) {
            throw pc.error("unknown action '" + name + "' (available: " + String.join(", ", actionFactories.keySet()) + ")");
        }
        try {
            return factory.create(Cfg.plain(value), pc);
        } catch (ConfigException e) {
            throw e;
        } catch (RuntimeException e) {
            throw pc.error("invalid value: " + e);
        }
    }

    /** Compiles a rule from a plain YAML map (this is what rule files use, and what code generating rules can use). */
    public Rule compileRule(String id, String source, Map<String, Object> config) throws ConfigException {
        return RuleLoader.compile(this, id.toLowerCase(), source, config);
    }

    boolean acceptsRule(boolean enchantmentBound) {
        return enchantmentBound ? customEnchantments : customCommands;
    }

    /** Adds compiled rules and registers the listeners they need. Rules with an id that exists are skipped. */
    public void addRules(Collection<Rule> toAdd) {
        for (Rule rule : toAdd) {
            if (!acceptsRule(rule.getEnchantId() != null)) {
                continue;
            }
            if (rules.containsKey(rule.getId())) {
                addError(rule.getSource() + ": a rule with the id '" + rule.getId() + "' already exists");
                continue;
            }
            rules.put(rule.getId(), rule);
            index(rule);
        }
        syncListeners();
    }

    /** Removes every rule the filter accepts (for example all the skills of one custom item) and updates the listeners. */
    public int removeRules(Predicate<Rule> filter) {
        int removed = 0;
        for (java.util.Iterator<Rule> it = rules.values().iterator(); it.hasNext(); ) {
            if (filter.test(it.next())) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            buckets = new Bucket[TriggerType.values().length]; // dispatches in progress keep the old, untouched buckets
            for (Rule rule : rules.values()) {
                index(rule);
            }
            syncListeners();
        }
        return removed;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Reads the settings and rules/ and (un)registers listeners. Safe to call again to reload. */
    public void load() {
        shutdown();
        enabled = plugin.getConfig().getBoolean("triggers.enabled", true);
        maxRuleErrors = Math.max(1, plugin.getConfig().getInt("triggers.max-rule-errors", 5));
        periodicBaseTicks = Math.max(1, plugin.getConfig().getInt("triggers.periodic-base-ticks", 20));
        periodicSlices = Math.max(1, Math.min(periodicBaseTicks,
                Math.min(20, plugin.getConfig().getInt("enchantment-performance.periodic-spread-ticks", 8))));
        if (enabled && (customCommands || customEnchantments)) mythicMobs.load();
        if (consumables != null) {
            consumables.load();
        }
        final List<Rule> catalogRules = enchantCatalog == null ? Collections.emptyList() : enchantCatalog.load();
        if (!enabled) {
            plugin.getLogger().info("Triggers are disabled in config.yml (triggers.enabled).");
            if (customCommands) {
                customItems.load();
            }
            return;
        }

        if (!customCommands && !customEnchantments) return; // Consumables run on their own interaction listener.
        final File folder = new File(plugin.getDataFolder(), "rules");
        if (!folder.exists()) {
            try {
                plugin.saveResource("rules/examples.yml", false);
            } catch (IllegalArgumentException e) {
                folder.mkdirs();
            }
        }
        final RuleLoader loader = new RuleLoader(this);
        final List<Rule> loaded = new ArrayList<>(catalogRules);
        loaded.addAll(loader.loadAll(folder));
        disabledInFiles = loader.disabledCount();
        addRules(loaded);
        if (enchantCatalog != null) {
            enchantCatalog.startAcquisition();
        }
        if (customCommands) {
            customItems.load();
        }

        plugin.getLogger().info("Triggers: " + rules.size() + " rule(s) active, " + disabledInFiles + " disabled, "
                + errors.size() + " error(s), " + listeners.size() + " listener(s).");
        for (int i = 0; i < Math.min(errors.size(), 10); i++) {
            plugin.getLogger().warning("Rule error: " + errors.get(i));
        }
    }

    /** Unregisters every listener and task and forgets the rules. Cooldowns are kept. */
    public void shutdown() {
        mythicMobs.stop();
        if (confirmedHitTask != null) {
            confirmedHitTask.cancel();
            confirmedHitTask = null;
        }
        confirmedHits.clear();
        hitGeneration++;
        if (enchantCatalog != null) {
            enchantCatalog.stop();
        }
        if (consumables != null) {
            consumables.shutdown();
        }
        if (customCommands) {
            customItems.shutdown();
        }
        for (Listener listener : listeners.values()) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
        stopPeriodic();
        rules.clear();
        errors.clear();
        buckets = new Bucket[TriggerType.values().length];
        disabledInFiles = 0;
    }

    public void onQuit(UUID player) {
        cooldowns.clear(player);
    }

    // ------------------------------------------------------------------ indexing and listeners

    private void index(Rule rule) {
        Bucket bucket = buckets[rule.getTrigger().ordinal()];
        if (bucket == null) {
            bucket = new Bucket();
            buckets[rule.getTrigger().ordinal()] = bucket;
        }
        if (rule.getItemId() != null) {
            bucket.byItem.computeIfAbsent(rule.getItemId(), k -> new ArrayList<>()).add(rule);
        } else if (rule.getEnchantId() != null) {
            bucket.byEnchant.computeIfAbsent(rule.getEnchantId(), k -> new ArrayList<>()).add(rule);
        } else {
            bucket.global.add(rule);
            return;
        }
        bucket.bound = true;
        bucket.slotMask |= rule.getSlotMask();
        if (rule.getMaterials() == null) {
            bucket.unfiltered = true;
            bucket.unfilteredSlots |= rule.getSlotMask();
        } else {
            bucket.materials.addAll(rule.getMaterials());
            for (int i = 0; i < Slots.SLOTS.length; i++) {
                if ((rule.getSlotMask() & (1 << i)) != 0) {
                    bucket.materialsBySlot.computeIfAbsent(Slots.SLOTS[i], slot -> EnumSet.noneOf(Material.class))
                            .addAll(rule.getMaterials());
                }
            }
        }
    }

    /** Registers the listener of every trigger in use, and starts the periodic task if a PERIODIC rule exists. */
    private void syncListeners() {
        for (TriggerType type : TriggerType.values()) {
            final Bucket bucket = buckets[type.ordinal()];
            final boolean needed = bucket != null && !bucket.isEmpty();
            if (type == TriggerType.PERIODIC) {
                if (needed && periodicTask == null) {
                    periodicTask = Bukkit.getScheduler().runTaskTimer(plugin, this::periodicTick, periodicBaseTicks, 1);
                } else if (!needed && periodicTask != null) {
                    stopPeriodic();
                }
                continue;
            }
            if (!needed && listeners.containsKey(type)) {
                HandlerList.unregisterAll(listeners.remove(type)); // nothing uses this trigger any more
                continue;
            }
            if (needed && !listeners.containsKey(type)) {
                final TriggerSource source = sources.get(type);
                if (source == null) {
                    addError("no event source registered for trigger " + type);
                    continue;
                }
                final Listener listener = new Listener() {
                };
                final Class<? extends Event> eventClass = source.eventClass();
                Bukkit.getPluginManager().registerEvent(eventClass, listener, source.priority(), (l, event) -> {
                    if (eventClass.isInstance(event)) {
                        handle(source, event);
                    }
                }, plugin, false); // cancelled events are filtered per rule (run-when-cancelled)
                listeners.put(type, listener);
            }
        }
    }

    // ------------------------------------------------------------------ dispatch

    private TriggerContext acquire() {
        if (depth >= MAX_DEPTH) {
            return null;
        }
        TriggerContext ctx = pool[depth];
        if (ctx == null) {
            ctx = new TriggerContext();
            pool[depth] = ctx;
        }
        depth++;
        ctx.engine = this;
        return ctx;
    }

    private void release(TriggerContext ctx) {
        ctx.reset();
        depth--;
    }

    private void handle(TriggerSource source, Event event) {
        final Bucket bucket = buckets[source.type().ordinal()];
        if (bucket == null) {
            return;
        }
        final TriggerContext ctx = acquire();
        if (ctx == null) {
            plugin.getLogger().warning("Trigger recursion limit reached (" + MAX_DEPTH + "): " + source.type() + " ignored. "
                    + "A rule probably triggers itself.");
            return;
        }
        try {
            ctx.type = source.type();
            ctx.event = event;
            if (!source.fill(event, ctx) || ctx.player == null) {
                return;
            }
            eventsHandled++;
            dispatch(bucket, ctx, source.type().isSubjectBased());
        } catch (RuntimeException e) {
            ruleErrors++;
            plugin.getLogger().log(Level.WARNING, "Trigger " + source.type() + " failed", e);
        } finally {
            release(ctx);
        }
    }

    private void dispatch(Bucket bucket, TriggerContext ctx, boolean subjectBased) {
        final List<Rule> global = bucket.global;
        if (!global.isEmpty()) {
            if (!subjectBased) {
                // so conditions like item-material / item-id mean "the item in the hand" for these triggers
                final ItemStack hand = ctx.player.getInventory().getItemInMainHand();
                ctx.item = hand == null || hand.getType().isAir() ? null : hand;
                ctx.slot = EquipmentSlot.HAND;
                ctx.forgetTag();
            }
            for (int i = 0; i < global.size(); i++) {
                run(global.get(i), ctx);
            }
        }
        if (!bucket.bound) {
            return;
        }
        if (subjectBased) {
            if (ctx.item == null || (!bucket.unfiltered && !bucket.materials.contains(ctx.item.getType()))) {
                return; // an item no bound rule can be about: its data is never read
            }
            final ItemTagData tag = ctx.tags();
            if (tag != null) {
                runTagged(bucket, tag, ctx, Slots.bit(ctx.slot));
            }
            return;
        }
        final PlayerInventory inventory = ctx.player.getInventory();
        for (int i = 0; i < Slots.SLOTS.length; i++) {
            final int bit = 1 << i;
            if ((bucket.slotMask & bit) == 0) {
                continue;
            }
            final ItemStack item = inventory.getItem(Slots.SLOTS[i]);
            if (item == null || !bucket.accepts(Slots.SLOTS[i], bit, item.getType())) {
                continue;
            }
            final ItemTagData tag = tags.read(item);
            if (tag == null) {
                continue;
            }
            ctx.item = item;
            ctx.slot = Slots.SLOTS[i];
            ctx.setTag(tag);
            runTagged(bucket, tag, ctx, bit);
        }
    }

    private void runTagged(Bucket bucket, ItemTagData tag, TriggerContext ctx, int slotBit) {
        ctx.itemId = tag.getItemId();
        if (tag.getItemId() != null) {
            final List<Rule> list = bucket.byItem.get(tag.getItemId());
            if (list != null) {
                ctx.level = 0;
                for (int i = 0; i < list.size(); i++) {
                    final Rule rule = list.get(i);
                    if ((rule.getSlotMask() & slotBit) != 0 && materialAllowed(rule, ctx)) {
                        run(rule, ctx);
                    }
                }
            }
        }
        if (!bucket.byEnchant.isEmpty() && !tag.getEnchants().isEmpty()) {
            for (Map.Entry<String, Integer> e : tag.getEnchants().entrySet()) {
                final List<Rule> list = bucket.byEnchant.get(e.getKey());
                if (list == null) {
                    continue;
                }
                ctx.level = e.getValue();
                for (int i = 0; i < list.size(); i++) {
                    final Rule rule = list.get(i);
                    if ((rule.getSlotMask() & slotBit) != 0 && materialAllowed(rule, ctx)) {
                        run(rule, ctx);
                    }
                }
            }
            ctx.level = 0;
        }
    }

    private static boolean materialAllowed(Rule rule, TriggerContext ctx) {
        return rule.getMaterials() == null || rule.getMaterials().contains(ctx.item.getType());
    }

    private void run(Rule rule, TriggerContext ctx) {
        if (rule.isDisabled()) {
            return;
        }
        if (rule.getPeriod() > 1 && ctx.tick % rule.getPeriod() != 0) {
            return;
        }
        if (rule.isIgnoreCancelled() && ctx.isCancelled()) {
            return; // another plugin (protection, anti-cheat...) already said no
        }
        if (rule.isCatalog() && rule.getCooldownMs() > 0 && rule.cooldownMessage() == null
                && !cooldowns.isReady(ctx.player.getUniqueId(), rule.getId())) {
            return;
        }
        ctx.rule = rule;
        rulesEvaluated++;
        try {
            final Condition conditions = rule.conditions();
            if (conditions != null && !conditions.test(ctx)) {
                return;
            }
            if (rule.getCooldownMs() > 0
                    && !cooldowns.tryUse(ctx.player.getUniqueId(), rule.getId(), rule.getCooldownMs())) {
                // checked after the conditions, so a rule that did not apply never burns its cooldown
                if (rule.cooldownMessage() != null) {
                    ctx.cooldownLeftMs = cooldowns.remaining(ctx.player.getUniqueId(), rule.getId());
                    ctx.player.sendMessage(rule.cooldownMessage().render(ctx));
                }
                return;
            }
            rulesFired++;
            rule.markFired();
            final RuleAction[] actions = rule.actions();
            for (int i = 0; i < actions.length; i++) {
                actions[i].execute(ctx);
            }
        } catch (RuntimeException e) {
            onRuleError(rule, e);
        }
    }

    private void onRuleError(Rule rule, RuntimeException e) {
        ruleErrors++;
        final int count = rule.addError();
        if (count == 1) {
            plugin.getLogger().log(Level.WARNING, "Rule '" + rule.getId() + "' (" + rule.getSource() + ") failed", e);
        } else {
            plugin.getLogger().warning("Rule '" + rule.getId() + "' failed again (" + count + "/" + maxRuleErrors + "): " + e);
        }
        if (count >= maxRuleErrors) {
            rule.disable();
            plugin.getLogger().severe("Rule '" + rule.getId() + "' was switched off after " + count
                    + " errors. Fix it and run /ie rules reload.");
        }
    }

    public boolean hasMythicMobs() {
        return mythicMobs != null && mythicMobs.isAvailable();
    }

    String mythicId(org.bukkit.entity.Entity target) {
        return mythicMobs == null ? null : mythicMobs.internalName(target);
    }

    @FunctionalInterface
    interface ConfirmedHit {
        void apply(Player player, LivingEntity target, double damage);
    }

    /** Confirms the final damage after protection and damage listeners have run. */
    void afterAttack(TriggerContext ctx, ConfirmedHit effect) {
        if (ctx.type != TriggerType.ATTACK || !(ctx.event instanceof EntityDamageByEntityEvent)
                || !(ctx.target instanceof LivingEntity) || confirmedHits.size() >= 256) return;
        final EntityDamageByEntityEvent event = (EntityDamageByEntityEvent) ctx.event;
        final Player player = ctx.player;
        final LivingEntity target = (LivingEntity) ctx.target;
        final double before = target.getHealth();
        final Rule rule = ctx.rule;
        confirmedHits.add(() -> {
            if (event.isCancelled() || !player.isOnline() || player.isDead()
                    || player.getWorld() != target.getWorld()) return;
            final double damage = Math.min(before, event.getFinalDamage());
            if (!Double.isFinite(damage) || damage <= 0) return;
            try {
                effect.apply(player, target, damage);
            } catch (RuntimeException ex) {
                if (rule != null) onRuleError(rule, ex);
                else plugin.getLogger().log(Level.WARNING, "Confirmed attack effect failed", ex);
            }
        });
        if (confirmedHitTask == null) {
            confirmedHitTask = Bukkit.getScheduler().runTask(plugin, () -> {
                confirmedHitTask = null;
                final long generation = hitGeneration;
                final List<Runnable> hits = new ArrayList<>(confirmedHits);
                confirmedHits.clear();
                for (Runnable hit : hits) {
                    if (generation != hitGeneration) break;
                    hit.run();
                }
            });
        }
    }

    /** Runs actions later with a snapshot of the context (used by the delay action). */
    void runLater(int ticks, TriggerContext snapshot, RuleAction action) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (snapshot.player == null || !snapshot.player.isOnline()) {
                return;
            }
            try {
                action.execute(snapshot);
            } catch (RuntimeException e) {
                if (snapshot.rule != null) {
                    onRuleError(snapshot.rule, e);
                } else {
                    plugin.getLogger().log(Level.WARNING, "Delayed action failed", e);
                }
            }
        }, ticks);
    }

    private void stopPeriodic() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        periodicPlayers.clear();
        periodicCursor = 0;
        periodicPhase = 0;
    }

    private void periodicTick() {
        final int phase = periodicPhase;
        periodicPhase = (phase + 1) % periodicBaseTicks;
        if (phase >= periodicSlices) return;
        final Bucket bucket = buckets[TriggerType.PERIODIC.ordinal()];
        if (bucket == null || bucket.isEmpty()) return;
        if (phase == 0) {
            periodicTick++;
            periodicPlayers.clear();
            periodicPlayers.addAll(Bukkit.getOnlinePlayers());
            periodicCursor = 0;
        }
        final int through = (int) ((periodicPlayers.size() * (long) (phase + 1) + periodicSlices - 1) / periodicSlices);
        final BukkitTask active = periodicTask;
        while (periodicTask == active && periodicCursor < through) {
            final Player player = periodicPlayers.get(periodicCursor++);
            if (!player.isOnline() || player.isDead()) continue;
            final TriggerContext ctx = acquire();
            if (ctx == null) {
                return;
            }
            try {
                ctx.type = TriggerType.PERIODIC;
                ctx.player = player;
                ctx.tick = periodicTick;
                eventsHandled++;
                dispatch(bucket, ctx, false);
            } catch (RuntimeException e) {
                ruleErrors++;
                plugin.getLogger().log(Level.WARNING, "Periodic trigger failed", e);
            } finally {
                release(ctx);
            }
        }
        if (periodicTask == active && periodicCursor == periodicPlayers.size()) {
            periodicPlayers.clear();
            periodicCursor = 0;
        }
    }

    /**
     * Fires a trigger from code (for custom features): {@code filler} sets what the rules should see.
     * The player is required.
     */
    public void fire(TriggerType type, Player player, Consumer<TriggerContext> filler) {
        final Bucket bucket = buckets[type.ordinal()];
        if (bucket == null || bucket.isEmpty() || !enabled) {
            return;
        }
        final TriggerContext ctx = acquire();
        if (ctx == null) {
            return;
        }
        try {
            ctx.type = type;
            ctx.player = player;
            if (filler != null) {
                filler.accept(ctx);
            }
            eventsHandled++;
            dispatch(bucket, ctx, type.isSubjectBased());
        } finally {
            release(ctx);
        }
    }

    // ------------------------------------------------------------------ information

    void addError(String message) {
        if (errors.size() < MAX_ERRORS_KEPT) {
            errors.add(message);
        }
    }

    /** Custom items: items with skills (rules that belong to the item). */
    public EnchantCatalog enchantCatalog() {
        return enchantCatalog;
    }

    public CustomItemManager customItems() {
        return customItems;
    }

    /** Consumable items: click them to run commands and use them up. */
    public ConsumableManager consumables() {
        return consumables;
    }

    public ItemTags tags() {
        return tags;
    }

    public CooldownManager cooldowns() {
        return cooldowns;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    int periodicBaseTicks() {
        return periodicBaseTicks;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Collection<Rule> getRules() {
        return Collections.unmodifiableCollection(rules.values());
    }

    public Rule getRule(String id) {
        return id == null ? null : rules.get(id.toLowerCase());
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public int getDisabledInFiles() {
        return disabledInFiles;
    }

    public int getListenerCount() {
        return listeners.size();
    }

    public boolean isPeriodicRunning() {
        return periodicTask != null;
    }

    public long getEventsHandled() {
        return eventsHandled;
    }

    public long getRulesEvaluated() {
        return rulesEvaluated;
    }

    public long getRulesFired() {
        return rulesFired;
    }

    public long getRuleErrors() {
        return ruleErrors;
    }

    public Collection<String> getConditionNames() {
        return Collections.unmodifiableSet(conditionFactories.keySet());
    }

    public Collection<String> getActionNames() {
        return Collections.unmodifiableSet(actionFactories.keySet());
    }
}

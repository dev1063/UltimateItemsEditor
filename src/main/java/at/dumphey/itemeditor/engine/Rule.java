package at.dumphey.itemeditor.engine;

import org.bukkit.Material;

import java.util.Set;

/** One compiled rule: when TRIGGER happens (for the bound item/enchantment) and CONDITIONS hold, run ACTIONS. */
public final class Rule {

    private final String id;
    private final boolean catalog;
    private final String source;
    private final TriggerType trigger;
    private final String itemId;
    private final String enchantId;
    private final Set<Material> materials;
    private final int slotMask;
    private final Condition conditions;
    private final RuleAction[] actions;
    private final long cooldownMs;
    private final Template cooldownMessage;
    private final boolean ignoreCancelled;
    private final int period;
    private final String displayName;
    private final String description;

    private boolean disabled;
    private int errors;
    private long fired;

    Rule(String id, String source, TriggerType trigger, String itemId, String enchantId, Set<Material> materials,
         int slotMask, Condition conditions, RuleAction[] actions, long cooldownMs, Template cooldownMessage,
         boolean ignoreCancelled, int period, String displayName, String description) {
        this.id = id;
        this.catalog = "custom-enchantments.yml".equals(source) && id.startsWith("catalog.");
        this.source = source;
        this.trigger = trigger;
        this.itemId = itemId;
        this.enchantId = enchantId;
        this.materials = materials;
        this.slotMask = slotMask;
        this.conditions = conditions;
        this.actions = actions;
        this.cooldownMs = cooldownMs;
        this.cooldownMessage = cooldownMessage;
        this.ignoreCancelled = ignoreCancelled;
        this.period = period;
        this.displayName = displayName;
        this.description = description;
    }

    Rule withLevelActions(RuleAction[] levels) {
        final boolean combat = trigger == TriggerType.ATTACK || trigger == TriggerType.DEFEND || trigger == TriggerType.INJURY;
        final Condition eligible = ctx -> ctx.level > 0 && ctx.level < levels.length
                && (!combat || ctx.engine() == null || ctx.engine().enchantCatalog() == null
                || !ctx.engine().enchantCatalog().isSecondaryDamage())
                && (conditions == null || conditions.test(ctx));
        return new Rule(id, source, trigger, itemId, enchantId, materials, slotMask, eligible,
                new RuleAction[]{ctx -> levels[ctx.level].execute(ctx)}, cooldownMs, cooldownMessage,
                ignoreCancelled, period, displayName, description);
    }

    boolean isCatalog() {
        return catalog;
    }

    public String getId() {
        return id;
    }

    /** File the rule was loaded from (or "api"). */
    public String getSource() {
        return source;
    }

    public TriggerType getTrigger() {
        return trigger;
    }

    /** Custom item id this rule is bound to, or null. */
    public String getItemId() {
        return itemId;
    }

    /** Custom enchantment id this rule is bound to, or null. */
    public String getEnchantId() {
        return enchantId;
    }

    public boolean isBound() {
        return itemId != null || enchantId != null;
    }

    /**
     * The only materials the bound item may have, or null for any. When every bound rule of a trigger lists its
     * materials, the engine skips an item of any other material without reading its data at all.
     */
    public Set<Material> getMaterials() {
        return materials;
    }

    public int getSlotMask() {
        return slotMask;
    }

    Condition conditions() {
        return conditions;
    }

    RuleAction[] actions() {
        return actions;
    }

    public boolean hasConditions() {
        return conditions != null;
    }

    public int getActionCount() {
        return actions.length;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    /** Sent to the player when the cooldown blocks the rule (null = silent). */
    Template cooldownMessage() {
        return cooldownMessage;
    }

    public boolean isIgnoreCancelled() {
        return ignoreCancelled;
    }

    /** PERIODIC only: runs every {@code period} base ticks of the periodic task. */
    public int getPeriod() {
        return period;
    }

    /** Optional human name (skills), or null. */
    public String getDisplayName() {
        return displayName;
    }

    /** Optional description (skills), or null. */
    public String getDescription() {
        return description;
    }

    public boolean isDisabled() {
        return disabled;
    }

    void disable() {
        disabled = true;
    }

    int addError() {
        return ++errors;
    }

    public int getErrors() {
        return errors;
    }

    void markFired() {
        fired++;
    }

    public long getFired() {
        return fired;
    }
}

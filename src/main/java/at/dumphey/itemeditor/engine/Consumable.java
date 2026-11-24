package at.dumphey.itemeditor.engine;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An item that, when clicked, runs commands/actions and is used up. Immutable and fully compiled: using one only
 * evaluates ready-made objects.
 */
public final class Consumable {

    private final String id;
    private final Map<String, Object> raw;
    private final ItemStack template;
    private final Set<Material> materials;
    private final int clickMask;
    private final int amount;
    private final long cooldownMs;
    private final boolean runWhenCancelled;
    private final Condition conditions;
    private final RuleAction action;
    private final List<String> commands;
    private final int extraActions;
    private final boolean hasConditions;

    Consumable(String id, Map<String, Object> raw, ItemStack template, EnumSet<Material> materials, int clickMask,
               int amount, long cooldownMs, boolean runWhenCancelled, Condition conditions, RuleAction action,
               List<String> commands, int extraActions) {
        this.id = id;
        this.raw = raw;
        this.template = template;
        this.materials = Collections.unmodifiableSet(materials);
        this.clickMask = clickMask;
        this.amount = amount;
        this.cooldownMs = cooldownMs;
        this.runWhenCancelled = runWhenCancelled;
        this.conditions = conditions;
        this.hasConditions = conditions != null;
        this.action = action;
        this.commands = Collections.unmodifiableList(commands);
        this.extraActions = extraActions;
    }

    public String getId() {
        return id;
    }

    /** The YAML of this consumable as plain data (what is written to its file). */
    Map<String, Object> raw() {
        return raw;
    }

    /** A copy of the item as it is given to players (without the consumable mark). */
    public ItemStack getTemplate() {
        return template.clone();
    }

    /** The configured click(s) as written in the file (right by default). */
    public String getClickText() {
        final Object click = raw.get("click");
        return click == null ? "right" : String.valueOf(click);
    }

    ItemStack template() {
        return template;
    }

    /** Every material this consumable has ever had: items given before an update keep working. */
    public Set<Material> getMaterials() {
        return materials;
    }

    int clickMask() {
        return clickMask;
    }

    /** How many items one use takes. */
    public int getAmount() {
        return amount;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public boolean isRunWhenCancelled() {
        return runWhenCancelled;
    }

    Condition conditions() {
        return conditions;
    }

    RuleAction action() {
        return action;
    }

    public List<String> getCommands() {
        return commands;
    }

    public List<String> getActionDescriptions() {
        final List<Object> entries = Cfg.list(raw.get("actions"));
        final List<String> descriptions = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            descriptions.add(String.valueOf(entry));
        }
        return descriptions;
    }

    public int getExtraActions() {
        return extraActions;
    }

    public boolean hasConditions() {
        return hasConditions;
    }

    /** A consumable with nothing to run does nothing and is never used up. */
    public boolean isUsable() {
        return !commands.isEmpty() || extraActions > 0;
    }
}

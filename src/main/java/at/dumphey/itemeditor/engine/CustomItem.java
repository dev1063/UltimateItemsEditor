package at.dumphey.itemeditor.engine;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An item definition with skills. A skill is a rule that belongs to the item: it fires for any item that carries
 * this id, in the slots and for the trigger the skill says. Immutable and compiled.
 */
public final class CustomItem {

    private final String id;
    private final Map<String, Object> raw;
    private final ItemStack template;
    private final Set<Material> materials;
    private final List<Rule> rules;
    private final int disabledSkills;

    CustomItem(String id, Map<String, Object> raw, ItemStack template, Set<Material> materials, List<Rule> rules,
               int disabledSkills) {
        this.id = id;
        this.raw = raw;
        this.template = template;
        this.materials = Collections.unmodifiableSet(materials);
        this.rules = Collections.unmodifiableList(rules);
        this.disabledSkills = disabledSkills;
    }

    public String getId() {
        return id;
    }

    Map<String, Object> raw() {
        return raw;
    }

    ItemStack template() {
        return template;
    }

    /** A copy of the item as it is stored (without the custom id). */
    public ItemStack getTemplate() {
        return template.clone();
    }

    /** Every material the item has ever had: skills accept all of them, so items given before an update still work. */
    public Set<Material> getMaterials() {
        return materials;
    }

    /** The compiled, active skills as rules (their ids are {@code <item>.<skill>}). */
    public List<Rule> getRules() {
        return rules;
    }

    public int getDisabledSkills() {
        return disabledSkills;
    }

    /** Ids of every skill in the file, including disabled and broken ones. */
    public List<String> getSkillIds() {
        final Map<String, Object> skills = Cfg.map(raw.get("skills"));
        return skills == null ? new ArrayList<String>() : new ArrayList<>(skills.keySet());
    }

    /** Descriptions in file order, including entries from a disabled skill. */
    public List<String> getSkillEntries(String skillId, boolean conditions) {
        final Map<String, Object> skills = Cfg.map(raw.get("skills"));
        final Map<String, Object> skill = skills == null ? null : Cfg.map(skills.get(skillId.toLowerCase(Locale.ROOT)));
        final List<String> entries = new ArrayList<>();
        if (skill != null) {
            for (Object entry : Cfg.list(skill.get(conditions ? "conditions" : "actions"))) {
                entries.add(String.valueOf(entry));
            }
        }
        return entries;
    }

    /** The active skill with that id, or null. */
    public Rule getSkill(String skillId) {
        final String ruleId = id + "." + skillId.toLowerCase();
        for (Rule rule : rules) {
            if (rule.getId().equals(ruleId)) {
                return rule;
            }
        }
        return null;
    }
}

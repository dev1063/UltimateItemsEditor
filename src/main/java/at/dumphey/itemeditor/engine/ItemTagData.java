package at.dumphey.itemeditor.engine;

import java.util.Collections;
import java.util.Map;

/** The custom data of an item: an optional custom item id and its custom enchantments (id -> level). */
public final class ItemTagData {

    private final String itemId;
    private final Map<String, Integer> enchants;

    public ItemTagData(String itemId, Map<String, Integer> enchants) {
        this.itemId = itemId;
        this.enchants = Collections.unmodifiableMap(enchants);
    }

    /** Custom item id, or null. */
    public String getItemId() {
        return itemId;
    }

    public Map<String, Integer> getEnchants() {
        return enchants;
    }

    /** 0 if the item does not have that enchantment. */
    public int level(String enchantId) {
        final Integer level = enchants.get(enchantId);
        return level == null ? 0 : level;
    }
}

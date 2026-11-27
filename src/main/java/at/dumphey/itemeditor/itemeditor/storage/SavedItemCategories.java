package at.dumphey.itemeditor.itemeditor.storage;

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SavedItemCategories {

    public static final String ALL = "all";
    public static final String UNASSIGNED = "uncategorized";

    public static final class Category {
        private final String id;
        private final String name;
        private final Material icon;
        private final String description;
        private final int order;

        private Category(String id, String name, Material icon, String description, int order) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.order = order;
        }

        public String id() { return id; }
        public String name() { return name; }
        public Material icon() { return icon; }
        public String description() { return description; }
    }

    private static final String CONFIG_PATH = "saved-item-categories";

    private static final Category ALL_ITEMS = new Category(ALL, "Todos los ítems", Material.CHEST,
            "Abrir la colección completa", 0);
    private static final Category NO_CATEGORY = new Category(UNASSIGNED, "Sin categoría", Material.PAPER,
            "Ítems sin categoría o de categorías retiradas", 0);

    private final Map<String, Category> categories = new LinkedHashMap<>();
    private List<SerializedItem> indexedItems;
    private Map<String, List<SerializedItem>> byCategory = Collections.emptyMap();

    public static SavedItemCategories fromConfig(Configuration config) {
        final Object configured = config.get(CONFIG_PATH, null);
        if (configured instanceof ConfigurationSection) {
            return new SavedItemCategories((ConfigurationSection) configured);
        }
        final Configuration defaults = config.getDefaults();
        return new SavedItemCategories(defaults == null ? null : defaults.getConfigurationSection(CONFIG_PATH));
    }

    private SavedItemCategories(ConfigurationSection section) {
        if (section == null) return;
        final List<Category> entries = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            final ConfigurationSection entry = section.getConfigurationSection(key);
            final String id = key.toLowerCase(Locale.ROOT);
            if (entry == null || !id.matches("[a-z0-9_-]{1,64}") || ALL.equals(id) || UNASSIGNED.equals(id)
                    || !entry.getBoolean("enabled", true)) {
                continue;
            }
            Material icon = Material.matchMaterial(entry.getString("icon", "CHEST"));
            if (icon == null || icon.isAir() || icon.isLegacy() || !icon.isItem()) icon = Material.CHEST;
            entries.add(new Category(id, entry.getString("name", key), icon,
                    entry.getString("description", ""), entry.getInt("order", 100)));
        }
        entries.sort(Comparator.comparingInt((Category category) -> category.order).thenComparing(Category::id));
        for (Category entry : entries) {
            categories.putIfAbsent(entry.id(), entry);
        }
    }

    public List<Category> menu(boolean browse) {
        final List<Category> result = new ArrayList<>();
        if (browse) result.add(ALL_ITEMS);
        result.addAll(categories.values());
        result.add(NO_CATEGORY);
        return result;
    }

    public Category find(String id) {
        if (ALL.equals(id)) return ALL_ITEMS;
        if (UNASSIGNED.equals(id)) return NO_CATEGORY;
        return categories.get(id);
    }

    public Category categoryOf(SerializedItem item) {
        return categories.getOrDefault(item.getCategory(), NO_CATEGORY);
    }

    public List<SerializedItem> items(String id, List<SerializedItem> sorted) {
        if (ALL.equals(id)) return sorted;
        // The storage replaces its sorted snapshot whenever the entries change.
        if (sorted != indexedItems) {
            final Map<String, List<SerializedItem>> grouped = new HashMap<>();
            for (SerializedItem item : sorted) {
                grouped.computeIfAbsent(categoryOf(item).id(), key -> new ArrayList<>()).add(item);
            }
            grouped.replaceAll((key, items) -> Collections.unmodifiableList(items));
            byCategory = grouped;
            indexedItems = sorted;
        }
        return byCategory.getOrDefault(id, Collections.emptyList());
    }
}

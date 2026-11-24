package at.dumphey.itemeditor.engine;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Reads and writes the custom item id and custom enchantments in an item's persistent data. This is the data layer
 * for future custom items and enchantments: the engine only needs these two values to find the rules of an item.
 *
 * <p>Format of the enchantments value: {@code id:level;id:level} (sorted, so the same data is always the same string).
 */
public final class ItemTags {

    public static final Pattern ID = Pattern.compile("[a-z0-9_\\-]{1,48}");
    private static final ItemTagData NONE = null;

    private final NamespacedKey keyItem;
    private final NamespacedKey keyEnchants;

    public ItemTags(Plugin plugin) {
        this.keyItem = new NamespacedKey(plugin, "item_id");
        this.keyEnchants = new NamespacedKey(plugin, "custom_enchants");
    }

    /** Null (without allocating anything) when the item has no custom data. */
    public ItemTagData read(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return NONE;
        }
        return read(item.getItemMeta());
    }

    public ItemTagData read(ItemMeta meta) {
        if (meta == null) {
            return NONE;
        }
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        final String id = pdc.get(keyItem, PersistentDataType.STRING);
        final String enchants = pdc.get(keyEnchants, PersistentDataType.STRING);
        if (id == null && enchants == null) {
            return NONE;
        }
        return new ItemTagData(id, parseEnchants(enchants));
    }

    public void setItemId(ItemMeta meta, String id) {
        if (id == null) {
            meta.getPersistentDataContainer().remove(keyItem);
        } else {
            meta.getPersistentDataContainer().set(keyItem, PersistentDataType.STRING, normalize(id));
        }
    }

    /** level <= 0 removes the enchantment. */
    public void setEnchant(ItemMeta meta, String id, int level) {
        final ItemTagData current = read(meta);
        final Map<String, Integer> map = new TreeMap<>();
        if (current != null) {
            map.putAll(current.getEnchants());
        }
        if (level <= 0) {
            map.remove(normalize(id));
        } else {
            map.put(normalize(id), level);
        }
        if (map.isEmpty()) {
            meta.getPersistentDataContainer().remove(keyEnchants);
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        meta.getPersistentDataContainer().set(keyEnchants, PersistentDataType.STRING, sb.toString());
    }

    public static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    private static Map<String, Integer> parseEnchants(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<String, Integer> map = new LinkedHashMap<>();
        for (String part : raw.split(";")) {
            final int colon = part.lastIndexOf(':');
            if (colon <= 0) {
                continue; // hand-edited / corrupt value: ignore the piece, never throw in an event
            }
            try {
                final int level = Integer.parseInt(part.substring(colon + 1));
                if (level > 0) {
                    map.put(part.substring(0, colon), level);
                }
            } catch (NumberFormatException ignored) {
                // skip it
            }
        }
        return map;
    }
}

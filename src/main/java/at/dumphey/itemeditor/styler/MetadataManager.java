package at.dumphey.itemeditor.styler;

import at.dumphey.itemeditor.styler.model.StyleState;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads/writes the {@link StyleState} in the item's persistent data container.
 * The keys live in the plugin's namespace (ultimateitemseditor). Items styled before the plugin was renamed carry
 * the old namespace (itemeditor): they are still read, and rewritten under the new one on the next change.
 */
public class MetadataManager {

    private static final String SEP = "\u001f";
    private static final String LEGACY_NAMESPACE = "itemeditor";

    private final Keys current;
    private final Keys legacy;

    private static final class Keys {
        private final NamespacedKey template;
        private final NamespacedKey originalName;
        private final NamespacedKey originalLore;
        private final NamespacedKey originalFlags;
        private final NamespacedKey generatedName;
        private final NamespacedKey generatedHead;
        private final NamespacedKey generatedTail;

        private Keys(NamespacedKey template, NamespacedKey originalName, NamespacedKey originalLore,
                     NamespacedKey originalFlags, NamespacedKey generatedName, NamespacedKey generatedHead,
                     NamespacedKey generatedTail) {
            this.template = template;
            this.originalName = originalName;
            this.originalLore = originalLore;
            this.originalFlags = originalFlags;
            this.generatedName = generatedName;
            this.generatedHead = generatedHead;
            this.generatedTail = generatedTail;
        }
    }

    @SuppressWarnings("deprecation")
    public MetadataManager(JavaPlugin plugin) {
        current = new Keys(new NamespacedKey(plugin, "template"), new NamespacedKey(plugin, "orig_name"),
                new NamespacedKey(plugin, "orig_lore"), new NamespacedKey(plugin, "orig_flags"),
                new NamespacedKey(plugin, "gen_name"), new NamespacedKey(plugin, "gen_head"),
                new NamespacedKey(plugin, "gen_tail"));
        legacy = new Keys(new NamespacedKey(LEGACY_NAMESPACE, "template"), new NamespacedKey(LEGACY_NAMESPACE, "orig_name"),
                new NamespacedKey(LEGACY_NAMESPACE, "orig_lore"), new NamespacedKey(LEGACY_NAMESPACE, "orig_flags"),
                new NamespacedKey(LEGACY_NAMESPACE, "gen_name"), new NamespacedKey(LEGACY_NAMESPACE, "gen_head"),
                new NamespacedKey(LEGACY_NAMESPACE, "gen_tail"));
    }

    /** The state of a styled item, or null if no template was applied to it. */
    public StyleState read(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        final StyleState state = read(pdc, current);
        return state != null ? state : read(pdc, legacy);
    }

    private static StyleState read(PersistentDataContainer pdc, Keys keys) {
        final String template = pdc.get(keys.template, PersistentDataType.STRING);
        if (template == null) {
            return null;
        }
        final Integer flags = pdc.get(keys.originalFlags, PersistentDataType.INTEGER);
        return new StyleState(template,
                pdc.get(keys.originalName, PersistentDataType.STRING),
                split(pdc.get(keys.originalLore, PersistentDataType.STRING)),
                flags == null ? 0 : flags,
                pdc.get(keys.generatedName, PersistentDataType.STRING),
                split(pdc.get(keys.generatedHead, PersistentDataType.STRING)),
                split(pdc.get(keys.generatedTail, PersistentDataType.STRING)));
    }

    public void write(ItemMeta meta, StyleState state) {
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        remove(pdc, legacy);
        pdc.set(current.template, PersistentDataType.STRING, state.getTemplate());
        pdc.set(current.originalFlags, PersistentDataType.INTEGER, state.getOriginalFlags());
        setOrRemove(pdc, current.originalName, state.getOriginalName());
        setOrRemove(pdc, current.generatedName, state.getGeneratedName());
        setOrRemove(pdc, current.originalLore, join(state.getOriginalLore()));
        setOrRemove(pdc, current.generatedHead, join(state.getGeneratedHead()));
        setOrRemove(pdc, current.generatedTail, join(state.getGeneratedTail()));
    }

    public void clear(ItemMeta meta) {
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        remove(pdc, current);
        remove(pdc, legacy);
    }

    public String getAppliedTemplate(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        final String template = pdc.get(current.template, PersistentDataType.STRING);
        return template != null ? template : pdc.get(legacy.template, PersistentDataType.STRING);
    }

    private static void remove(PersistentDataContainer pdc, Keys keys) {
        pdc.remove(keys.template);
        pdc.remove(keys.originalName);
        pdc.remove(keys.originalLore);
        pdc.remove(keys.originalFlags);
        pdc.remove(keys.generatedName);
        pdc.remove(keys.generatedHead);
        pdc.remove(keys.generatedTail);
    }

    private static void setOrRemove(PersistentDataContainer pdc, NamespacedKey key, String value) {
        if (value == null) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, value);
        }
    }

    /** An empty list is stored as "no value", a list with one empty line as "". */
    private static String join(List<String> lines) {
        return lines.isEmpty() ? null : String.join(SEP, lines);
    }

    private static List<String> split(String raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(raw.split(SEP, -1)));
    }
}

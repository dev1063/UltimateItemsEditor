package at.dumphey.itemeditor.styler;

import at.dumphey.itemeditor.styler.model.AttributeStyle;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * enchantsstyle/attributes.yml (section "attributes"). Each child is either an attribute style itself
 * ("attack_damage:") or a group of them ("generic: attack_damage:"). Keys are matched by their last segment, so
 * "generic.attack_damage" (old registry key) and "attack_damage" (1.21.2+) are the same attribute.
 */
public class AttributeStyleManager {

    private final JavaPlugin plugin;
    private final Map<String, AttributeStyle> styles = new LinkedHashMap<>();

    public AttributeStyleManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        styles.clear();
        final File file = new File(plugin.getDataFolder(), "enchantsstyle" + File.separator + "attributes.yml");
        if (!file.exists()) {
            return;
        }
        final YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(file);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load attributes.yml", ex);
            return;
        }
        final ConfigurationSection root = yaml.getConfigurationSection("attributes");
        if (root == null) {
            plugin.getLogger().warning("attributes.yml has no 'attributes' section.");
            return;
        }
        for (String key : root.getKeys(false)) {
            final ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            if (looksLikeStyle(s)) {
                put(key, s);
            } else {
                for (String child : s.getKeys(false)) {
                    final ConfigurationSection c = s.getConfigurationSection(child);
                    if (c != null) {
                        put(child, c);
                    }
                }
            }
        }
    }

    private static boolean looksLikeStyle(ConfigurationSection s) {
        return s.contains("format") || s.contains("display") || s.contains("icon") || s.contains("enabled")
                || s.contains("priority");
    }

    private void put(String key, ConfigurationSection a) {
        final String normalized = normalize(key);
        styles.put(normalized, new AttributeStyle(normalized, a.getBoolean("enabled", true),
                a.getString("display", Text.title(normalized)), a.getString("icon", ""),
                a.getString("format", "&7%icon% %value% &7%display%"), a.getInt("priority", 50)));
    }

    /** The configured style, or a generic one for attributes that have none. Check isEnabled() before use. */
    public AttributeStyle getStyle(Attribute attribute) {
        final String key = normalize(attribute.getKey().getKey());
        final AttributeStyle style = styles.get(key);
        if (style != null) {
            return style;
        }
        return new AttributeStyle(key, true, Text.title(key), "\u25c8", "&7%icon% %value% &7%display%", 999);
    }

    public int count() {
        return styles.size();
    }

    /** "generic.attack_damage" -> "attack_damage" */
    public static String normalize(String key) {
        final String k = key.toLowerCase();
        final int dot = k.lastIndexOf('.');
        return dot >= 0 ? k.substring(dot + 1) : k;
    }
}

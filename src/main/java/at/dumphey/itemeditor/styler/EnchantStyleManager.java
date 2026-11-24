package at.dumphey.itemeditor.styler;

import at.dumphey.itemeditor.styler.model.EnchantStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * enchantsstyle/vanilla.yml  : one style per minecraft: enchantment (section "enchants").
 * enchantsstyle/custom.yml   : default-format for unknown enchantments, plugin-formats per namespace and
 *                              overrides for single enchantments ("namespace:key").
 */
public class EnchantStyleManager {

    private static final String DEFAULT_VANILLA_FORMAT = "&8\u00bb &7%name% %level_roman%";

    private final JavaPlugin plugin;
    private final Map<String, EnchantStyle> vanilla = new LinkedHashMap<>();
    private final Map<String, EnchantStyle> overrides = new LinkedHashMap<>();
    private final Map<String, String> pluginFormats = new HashMap<>();
    private final Map<String, Integer> pluginPriorities = new HashMap<>();
    private String defaultCustomFormat = "&8\u00bb &d%name% %level_roman%";

    public EnchantStyleManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        vanilla.clear();
        overrides.clear();
        pluginFormats.clear();
        pluginPriorities.clear();
        defaultCustomFormat = "&8\u00bb &d%name% %level_roman%";
        loadVanilla();
        loadCustom();
    }

    public EnchantStyle getStyle(Enchantment enchant) {
        final String namespace = enchant.getKey().getNamespace();
        final String key = enchant.getKey().getKey();
        final String fullKey = namespace + ":" + key;

        final EnchantStyle override = overrides.get(fullKey);
        if (override != null) {
            return override;
        }
        if ("minecraft".equals(namespace) && vanilla.containsKey(key)) {
            return vanilla.get(key);
        }
        if (pluginFormats.containsKey(namespace)) {
            return dynamic(enchant, pluginFormats.get(namespace), pluginPriorities.getOrDefault(namespace, 150));
        }
        return dynamic(enchant, defaultCustomFormat, 999);
    }

    public int vanillaCount() {
        return vanilla.size();
    }

    public int customCount() {
        return overrides.size();
    }

    private void loadVanilla() {
        final YamlConfiguration yaml = read("enchantsstyle" + File.separator + "vanilla.yml");
        if (yaml == null) {
            return;
        }
        final ConfigurationSection section = yaml.getConfigurationSection("enchants");
        if (section == null) {
            plugin.getLogger().warning("vanilla.yml has no 'enchants' section.");
            return;
        }
        for (String key : section.getKeys(false)) {
            final ConfigurationSection e = section.getConfigurationSection(key);
            if (e == null) {
                continue;
            }
            vanilla.put(key.toLowerCase(), new EnchantStyle(key.toLowerCase(), e.getBoolean("enabled", true),
                    e.getString("display", Text.title(key)), e.getString("description", ""),
                    e.getString("format", DEFAULT_VANILLA_FORMAT), e.getInt("priority", 100)));
        }
    }

    private void loadCustom() {
        final YamlConfiguration yaml = read("enchantsstyle" + File.separator + "custom.yml");
        if (yaml == null) {
            return;
        }
        final String def = yaml.getString("default-format");
        if (def != null) {
            defaultCustomFormat = def;
        }
        final ConfigurationSection plugins = yaml.getConfigurationSection("plugin-formats");
        if (plugins != null) {
            for (String ns : plugins.getKeys(false)) {
                final ConfigurationSection p = plugins.getConfigurationSection(ns);
                if (p != null) {
                    pluginFormats.put(ns.toLowerCase(), p.getString("format", defaultCustomFormat));
                    pluginPriorities.put(ns.toLowerCase(), p.getInt("priority", 150));
                }
            }
        }
        final ConfigurationSection section = yaml.getConfigurationSection("overrides");
        if (section != null) {
            for (String fullKey : section.getKeys(false)) {
                final ConfigurationSection e = section.getConfigurationSection(fullKey);
                if (e != null) {
                    overrides.put(fullKey.toLowerCase(), new EnchantStyle(fullKey.toLowerCase(),
                            e.getBoolean("enabled", true), e.getString("display", fullKey),
                            e.getString("description", ""), e.getString("format", defaultCustomFormat),
                            e.getInt("priority", 140)));
                }
            }
        }
    }

    private static EnchantStyle dynamic(Enchantment enchant, String format, int priority) {
        return new EnchantStyle(enchant.getKey().toString(), true, Text.title(enchant.getKey().getKey()), "",
                format, priority);
    }

    private YamlConfiguration read(String relativePath) {
        final File file = new File(plugin.getDataFolder(), relativePath);
        if (!file.exists()) {
            return null;
        }
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load " + file.getName(), ex);
            return null;
        }
    }
}

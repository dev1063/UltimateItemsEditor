package at.dumphey.itemeditor.styler;

import at.dumphey.itemeditor.styler.model.Template;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads templates from palettes.yml (section "palettes") and from palettes/&lt;name&gt;.yml (one template per file,
 * the file name is the template name; the file's own keys are the template's keys).
 */
public class TemplateManager {

    private final JavaPlugin plugin;
    private final Map<String, Template> templates = new LinkedHashMap<>();

    public TemplateManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        templates.clear();
        loadMonolithic();
        loadDirectory();
        plugin.getLogger().info("Templates: " + templates.size() + " loaded.");
    }

    private void loadMonolithic() {
        final File file = new File(plugin.getDataFolder(), "palettes.yml");
        if (!file.exists()) {
            return;
        }
        final YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(file);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load palettes.yml", ex);
            return;
        }
        final ConfigurationSection section = yaml.getConfigurationSection("palettes");
        if (section == null) {
            plugin.getLogger().warning("palettes.yml has no 'palettes' section - skipping.");
            return;
        }
        for (String key : section.getKeys(false)) {
            final ConfigurationSection p = section.getConfigurationSection(key);
            if (p != null) {
                templates.put(key.toLowerCase(), parse(key.toLowerCase(), p));
            }
        }
    }

    private void loadDirectory() {
        final File dir = new File(plugin.getDataFolder(), "palettes");
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (!f.getName().toLowerCase().endsWith(".yml")) {
                continue;
            }
            final String name = f.getName().substring(0, f.getName().length() - 4).toLowerCase();
            try {
                final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
                templates.put(name, parse(name, yaml));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load template file " + f.getName(), ex);
            }
        }
    }

    private static Template parse(String name, ConfigurationSection p) {
        final String icon = p.getString("icon");
        return new Template(name, Text.isBlank(icon) ? null : icon.trim(),
                p.getString("display-name-prefix", ""),
                p.getStringList("header"), p.getStringList("footer"),
                p.getBoolean("enchant-spacing", false), p.getBoolean("attribute-spacing", true),
                p.getBoolean("hide-enchants", false), p.getBoolean("hide-attributes", false));
    }

    public Template get(String name) {
        return name == null ? null : templates.get(name.toLowerCase());
    }

    public List<String> names() {
        return new ArrayList<>(templates.keySet());
    }

    public List<Template> all() {
        return new ArrayList<>(templates.values());
    }

    public int count() {
        return templates.size();
    }
}

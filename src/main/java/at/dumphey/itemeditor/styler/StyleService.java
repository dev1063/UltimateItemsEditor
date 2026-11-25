package at.dumphey.itemeditor.styler;

import at.dumphey.itemeditor.styler.model.Template;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

/**
 * Entry point of the template system: owns the language, templates, enchant/attribute styles and the lore
 * builder, and (re)loads them from plugins/UltimateItemsEditor.
 */
public class StyleService {

    private static final String[] DEFAULT_RESOURCES = {
            "palettes.yml",
            "enchantsstyle/vanilla.yml", "enchantsstyle/custom.yml", "enchantsstyle/attributes.yml"};
    private static final String[] MESSAGE_RESOURCES = {"messages/english.yml", "messages/spanish.yml"};

    private final JavaPlugin plugin;
    private final boolean vanilla;
    private StyleConfig config;
    private LanguageManager lang;
    private TemplateManager templates;
    private EnchantStyleManager enchants;
    private AttributeStyleManager attributes;
    private MetadataManager metadata;
    private LoreBuilder loreBuilder;

    public StyleService(JavaPlugin plugin) {
        this(plugin, true);
    }

    public StyleService(JavaPlugin plugin, boolean vanilla) {
        this.plugin = plugin;
        this.vanilla = vanilla;
    }

    /** Creates the default files that are missing and loads everything. Call once from onEnable. */
    public void load() {
        plugin.saveDefaultConfig();
        extractDefaultResources(MESSAGE_RESOURCES);
        config = new StyleConfig(plugin);
        lang = new LanguageManager(plugin, config);
        lang.load();
        if (!vanilla) {
            return;
        }
        extractDefaultResources(DEFAULT_RESOURCES);
        templates = new TemplateManager(plugin);
        enchants = new EnchantStyleManager(plugin);
        attributes = new AttributeStyleManager(plugin);
        metadata = new MetadataManager(plugin);
        loreBuilder = new LoreBuilder(config, lang, enchants, attributes, metadata);

        templates.load();
        enchants.load();
        attributes.load();
        plugin.getLogger().info("Language: " + lang.getActiveLang() + " | templates: " + templates.count()
                + " | enchant styles: " + enchants.vanillaCount() + " vanilla, " + enchants.customCount()
                + " custom | attribute styles: " + attributes.count());
    }

    public void reload() {
        extractDefaultResources(MESSAGE_RESOURCES);
        config.reload();
        lang.load();
        if (!vanilla) {
            return;
        }
        extractDefaultResources(DEFAULT_RESOURCES);
        templates.load();
        enchants.load();
        attributes.load();
    }

    private void extractDefaultResources(String[] resources) {
        for (String path : resources) {
            final File target = new File(plugin.getDataFolder(), path.replace("/", File.separator));
            if (target.exists()) {
                continue;
            }
            try {
                plugin.saveResource(path, false);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Could not extract default file " + path, ex);
            }
        }
    }

    public boolean apply(ItemStack item, Template template) {
        return vanilla && loreBuilder.apply(item, template);
    }

    public boolean reset(ItemStack item) {
        return vanilla && loreBuilder.reset(item);
    }

    /** Switches to the next available language (saved to config.yml) and returns its name. */
    public String cycleLanguage() {
        final List<String> languages = lang.availableLanguages();
        final int index = languages.indexOf(lang.getActiveLang());
        final String next = languages.get((index + 1) % languages.size());
        return lang.setLanguage(next) ? next : lang.getActiveLang();
    }

    public boolean isVanillaStyleEnabled() {
        return vanilla;
    }

    public StyleConfig getConfig() {
        return config;
    }

    public LanguageManager getLang() {
        return lang;
    }

    public TemplateManager getTemplates() {
        return templates;
    }

    public EnchantStyleManager getEnchants() {
        return enchants;
    }

    public AttributeStyleManager getAttributes() {
        return attributes;
    }

    public MetadataManager getMetadata() {
        return metadata;
    }

    public LoreBuilder getLoreBuilder() {
        return loreBuilder;
    }
}

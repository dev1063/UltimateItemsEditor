package at.dumphey.itemeditor.styler;

import org.bukkit.plugin.java.JavaPlugin;

/** Typed access to config.yml (always reads the live config, so a reload is picked up automatically). */
public class StyleConfig {

    private final JavaPlugin plugin;

    public StyleConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public String getLanguage() {
        return plugin.getConfig().getString("language", "english").toLowerCase();
    }

    public void setLanguage(String language) {
        plugin.getConfig().set("language", language.toLowerCase());
        plugin.saveConfig();
    }

    public String getDefaultTemplate() {
        return plugin.getConfig().getString("default-template", "legendary").toLowerCase();
    }

    public boolean isHideEnchantsGlobally() {
        return plugin.getConfig().getBoolean("hide-enchants-globally", false);
    }

    public boolean isHideAttributesGlobally() {
        return plugin.getConfig().getBoolean("hide-attributes-globally", false);
    }

    public int getAttributeDecimalPlaces() {
        return Math.max(0, Math.min(6, plugin.getConfig().getInt("attribute-decimal-places", 2)));
    }

    /** Keep the item's own lore between the template's header and the generated enchant/attribute lines. */
    public boolean isKeepItemLore() {
        return plugin.getConfig().getBoolean("keep-item-lore", true);
    }
}

package at.dumphey.itemeditor.itemeditor.modules.home;

import org.bukkit.Material;

/**
 * A tab of the editor home menu. The text can be translated: put {@code gui-cat-<id>} and {@code gui-cat-<id>-desc}
 * in a language file; the texts given here are the fallback.
 */
public final class EditorCategory {

    private final String id;
    private final String name;
    private final String description;
    private final Material icon;
    private final Material accent;
    private final int order;
    private final String disabledModule;

    /**
     * @param id     lowercase id, used to register options
     * @param icon   the tab's item
     * @param accent a stained glass pane: the colored rail under the tabs while this tab is open
     * @param order  tabs are shown from the lowest to the highest (the defaults use 10, 20, 30 to leave room)
     */
    public EditorCategory(String id, String name, String description, Material icon, Material accent, int order) {
        this(id, name, description, icon, accent, order, null);
    }

    public EditorCategory(String id, String name, String description, Material icon, Material accent, int order,
                          String disabledModule) {
        this.disabledModule = disabledModule;
        this.id = id.toLowerCase(java.util.Locale.ROOT);
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.accent = accent;
        this.order = order;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return disabledModule == null ? icon : Material.ORANGE_STAINED_GLASS_PANE;
    }

    public Material getAccent() {
        return disabledModule == null ? accent : Material.ORANGE_STAINED_GLASS_PANE;
    }

    public String getDisabledModule() {
        return disabledModule;
    }

    public int getOrder() {
        return order;
    }
}

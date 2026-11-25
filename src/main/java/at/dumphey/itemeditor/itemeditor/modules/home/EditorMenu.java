package at.dumphey.itemeditor.itemeditor.modules.home;

import at.dumphey.itemeditor.itemeditor.modules.attributes.items.OpenManageAttributesUiItem;
import at.dumphey.itemeditor.itemeditor.modules.behavior.BehaviorEditor;
import at.dumphey.itemeditor.itemeditor.modules.components.ItemComponentsUiScreen;
import at.dumphey.itemeditor.itemeditor.modules.book.items.OpenManageBookUiItem;
import at.dumphey.itemeditor.itemeditor.modules.durability.SetDurabilityItem;
import at.dumphey.itemeditor.itemeditor.modules.enchantments.CustomEnchantUiScreen;
import at.dumphey.itemeditor.itemeditor.modules.enchantments.items.OpenManageEnchantmentsUiItem;
import at.dumphey.itemeditor.itemeditor.modules.flags.items.OpenManageItemFlagsUiItem;
import at.dumphey.itemeditor.itemeditor.modules.leather.SetLeatherColorUiItem;
import at.dumphey.itemeditor.itemeditor.modules.lore.items.OpenManageLoreUiItem;
import at.dumphey.itemeditor.itemeditor.modules.name.ChangeItemNameUiItem;
import at.dumphey.itemeditor.itemeditor.modules.potions.items.OpenManagePotionUiItem;
import at.dumphey.itemeditor.itemeditor.modules.skull.SetSkullNameUiItem;
import at.dumphey.itemeditor.itemeditor.modules.template.items.OpenTemplatesUiItem;
import at.dumphey.itemeditor.itemeditor.modules.unbreakable.ToggleUnbreakableUiItem;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.utils.ItemBuilder;
import at.dumphey.itemeditor.ui.template.UiScreen;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What the editor home menu shows: categories (tabs) holding options (buttons).
 *
 * <p>Adding a feature to the editor is one line:
 * <pre>
 * ItemEditor.INSTANCE.getEditorMenu().registerOption("properties", "my_option", 50, MyOptionUiItem::new);
 * </pre>
 * Options are listed by {@code order} inside their category (the defaults use 10, 20, 30... so you can slot yours
 * in between); a category with more than 27 options gets pages, and more than 7 categories get a "more" tab.
 * A new category needs {@link #registerCategory} first.
 */
public final class EditorMenu {

    /** Builds the button for one player's menu. */
    @FunctionalInterface
    public interface OptionFactory {
        UiItem create(UiScreen screen, Player player);
    }

    /** One button of the menu. */
    public static final class Option {
        private final String id;
        private final String categoryId;
        private final int order;
        private final OptionFactory factory;

        private Option(String id, String categoryId, int order, OptionFactory factory) {
            this.id = id;
            this.categoryId = categoryId;
            this.order = order;
            this.factory = factory;
        }

        public String getId() {
            return id;
        }

        public String getCategoryId() {
            return categoryId;
        }

        public int getOrder() {
            return order;
        }

        public UiItem create(UiScreen screen, Player player) {
            return factory.create(screen, player);
        }
    }

    /** Where a player was in the menu, so coming back from a submenu lands on the same tab and page. */
    public static final class View {
        private String categoryId;
        private int page;
        private int tabStart;

        public String getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(String categoryId) {
            this.categoryId = categoryId;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getTabStart() {
            return tabStart;
        }

        public void setTabStart(int tabStart) {
            this.tabStart = tabStart;
        }
    }

    private static final Comparator<Option> OPTION_ORDER = new Comparator<Option>() {
        @Override
        public int compare(Option a, Option b) {
            final int byOrder = Integer.compare(a.order, b.order);
            return byOrder != 0 ? byOrder : a.id.compareTo(b.id);
        }
    };

    private final Map<String, EditorCategory> categories = new LinkedHashMap<>();
    private final Map<String, List<Option>> options = new HashMap<>();
    private final Map<UUID, View> views = new HashMap<>();
    private List<EditorCategory> sortedCategories;

    public void registerCategory(EditorCategory category) {
        categories.put(category.getId(), category);
        sortedCategories = null;
    }

    /** Adds (or replaces, by id) an option. The category must exist. */
    public void registerOption(String categoryId, String optionId, int order, OptionFactory factory) {
        final String category = categoryId.toLowerCase(Locale.ROOT);
        if (!categories.containsKey(category)) {
            throw new IllegalArgumentException("Unknown editor category '" + categoryId + "': register it first");
        }
        unregisterOption(optionId);
        List<Option> list = options.get(category);
        if (list == null) {
            list = new ArrayList<>();
            options.put(category, list);
        }
        list.add(new Option(optionId, category, order, factory));
        Collections.sort(list, OPTION_ORDER);
        sortedCategories = null;
    }

    public boolean unregisterOption(String optionId) {
        boolean removed = false;
        for (List<Option> list : options.values()) {
            removed |= list.removeIf(o -> o.id.equals(optionId));
        }
        sortedCategories = null;
        return removed;
    }

    /** Categories that have at least one option, in display order. Do not modify the list. */
    public List<EditorCategory> categories() {
        if (sortedCategories == null) {
            final List<EditorCategory> list = new ArrayList<>();
            for (EditorCategory c : categories.values()) {
                final List<Option> o = options.get(c.getId());
                if (c.getDisabledModule() != null || (o != null && !o.isEmpty())) {
                    list.add(c);
                }
            }
            Collections.sort(list, new Comparator<EditorCategory>() {
                @Override
                public int compare(EditorCategory a, EditorCategory b) {
                    final int byOrder = Integer.compare(a.getOrder(), b.getOrder());
                    return byOrder != 0 ? byOrder : a.getId().compareTo(b.getId());
                }
            });
            sortedCategories = Collections.unmodifiableList(list);
        }
        return sortedCategories;
    }

    public EditorCategory category(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    /** The options of a category in display order. Do not modify the list. */
    public List<Option> options(String categoryId) {
        final List<Option> list = categoryId == null ? null : options.get(categoryId.toLowerCase(Locale.ROOT));
        return list == null ? Collections.<Option>emptyList() : list;
    }

    public View view(UUID player) {
        View view = views.get(player);
        if (view == null) {
            view = new View();
            views.put(player, view);
        }
        return view;
    }

    public void forget(UUID player) {
        views.remove(player);
    }

    /** The menu with every option the editor has always had, grouped by category. */
    public static EditorMenu withDefaults() {
        return withDefaults(true, true);
    }

    public static EditorMenu withDefaults(boolean vanilla, boolean customCommands) {
        return withDefaults(vanilla, customCommands, false);
    }

    public static EditorMenu withDefaults(boolean vanilla, boolean customCommands, boolean customEnchantments) {
        return withDefaults(vanilla, customCommands, customEnchantments, customCommands, true);
    }

    public static EditorMenu withDefaults(boolean vanilla, boolean customCommands, boolean customEnchantments,
                                          boolean consumables, boolean modernApi) {
        final EditorMenu menu = new EditorMenu();

        menu.registerCategory(new EditorCategory("appearance", "Appearance", "Name, lore, flags and templates",
                Material.NAME_TAG, Material.LIGHT_BLUE_STAINED_GLASS_PANE, 10, vanilla ? null : "vanilla"));
        menu.registerCategory(new EditorCategory("properties", "Properties", "Enchantments, attributes and durability",
                Material.ENCHANTING_TABLE, Material.ORANGE_STAINED_GLASS_PANE, 20, vanilla ? null : "vanilla"));
        menu.registerCategory(new EditorCategory("special", "Special items", "Potions, books, heads and leather armor",
                Material.BREWING_STAND, Material.PURPLE_STAINED_GLASS_PANE, 30, vanilla ? null : "vanilla"));
        menu.registerCategory(new EditorCategory("behavior", "Comportamiento", "Habilidades y efectos de ítems",
                Material.COMMAND_BLOCK, Material.LIME_STAINED_GLASS_PANE, 40, customCommands ? null : "custom-commands"));
        menu.registerCategory(new EditorCategory("custom-enchantments", "Encantamientos custom", "Aplica encantamientos del catálogo",
                Material.ENCHANTED_BOOK, Material.MAGENTA_STAINED_GLASS_PANE, 50, customEnchantments ? null : "custom-enchantments"));
        menu.registerCategory(new EditorCategory("consumables", "Consumibles", "Premios para tiendas, cajas y misiones",
                Material.HONEY_BOTTLE, Material.YELLOW_STAINED_GLASS_PANE, 45, consumables ? null : "consumables"));

        if (vanilla) {
            menu.registerOption("appearance", "name", 10, ChangeItemNameUiItem::new);
            menu.registerOption("appearance", "lore", 20, OpenManageLoreUiItem::new);
            menu.registerOption("appearance", "flags", 30, OpenManageItemFlagsUiItem::new);
            if (modernApi) {
                menu.registerOption("appearance", "template", 40, OpenTemplatesUiItem::new);
            }

            menu.registerOption("properties", "enchantments", 10, OpenManageEnchantmentsUiItem::new);
            if (modernApi) {
                menu.registerOption("properties", "attributes", 20, OpenManageAttributesUiItem::new);
            }
            menu.registerOption("properties", "durability", 30, SetDurabilityItem::new);
            menu.registerOption("properties", "unbreakable", 40, ToggleUnbreakableUiItem::new);

            menu.registerOption("special", "potions", 10, OpenManagePotionUiItem::new);
            menu.registerOption("special", "book", 20, OpenManageBookUiItem::new);
            menu.registerOption("special", "skull", 30, SetSkullNameUiItem::new);
            menu.registerOption("special", "leather", 40, SetLeatherColorUiItem::new);
            if (modernApi) {
                menu.registerOption("special", "components", 50, (screen, player) -> new ActionUiItem(screen, player,
                        ItemBuilder.uiItem(Material.COMPARATOR, "Componentes nativos", "Comida, herramientas, rareza y modelos"),
                        () -> at.dumphey.itemeditor.itemeditor.ItemEditor.INSTANCE.getUiManager()
                                .open(player, new ItemComponentsUiScreen(player))));
            }
        }

        if (customCommands) {
            menu.registerOption("behavior", "customitems", 10, (screen, player) -> new ActionUiItem(screen, player,
                    ItemBuilder.uiItem(Material.NETHER_STAR, "Ítems personalizados", "Crea ítems con habilidades"),
                    () -> new BehaviorEditor(player).open(false)));

        }

        if (customEnchantments) {
            menu.registerOption("custom-enchantments", "catalog", 10, (screen, player) -> new ActionUiItem(screen, player,
                    ItemBuilder.uiItem(Material.ENCHANTED_BOOK, "Catálogo de encantamientos"),
                    () -> at.dumphey.itemeditor.itemeditor.ItemEditor.INSTANCE.getUiManager()
                            .open(player, new CustomEnchantUiScreen(player))));
        }

        if (consumables) {
            menu.registerOption("consumables", "consumables", 20, (screen, player) -> new ActionUiItem(screen, player,
                    ItemBuilder.uiItem(Material.HONEY_BOTTLE, "Consumibles", "Ejecuta acciones al usar el ítem"),
                    () -> new BehaviorEditor(player).open(true)));
        }

        return menu;
    }
}

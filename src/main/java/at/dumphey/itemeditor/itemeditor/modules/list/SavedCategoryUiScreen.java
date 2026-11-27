package at.dumphey.itemeditor.itemeditor.modules.list;

import at.dumphey.itemeditor.UltimateItemsEditor;
import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.template.ConfirmUiScreen;
import at.dumphey.itemeditor.itemeditor.storage.ItemStorage;
import at.dumphey.itemeditor.itemeditor.storage.SavedItemCategories;
import at.dumphey.itemeditor.itemeditor.storage.SavedItemCategories.Category;
import at.dumphey.itemeditor.itemeditor.storage.SerializedItem;
import at.dumphey.itemeditor.ui.design.PageBar;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.items.FillerUiItem;
import at.dumphey.itemeditor.ui.items.StaticUiItem;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import at.dumphey.itemeditor.utils.Messages;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class SavedCategoryUiScreen extends UiScreen {

    private static final int PAGE_SIZE = 45;

    private final Consumer<Category> onSelect;
    private final ItemStack preview;
    private final Runnable onCancel;
    private int page;

    public SavedCategoryUiScreen(Player player) {
        this(player, "Ítems guardados", null, null, null);
    }

    SavedCategoryUiScreen(Player player, String title, ItemStack preview, Consumer<Category> onSelect,
                          Runnable onCancel) {
        super(player, 54, title);
        this.preview = preview;
        this.onSelect = onSelect;
        this.onCancel = onCancel;
    }

    public static UiScreen forSave(Player player, String name, ItemStack hand) {
        final ItemEditor editor = ItemEditor.INSTANCE;
        final ItemStorage storage = editor.getItemStorage();
        final ItemStack snapshot = hand.clone();
        return new SavedCategoryUiScreen(player, "Guardar ítem", snapshot, category -> {
            final SerializedItem existing = storage.getItem(name);
            if (existing == null) {
                saveAndBrowse(player, name, snapshot, category);
                return;
            }
            editor.getUiManager().open(player, new ConfirmUiScreen(player, "Reemplazar ítem",
                    "Reemplazar " + existing.getName(), "Se sustituirá la copia guardada.",
                    () -> {
                        if (storage.getItem(name) != existing) {
                            Messages.send(player, "§cLa entrada cambió. Selecciona la categoría otra vez.");
                            editor.getUiManager().open(player, forSave(player, name, snapshot));
                            return;
                        }
                        saveAndBrowse(player, name, snapshot, category);
                    },
                    () -> editor.getUiManager().open(player, forSave(player, name, snapshot))));
        }, () -> editor.getUiManager().open(player, new SavedCategoryUiScreen(player)));
    }

    private static void saveAndBrowse(Player player, String name, ItemStack snapshot, Category category) {
        if (persist(player, name, snapshot, category)) {
            ItemEditor.INSTANCE.getUiManager().open(player, new ItemListUiScreen(player, category.id(), 0));
        }
    }

    static boolean persist(Player player, String name, ItemStack stack, Category selection) {
        final UltimateItemsEditor plugin = UltimateItemsEditor.getInstance();
        final Category category = plugin.savedItemCategories().find(selection.id());
        if (category == null || SavedItemCategories.ALL.equals(category.id())) {
            Messages.send(player, "§cEsta categoría ya no está disponible.");
            return false;
        }
        final String categoryId = SavedItemCategories.UNASSIGNED.equals(category.id()) ? null : category.id();
        try {
            final ItemStorage storage = ItemEditor.INSTANCE.getItemStorage();
            storage.saveItem(name, stack, categoryId).whenCompleteAsync((result, error) -> {
                if (!player.isOnline()) return;
                if (error != null) {
                    Messages.send(player, "§cNo se pudo guardar " + name + ". Revisa la consola.");
                    final UiScreen current = ItemEditor.INSTANCE.getUiManager().getScreen(player);
                    if ((current instanceof ItemListUiScreen || current instanceof ItemActionsUiScreen)
                            && current.isInventory(player.getOpenInventory().getTopInventory())) {
                        current.update();
                    }
                    return;
                }
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                Messages.send(player, "§a" + name + " guardado en §f" + category.name() + "§a.");
            }, storage.mainThreadExecutor());
            return true;
        } catch (IOException | IllegalStateException e) {
            plugin.getLogger().log(Level.WARNING, "Could not queue saved item '" + name + "'", e);
            Messages.send(player, "§cNo se pudo guardar " + name + ". Revisa la consola.");
            return false;
        }
    }

    @Override
    protected void onUpdate() {
        final SavedItemCategories catalog = UltimateItemsEditor.getInstance().savedItemCategories();
        final List<SerializedItem> items = ItemEditor.INSTANCE.getItemStorage().getSortedItems();
        final List<Category> categories = catalog.menu(onSelect == null);
        final int pageCount = PageBar.pages(categories.size(), PAGE_SIZE);
        page = Math.min(page, pageCount - 1);
        final int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, categories.size()); index++) {
            final Category category = categories.get(index);
            final int count = catalog.items(category.id(), items).size();
            setItem(index - start, new ActionUiItem(this, player,
                    ItemBuilder.uiItem(category.icon(), category.name(),
                            category.description().trim().isEmpty() ? count + " ítems" : category.description() + "\n" + count + " ítems"),
                    () -> select(category)));
        }
        if (preview != null) setItem(45, new StaticUiItem(this, player, preview));
        final String detail = onSelect == null ? "Categorías" : "Elige la categoría de destino";
        final UiItem[] bar = PageBar.create(this, player, page, pageCount, detail, newPage -> {
            page = newPage;
            update();
        });
        setItem(48, bar[0]);
        setItem(49, bar[1]);
        setItem(50, bar[2]);
        if (onCancel != null) {
            setItem(53, new ActionUiItem(this, player, ItemBuilder.uiItem(Material.ARROW, "Volver"), onCancel));
        }
        fillEmpty(45, 53, new FillerUiItem(this, player));
    }

    private void select(Category category) {
        final Category current = UltimateItemsEditor.getInstance().savedItemCategories().find(category.id());
        if (current == null) {
            Messages.send(player, "§cEsta categoría ya no está disponible.");
            update();
        } else if (onSelect == null) {
            open(new ItemListUiScreen(player, current.id(), 0));
        } else {
            onSelect.accept(current);
        }
    }
}

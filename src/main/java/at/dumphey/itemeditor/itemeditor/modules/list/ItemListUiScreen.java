package at.dumphey.itemeditor.itemeditor.modules.list;

import at.dumphey.itemeditor.UltimateItemsEditor;
import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.list.items.ManageItemFromListUiItem;
import at.dumphey.itemeditor.itemeditor.modules.template.ConfirmUiScreen;
import at.dumphey.itemeditor.itemeditor.modules.template.TemplatePickerUiScreen;
import at.dumphey.itemeditor.itemeditor.storage.SavedItemCategories;
import at.dumphey.itemeditor.itemeditor.storage.SerializedItem;
import at.dumphey.itemeditor.styler.LanguageManager;
import at.dumphey.itemeditor.styler.model.Template;
import at.dumphey.itemeditor.ui.UiPosition;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.design.PageBar;
import at.dumphey.itemeditor.ui.items.FillerUiItem;
import at.dumphey.itemeditor.ui.items.StaticUiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Paginated contents of a category or of the complete saved collection. */
public class ItemListUiScreen extends UiScreen {

    private static final int PAGE_SIZE = 45;

    private final String categoryId;
    private int page;

    public ItemListUiScreen(Player player, String categoryId, int page) {
        super(player, 54, "Ítems guardados");
        this.categoryId = categoryId;
        this.page = Math.max(0, page);
    }

    @Override
    protected void onUpdate() {
        final SavedItemCategories catalog = UltimateItemsEditor.getInstance().savedItemCategories();
        final SavedItemCategories.Category category = catalog.find(categoryId);
        final List<SerializedItem> items = catalog.items(categoryId,
                ItemEditor.INSTANCE.getItemStorage().getSortedItems());
        setItem(45, new ActionUiItem(this, player, ItemBuilder.uiItem(Material.ARROW, "Categorías"),
                () -> open(new SavedCategoryUiScreen(player))));
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        final int pageCount = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pageCount) {
            page = pageCount - 1; // e.g. after deleting the last item of the last page
        }

        int slot = 0;
        for (int i = page * PAGE_SIZE; i < items.size() && i < (page + 1) * PAGE_SIZE; i++) {
            setItem(slot++, new ManageItemFromListUiItem(this, player, items.get(i), categoryId, page));
        }

        if (items.isEmpty()) {
            setItem(22, new StaticUiItem(this, player, ItemBuilder.uiItem(Material.BARRIER, "No hay ítems en esta categoría",
                    "Usa /ie save <nombre> para guardar un ítem.")));
        }

        final String label = category == null ? "Categoría retirada" : category.name();
        final UiItem[] bar = PageBar.create(this, player, page, pageCount, label + " §8· §7" + items.size(), newPage -> {
            page = newPage;
            update();
        });
        setItem(UiPosition.of(5, 3), bar[0]);
        setItem(UiPosition.of(5, 4), bar[1]);
        setItem(UiPosition.of(5, 5), bar[2]);

        if (SavedItemCategories.ALL.equals(categoryId) && !items.isEmpty() && ItemEditor.INSTANCE.getStyleService().isVanillaStyleEnabled()) {
            setItem(UiPosition.of(5, 8), new ActionUiItem(this, player,
                    ItemBuilder.uiItem(Material.ENCHANTED_BOOK, lang.msg("gui-bulk-name"), lang.msg("gui-bulk-lore")),
                    this::openBulkTemplates));
        }

        fillEmpty(UiPosition.of(5, 0), UiPosition.of(5, 8), new FillerUiItem(this, player));
    }

    private void openBulkTemplates() {
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        open(new TemplatePickerUiScreen(player, lang.msg("gui-hint-all"), this::confirmBulk, null,
                () -> open(new ItemListUiScreen(player, categoryId, page))));
    }

    private void confirmBulk(Template template) {
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        final int count = ItemEditor.INSTANCE.getItemStorage().getItems().size();
        open(new ConfirmUiScreen(player, lang.msg("gui-confirm-title"),
                lang.msg("gui-confirm-info", "%template%", template.getName(), "%count%", String.valueOf(count)),
                lang.msg("gui-confirm-warning"),
                () -> {
                    final ItemEditor editor = ItemEditor.INSTANCE;
                    editor.applyTemplateToAllSaved(template).thenAcceptAsync(done -> {
                        if (player.isOnline()) {
                            player.sendMessage(lang.prefixed("template-bulk-done", "%template%", template.getName(),
                                    "%count%", String.valueOf(done)));
                        }
                    }, editor.getItemStorage().mainThreadExecutor());
                    open(new ItemListUiScreen(player, categoryId, page));
                },
                () -> open(new ItemListUiScreen(player, categoryId, page))));
    }
}

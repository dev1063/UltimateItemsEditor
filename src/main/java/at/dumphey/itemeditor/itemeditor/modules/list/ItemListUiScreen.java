package at.dumphey.itemeditor.itemeditor.modules.list;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.list.items.ManageItemFromListUiItem;
import at.dumphey.itemeditor.itemeditor.modules.template.ConfirmUiScreen;
import at.dumphey.itemeditor.itemeditor.modules.template.TemplatePickerUiScreen;
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

/**
 * Paginated list of every saved item. Clicking an item opens {@link ItemActionsUiScreen}; the bottom-right button
 * applies one template to all saved items at once (after a confirmation).
 */
public class ItemListUiScreen extends UiScreen {

    private static final int PAGE_SIZE = 45;

    private int page;

    public ItemListUiScreen(Player player) {
        this(player, 0);
    }

    public ItemListUiScreen(Player player, int page) {
        super(player, 54, "Saved Items");
        this.page = Math.max(0, page);
    }

    private List<SerializedItem> getItems() {
        return ItemEditor.INSTANCE.getItemStorage().getSortedItems();
    }

    @Override
    protected void onUpdate() {
        final List<SerializedItem> items = getItems();
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        final int pageCount = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pageCount) {
            page = pageCount - 1; // e.g. after deleting the last item of the last page
        }

        int slot = 0;
        for (int i = page * PAGE_SIZE; i < items.size() && i < (page + 1) * PAGE_SIZE; i++) {
            setItem(slot++, new ManageItemFromListUiItem(this, player, items.get(i), page));
        }

        if (items.isEmpty()) {
            setItem(22, new StaticUiItem(this, player, ItemBuilder.uiItem(Material.BARRIER, "No items saved.",
                    "Use §8§o/ie save <name>\n§8to save an item.")));
        }

        final UiItem[] bar = PageBar.create(this, player, page, pageCount, items.size() + " saved item(s)", newPage -> {
            page = newPage;
            update();
        });
        setItem(UiPosition.of(5, 3), bar[0]);
        setItem(UiPosition.of(5, 4), bar[1]);
        setItem(UiPosition.of(5, 5), bar[2]);

        if (!items.isEmpty()) {
            setItem(UiPosition.of(5, 8), new ActionUiItem(this, player,
                    ItemBuilder.uiItem(Material.ENCHANTED_BOOK, lang.msg("gui-bulk-name"), lang.msg("gui-bulk-lore")),
                    this::openBulkTemplates));
        }

        fillEmpty(UiPosition.of(5, 0), UiPosition.of(5, 8), new FillerUiItem(this, player));
    }

    private void openBulkTemplates() {
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        open(new TemplatePickerUiScreen(player, lang.msg("gui-hint-all"), this::confirmBulk, null,
                () -> open(new ItemListUiScreen(player, page))));
    }

    private void confirmBulk(Template template) {
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        final int count = ItemEditor.INSTANCE.getItemStorage().getItems().size();
        open(new ConfirmUiScreen(player, lang.msg("gui-confirm-title"),
                lang.msg("gui-confirm-info", "%template%", template.getName(), "%count%", String.valueOf(count)),
                lang.msg("gui-confirm-warning"),
                () -> {
                    final int done = ItemEditor.INSTANCE.applyTemplateToAllSaved(template);
                    player.sendMessage(lang.prefixed("template-bulk-done", "%template%", template.getName(),
                            "%count%", String.valueOf(done)));
                    open(new ItemListUiScreen(player, page));
                },
                () -> open(new ItemListUiScreen(player, page))));
    }
}

package at.dumphey.itemeditor.ui.design;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.styler.LanguageManager;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.items.StaticUiItem;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.IntConsumer;

/**
 * The previous / page / next controls every paginated menu uses, so they look and behave the same everywhere. The
 * caller puts the three items where it wants them (the footer row is conventional: columns 3, 4 and 5).
 */
public final class PageBar {

    private PageBar() {
    }

    /**
     * @param page      current page, 0-based
     * @param pageCount total pages (at least 1)
     * @param detail    second line of the indicator (for example "36 items"), may be empty
     * @param onPage    called with the new page; it should store it and call {@code screen.update()}
     * @return [previous or null, indicator, next or null]
     */
    public static UiItem[] create(UiScreen screen, Player player, int page, int pageCount, String detail,
                                  IntConsumer onPage) {
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        final UiItem[] bar = new UiItem[3];

        if (page > 0) {
            bar[0] = new ActionUiItem(screen, player, ItemBuilder.uiItem(Material.ARROW, lang.msg("gui-prev"),
                    lang.msg("gui-prev-lore", "%page%", String.valueOf(page))), () -> onPage.accept(page - 1));
        }

        final String title = lang.msg("gui-page", "%page%", String.valueOf(page + 1), "%pages%", String.valueOf(pageCount));
        final ItemStack indicator = detail == null || detail.isEmpty()
                ? ItemBuilder.uiItem(Material.PAPER, title)
                : ItemBuilder.uiItem(Material.PAPER, title, detail);
        indicator.setAmount(Math.max(1, Math.min(64, page + 1))); // the stack size doubles as a page counter
        bar[1] = new StaticUiItem(screen, player, indicator);

        if (page < pageCount - 1) {
            bar[2] = new ActionUiItem(screen, player, ItemBuilder.uiItem(Material.ARROW, lang.msg("gui-next"),
                    lang.msg("gui-next-lore", "%page%", String.valueOf(page + 2))), () -> onPage.accept(page + 1));
        }
        return bar;
    }

    /** Number of pages for {@code total} entries (never less than 1). */
    public static int pages(int total, int perPage) {
        return Math.max(1, (total + perPage - 1) / perPage);
    }
}

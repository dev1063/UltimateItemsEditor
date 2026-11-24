package at.dumphey.itemeditor.itemeditor.modules.home;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.home.items.GetItemUiItem;
import at.dumphey.itemeditor.styler.LanguageManager;
import at.dumphey.itemeditor.ui.UiPosition;
import at.dumphey.itemeditor.ui.design.PageBar;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.items.StaticUiItem;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import at.dumphey.itemeditor.utils.NameUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The editor home menu (54 slots):
 *
 * <pre>
 * row 0   [item] [tab][tab][tab][tab][tab][tab][tab] [info]     the item (click = finish), category tabs, item info
 * row 1   colored rail in the accent color of the open tab
 * rows 2-4  the options of the open tab, 27 per page
 * row 5   [cancel]  .  .  [prev][page][next]  .  .  [finish]
 * </pre>
 *
 * What is shown comes from {@link EditorMenu}, so new options and categories do not touch this class. The tab and
 * page are remembered per player: going back from a submenu returns to where you were.
 */
public class HomeUiScreen extends UiScreen {

    private static final int OPTIONS_PER_PAGE = 27;
    private static final int TAB_COLUMNS = 7;
    private static final int FIRST_OPTION_SLOT = UiPosition.of(2, 0);

    private final EditorMenu.View view;

    public HomeUiScreen(Player player) {
        super(player, 54, ItemEditor.INSTANCE.getStyleService().getLang().msg("gui-hub-title"));
        this.view = ItemEditor.INSTANCE.getEditorMenu().view(player.getUniqueId());
    }

    @Override
    protected void onUpdate() {
        final EditorMenu menu = ItemEditor.INSTANCE.getEditorMenu();
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        final List<EditorCategory> categories = menu.categories();

        setItem(UiPosition.of(0, 0), new GetItemUiItem(this, player));
        setItem(UiPosition.of(0, 8), infoItem(lang));

        if (categories.isEmpty()) {
            setItem(22, new StaticUiItem(this, player, ItemBuilder.uiItem(Material.BARRIER, lang.msg("gui-hub-empty"))));
            addFooter(lang, 1);
            fillEmptyWithFiller();
            return;
        }

        EditorCategory current = menu.category(view.getCategoryId());
        if (current == null || !categories.contains(current)) {
            current = categories.get(0);
            view.setCategoryId(current.getId());
            view.setPage(0);
        }

        addTabs(menu, lang, categories, current);

        // colored rail: the accent of the open tab
        final StaticUiItem rail = new StaticUiItem(this, player,
                ItemBuilder.of(current.getAccent()).withName("\u00a70").withAllHideFlags().build());
        for (int x = 0; x < 9; x++) {
            setItem(UiPosition.of(1, x), rail);
        }

        // options, with pages
        final List<EditorMenu.Option> options = menu.options(current.getId());
        final int pageCount = PageBar.pages(options.size(), OPTIONS_PER_PAGE);
        if (view.getPage() >= pageCount || view.getPage() < 0) {
            view.setPage(pageCount - 1);
        }
        final int first = view.getPage() * OPTIONS_PER_PAGE;
        for (int i = 0; i < OPTIONS_PER_PAGE && first + i < options.size(); i++) {
            setItem(FIRST_OPTION_SLOT + i, options.get(first + i).create(this, player));
        }

        addFooter(lang, pageCount);
        fillEmptyWithFiller();
    }

    private void addTabs(EditorMenu menu, LanguageManager lang, List<EditorCategory> categories, EditorCategory current) {
        final boolean needMore = categories.size() > TAB_COLUMNS;
        final int perWindow = needMore ? TAB_COLUMNS - 1 : TAB_COLUMNS;
        if (view.getTabStart() >= categories.size() || view.getTabStart() < 0) {
            view.setTabStart(0);
        }
        for (int i = 0; i < perWindow && view.getTabStart() + i < categories.size(); i++) {
            final EditorCategory category = categories.get(view.getTabStart() + i);
            final boolean selected = category.equals(current);
            setItem(UiPosition.of(0, 1 + i), new CategoryTabUiItem(this, player, category, selected,
                    menu.options(category.getId()).size(), () -> {
                view.setCategoryId(category.getId());
                view.setPage(0);
                update();
            }));
        }
        if (needMore) {
            setItem(UiPosition.of(0, TAB_COLUMNS), new ActionUiItem(this, player,
                    ItemBuilder.uiItem(Material.SPECTRAL_ARROW, lang.msg("gui-hub-more"), lang.msg("gui-hub-more-lore")), () -> {
                final int next = view.getTabStart() + perWindow;
                view.setTabStart(next >= categories.size() ? 0 : next);
                update();
            }));
        }
    }

    private void addFooter(LanguageManager lang, int pageCount) {
        final boolean editingSaved = ItemEditor.INSTANCE.isEditingSaved(player);

        if (editingSaved) {
            setItem(UiPosition.of(5, 0), new ActionUiItem(this, player,
                    ItemBuilder.uiItem(Material.BARRIER, lang.msg("gui-hub-cancel"), lang.msg("gui-hub-cancel-lore")),
                    () -> ItemEditor.INSTANCE.cancelSavedEdit(player)));
        }

        if (pageCount > 1) {
            final UiItem[] bar = PageBar.create(this, player, view.getPage(), pageCount,
                    "", page -> {
                        view.setPage(page);
                        update();
                    });
            setItem(UiPosition.of(5, 3), bar[0]);
            setItem(UiPosition.of(5, 4), bar[1]);
            setItem(UiPosition.of(5, 5), bar[2]);
        }

        setItem(UiPosition.of(5, 8), new ActionUiItem(this, player,
                ItemBuilder.uiItem(Material.LIME_DYE, lang.msg("gui-hub-finish"), editingSaved
                        ? lang.msg("gui-hub-finish-saved-lore", "%name%", ItemEditor.INSTANCE.getEditingSavedName(player))
                        : lang.msg("gui-hub-finish-lore")),
                () -> ItemEditor.INSTANCE.finish(player)));
    }

    /** A summary of the item being edited. */
    private UiItem infoItem(LanguageManager lang) {
        final ItemStack item = ItemEditor.INSTANCE.getItem(player);
        final List<String> lines = new ArrayList<>();
        if (item != null) {
            lines.add(lang.msg("gui-hub-info-type", "%type%", NameUtils.enumToFriendlyName(item.getType().name())));
            lines.add(lang.msg("gui-hub-info-amount", "%amount%", String.valueOf(item.getAmount())));
            final String template = ItemEditor.INSTANCE.modules().vanilla()
                    ? ItemEditor.INSTANCE.getStyleService().getMetadata().getAppliedTemplate(item.getItemMeta()) : null;
            if (template != null) {
                lines.add(lang.msg("gui-hub-info-template", "%template%", template));
            }
        }
        if (ItemEditor.INSTANCE.isEditingSaved(player)) {
            lines.add(lang.msg("gui-hub-info-saved", "%name%", ItemEditor.INSTANCE.getEditingSavedName(player)));
        }
        return new StaticUiItem(this, player, ItemBuilder.uiItem(Material.PAPER, lang.msg("gui-hub-info-name"),
                lines.toArray(new String[0])));
    }
}

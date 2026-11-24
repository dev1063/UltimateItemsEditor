package at.dumphey.itemeditor.itemeditor.modules.home;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.styler.ColorFormat;
import at.dumphey.itemeditor.styler.LanguageManager;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** One tab of the home menu. The open tab glows. */
final class CategoryTabUiItem extends UiItem {

    private final EditorCategory category;
    private final boolean selected;
    private final int optionCount;
    private final Runnable onSelect;

    CategoryTabUiItem(UiScreen screen, Player player, EditorCategory category, boolean selected, int optionCount,
                      Runnable onSelect) {
        super(screen, player);
        this.category = category;
        this.selected = selected;
        this.optionCount = optionCount;
        this.onSelect = onSelect;
    }

    @Override
    protected ItemStack onRender() {
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        final String name = ColorFormat.toLegacy(lang.rawOr("gui-cat-" + category.getId(), category.getName()));
        final String description = ColorFormat.toLegacy(lang.rawOr("gui-cat-" + category.getId() + "-desc", category.getDescription()));

        final List<String> lore = new ArrayList<>();
        lore.add("\u00a78" + description);
        lore.add("");
        lore.add(selected ? lang.msg("gui-hub-tab-selected") : lang.msg("gui-hub-tab-click"));
        lore.add("\u00a78" + lang.msg("gui-hub-tab-count", "%count%", String.valueOf(optionCount)));

        final ItemBuilder builder = ItemBuilder.of(category.getIcon())
                                               .withName((selected ? "\u00a7a\u00a7l" : "\u00a77") + name)
                                               .withLore(lore)
                                               .withAllHideFlags();
        if (selected) {
            builder.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return builder.build();
    }

    @Override
    protected void onClick(ClickType clickType) {
        if (clickType.isLeftClick() && !selected) {
            onSelect.run();
        }
    }
}

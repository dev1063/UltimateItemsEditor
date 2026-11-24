package at.dumphey.itemeditor.itemeditor.modules.template;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.styler.ColorFormat;
import at.dumphey.itemeditor.styler.LanguageManager;
import at.dumphey.itemeditor.styler.StyleService;
import at.dumphey.itemeditor.styler.Text;
import at.dumphey.itemeditor.styler.model.Template;
import at.dumphey.itemeditor.ui.UiPosition;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.design.PageBar;
import at.dumphey.itemeditor.ui.items.FillerUiItem;
import at.dumphey.itemeditor.ui.items.StaticUiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.Format;
import at.dumphey.itemeditor.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Paginated list of every template with a preview of what it adds to an item. The same screen is used to apply a
 * template to the item being edited, to the item in the hand, to a saved item or to all saved items: the caller
 * decides what a click does.
 */
public class TemplatePickerUiScreen extends UiScreen {

    private static final int PAGE_SIZE = 36;

    private final String hint;
    private final Consumer<Template> onPick;
    private final Runnable onReset;
    private final Runnable onBack;
    private int page = 0;

    /**
     * @param hint    localized "Click to apply to ..." line shown on every template
     * @param onPick  called when a template is clicked
     * @param onReset called by the "remove template" button; null hides the button
     * @param onBack  called by the back button
     */
    public TemplatePickerUiScreen(Player player, String hint, Consumer<Template> onPick, Runnable onReset,
                                  Runnable onBack) {
        super(player, 54, ItemEditor.INSTANCE.getStyleService().getLang().msg("gui-title-templates"));
        this.hint = hint;
        this.onPick = onPick;
        this.onReset = onReset;
        this.onBack = onBack;
    }

    @Override
    protected void onUpdate() {
        final StyleService styler = ItemEditor.INSTANCE.getStyleService();
        final LanguageManager lang = styler.getLang();
        final List<Template> templates = styler.getTemplates().all();

        final int pageCount = Math.max(1, (templates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pageCount) {
            page = pageCount - 1;
        }

        int slot = 0;
        for (int i = page * PAGE_SIZE; i < templates.size() && i < (page + 1) * PAGE_SIZE; i++) {
            final Template template = templates.get(i);
            setItem(slot++, new ActionUiItem(this, player, icon(template, styler, lang), () -> onPick.accept(template)));
        }

        if (templates.isEmpty()) {
            setItem(22, new StaticUiItem(this, player, ItemBuilder.uiItem(Material.BARRIER,
                    lang.msg("gui-no-templates"), lang.msg("gui-no-templates-lore"))));
        }

        // Bottom row
        setItem(UiPosition.of(5, 0), new ActionUiItem(this, player,
                ItemBuilder.uiItem(Material.ARROW, lang.msg("gui-back"), lang.msg("gui-back-lore")), onBack));

        if (onReset != null) {
            setItem(UiPosition.of(5, 2), new ActionUiItem(this, player,
                    ItemBuilder.uiItem(Material.MILK_BUCKET, lang.msg("gui-remove-name"), lang.msg("gui-remove-lore")),
                    onReset));
        }

        final UiItem[] bar = PageBar.create(this, player, page, pageCount, "", newPage -> {
            page = newPage;
            update();
        });
        setItem(UiPosition.of(5, 3), bar[0]);
        setItem(UiPosition.of(5, 4), bar[1]);
        setItem(UiPosition.of(5, 5), bar[2]);

        setItem(UiPosition.of(5, 7), new ActionUiItem(this, player, ItemBuilder.uiItem(Material.BOOK,
                lang.msg("gui-language-name", "%lang%", lang.getActiveLang()),
                lang.msg("gui-language-lore"),
                lang.msg("gui-language-available", "%list%", String.join(", ", lang.availableLanguages()))),
                () -> {
                    styler.cycleLanguage();
                    update();
                }));

        fillEmpty(UiPosition.of(5, 0), UiPosition.of(5, 8), new FillerUiItem(this, player));
    }

    private ItemStack icon(Template template, StyleService styler, LanguageManager lang) {
        final List<String> lore = new ArrayList<>();
        lore.add(Format.description(hint));
        lore.add("");

        final String prefix = lang.resolveTemplateText(template.getDisplayNamePrefix());
        if (!Text.isBlank(prefix)) {
            lore.add("\u00a77" + lang.msg("gui-preview-name") + "\u00a7r" + ColorFormat.toLegacyPrefix(prefix) + "Item");
        }
        lore.addAll(styler.getLoreBuilder().renderLines(template.getHeader()));
        lore.add("\u00a78...");
        lore.addAll(styler.getLoreBuilder().renderLines(template.getFooter()));

        return ItemBuilder.of(resolveIcon(template.getIcon()))
                          .withName(Format.name(template.getName()))
                          .withLore(lore)
                          .build();
    }

    private static Material resolveIcon(String name) {
        if (name != null) {
            final Material material = Material.matchMaterial(name);
            if (material != null && material.isItem() && material != Material.AIR) {
                return material;
            }
        }
        return Material.PAPER;
    }
}

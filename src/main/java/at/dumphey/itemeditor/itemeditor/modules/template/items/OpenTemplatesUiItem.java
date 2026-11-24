package at.dumphey.itemeditor.itemeditor.modules.template.items;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.template.TemplatePickerUiScreen;
import at.dumphey.itemeditor.styler.LanguageManager;
import at.dumphey.itemeditor.styler.StyleService;
import at.dumphey.itemeditor.styler.model.Template;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Home-screen button: opens the template picker for the item being edited. */
public class OpenTemplatesUiItem extends UiItem {

    public OpenTemplatesUiItem(UiScreen screen, Player player) {
        super(screen, player);
        requirePermission("iep.edit.template");
    }

    @Override
    protected ItemStack onRender() {
        final StyleService styler = ItemEditor.INSTANCE.getStyleService();
        final LanguageManager lang = styler.getLang();

        final List<String> description = new ArrayList<>();
        description.add(lang.msg("gui-home-template-lore"));
        final ItemStack edited = getEditedItem();
        final String applied = edited == null ? null : styler.getMetadata().getAppliedTemplate(edited.getItemMeta());
        if (applied != null) {
            description.add(lang.msg("gui-home-template-current", "%template%", applied));
        }
        return ItemBuilder.uiItem(Material.ENCHANTED_BOOK, lang.msg("gui-home-template-name"),
                description.toArray(new String[0]));
    }

    @Override
    protected void onClick(ClickType clickType) {
        if (!clickType.isLeftClick()) {
            return;
        }
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        open(new TemplatePickerUiScreen(player, lang.msg("gui-hint-edited-item"), this::applyTemplate,
                this::removeTemplate, () -> ItemEditor.INSTANCE.openHome(player)));
    }

    private void applyTemplate(Template template) {
        final StyleService styler = ItemEditor.INSTANCE.getStyleService();
        final boolean[] applied = new boolean[1];
        modifyItem(item -> {
            applied[0] = styler.apply(item, template);
            return item;
        });
        player.sendMessage(applied[0]
                ? styler.getLang().prefixed("template-applied", "%template%", template.getName())
                : styler.getLang().prefixed("template-apply-failed"));
        ItemEditor.INSTANCE.openHome(player);
    }

    private void removeTemplate() {
        final StyleService styler = ItemEditor.INSTANCE.getStyleService();
        final boolean[] removed = new boolean[1];
        modifyItem(item -> {
            removed[0] = styler.reset(item);
            return item;
        });
        player.sendMessage(removed[0]
                ? styler.getLang().prefixed("template-reset-success")
                : styler.getLang().prefixed("template-reset-none"));
        ItemEditor.INSTANCE.openHome(player);
    }
}

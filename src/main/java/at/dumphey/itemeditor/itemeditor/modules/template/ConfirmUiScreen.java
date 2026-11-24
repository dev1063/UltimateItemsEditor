package at.dumphey.itemeditor.itemeditor.modules.template;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.styler.LanguageManager;
import at.dumphey.itemeditor.ui.UiPosition;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.items.StaticUiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Small yes/no screen for actions that change many things at once. */
public class ConfirmUiScreen extends UiScreen {

    private final String info;
    private final String warning;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmUiScreen(Player player, String title, String info, String warning, Runnable onConfirm,
                           Runnable onCancel) {
        super(player, 27, title);
        this.info = info;
        this.warning = warning;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void onUpdate() {
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();

        setItem(UiPosition.of(1, 2), new ActionUiItem(this, player,
                ItemBuilder.uiItem(Material.LIME_CONCRETE, lang.msg("gui-confirm-yes")), onConfirm));
        setItem(UiPosition.of(1, 4), new StaticUiItem(this, player,
                ItemBuilder.uiItem(Material.PAPER, info, warning)));
        setItem(UiPosition.of(1, 6), new ActionUiItem(this, player,
                ItemBuilder.uiItem(Material.RED_CONCRETE, lang.msg("gui-confirm-no")), onCancel));
        fillEmptyWithFiller();
    }
}

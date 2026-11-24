package at.dumphey.itemeditor.itemeditor.modules.list;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.list.items.ManageItemFromListUiItem;
import at.dumphey.itemeditor.itemeditor.modules.template.TemplatePickerUiScreen;
import at.dumphey.itemeditor.itemeditor.storage.SerializedItem;
import at.dumphey.itemeditor.styler.LanguageManager;
import at.dumphey.itemeditor.styler.model.Template;
import at.dumphey.itemeditor.ui.UiPosition;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.items.StaticUiItem;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import at.dumphey.itemeditor.utils.Messages;
import at.dumphey.itemeditor.utils.NameUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Management screen of one saved item: preview, get, give to a player, edit, apply a template and delete
 * (with confirmation).
 */
public class ItemActionsUiScreen extends UiScreen {

    private final String itemName;
    private final int listPage;
    private boolean confirmingDelete = false;

    public ItemActionsUiScreen(Player player, String itemName, int listPage) {
        super(player, 45, "Manage item");
        this.itemName = itemName;
        this.listPage = listPage;
    }

    private UiItem backItem() {
        return new ActionUiItem(this, player, ItemBuilder.uiItem(Material.ARROW, "§cBack", "Back to the item list"),
                () -> open(new ItemListUiScreen(player, listPage)));
    }

    @Override
    protected void onUpdate() {
        final SerializedItem stored = ItemEditor.INSTANCE.getItemStorage().getItem(itemName);
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();

        setItem(44, backItem());

        if (stored == null) {
            setItem(UiPosition.of(2, 4), new StaticUiItem(this, player,
                    ItemBuilder.uiItem(Material.BARRIER, "Item not found", "It may have been deleted.")));
            fillEmptyWithFiller();
            return;
        }

        // A broken entry (unknown material -> AIR, no ItemMeta) can only be deleted.
        final boolean usable = ManageItemFromListUiItem.isUsable(stored);

        // Preview: the item exactly as it is stored (no extra lore).
        setItem(UiPosition.of(1, 4), new StaticUiItem(this, player, usable
                ? stored.getItemStack().clone()
                : ItemBuilder.uiItem(Material.BARRIER, "§cInvalid item",
                        "Its material is unknown or air.", "You can only delete it.")));

        final String appliedTemplate = ItemEditor.INSTANCE.getStyleService().getMetadata()
                                                          .getAppliedTemplate(stored.getItemStack().getItemMeta());
        final String templateLine = appliedTemplate == null ? "Template: §fnone" : "Template: §f" + appliedTemplate;
        setItem(UiPosition.of(1, 6), new StaticUiItem(this, player, ItemBuilder.uiItem(Material.PAPER, "Item info",
                "Saved as: §f" + stored.getName(),
                "Type: §f" + NameUtils.enumToFriendlyName(stored.getItemStack().getType().name()),
                "Amount: §f" + stored.getItemStack().getAmount(),
                templateLine)));

        if (confirmingDelete) {
            setItem(UiPosition.of(3, 3), new ActionUiItem(this, player,
                    ItemBuilder.uiItem(Material.RED_CONCRETE, "§cConfirm delete",
                            "Permanently delete §f" + stored.getName(), "§cThis cannot be undone."),
                    this::deleteItem));
            setItem(UiPosition.of(3, 5), new ActionUiItem(this, player,
                    ItemBuilder.uiItem(Material.LIME_CONCRETE, "§aKeep item", "Cancel and keep the item"),
                    () -> {
                        confirmingDelete = false;
                        update();
                    }));
        } else {
            if (usable) {
                setItem(UiPosition.of(3, 0), new ActionUiItem(this, player,
                        ItemBuilder.uiItem(Material.CHEST, "Get",
                                "Add §f" + stored.getItemStack().getAmount() + "x §8to your inventory"),
                        () -> getItem(stored)));
                setItem(UiPosition.of(3, 2), new ActionUiItem(this, player,
                        ItemBuilder.uiItem(Material.PLAYER_HEAD, "Give to player",
                                "Choose an online player and an amount"),
                        () -> open(new ItemGiveUiScreen(player, itemName, listPage))));
                setItem(UiPosition.of(3, 4), new ActionUiItem(this, player,
                        ItemBuilder.uiItem(Material.ANVIL, "Edit",
                                "Open the editor with this item\n§8Your hand is not touched; changes are\n§8saved back to this entry"),
                        () -> ItemEditor.INSTANCE.editSaved(player, stored, listPage)));
                setItem(UiPosition.of(3, 6), new ActionUiItem(this, player,
                        ItemBuilder.uiItem(Material.ENCHANTED_BOOK, lang.msg("gui-actions-template-name"),
                                lang.msg("gui-actions-template-lore")),
                        this::openTemplates));
            }
            setItem(UiPosition.of(3, usable ? 8 : 4), new ActionUiItem(this, player,
                    ItemBuilder.uiItem(Material.LAVA_BUCKET, "§cDelete", "Delete this saved item"),
                    () -> {
                        confirmingDelete = true;
                        update();
                    }));
        }

        fillEmptyWithFiller();
    }

    private void openTemplates() {
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        open(new TemplatePickerUiScreen(player, lang.msg("gui-hint-saved"), this::applyTemplate, this::removeTemplate,
                () -> open(new ItemActionsUiScreen(player, itemName, listPage))));
    }

    private void applyTemplate(Template template) {
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        if (ItemEditor.INSTANCE.applyTemplateToSaved(itemName, template)) {
            player.sendMessage(lang.prefixed("template-saved", "%name%", itemName, "%template%", template.getName()));
        } else {
            player.sendMessage(lang.prefixed("template-save-failed"));
        }
        open(new ItemActionsUiScreen(player, itemName, listPage));
    }

    private void removeTemplate() {
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        if (ItemEditor.INSTANCE.resetTemplateOfSaved(itemName)) {
            player.sendMessage(lang.prefixed("template-saved-reset", "%name%", itemName));
        } else {
            player.sendMessage(lang.prefixed("template-reset-none"));
        }
        open(new ItemActionsUiScreen(player, itemName, listPage));
    }

    private void getItem(SerializedItem stored) {
        final int amount = stored.getItemStack().getAmount();
        final int dropped = ItemEditor.INSTANCE.giveItem(player, stored, amount);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        Messages.send(player, "Received §7" + amount + "x §e" + stored.getName() + "§f."
                + (dropped > 0 ? " §c(" + dropped + " dropped on the ground: inventory full)" : ""));
    }

    private void deleteItem() {
        if (ItemEditor.INSTANCE.getItemStorage().deleteItem(itemName)) {
            Messages.send(player, "§aThe item §o" + itemName + " §awas deleted.");
        } else {
            Messages.send(player, "§cAn item called §o" + itemName + "§c could not be found.");
        }
        open(new ItemListUiScreen(player, listPage));
    }
}

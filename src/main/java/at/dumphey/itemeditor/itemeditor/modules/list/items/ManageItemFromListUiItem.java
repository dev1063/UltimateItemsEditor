package at.dumphey.itemeditor.itemeditor.modules.list.items;

import at.dumphey.itemeditor.UltimateItemsEditor;
import at.dumphey.itemeditor.itemeditor.modules.list.ItemActionsUiScreen;
import at.dumphey.itemeditor.itemeditor.storage.SerializedItem;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

/**
 * A saved item inside the paginated list. Clicking it opens the item's management screen.
 * A saved entry whose material no longer exists (the server reads it as AIR and gives it no ItemMeta) is shown as
 * a red barrier so it can still be opened and deleted instead of breaking the whole list.
 */
public class ManageItemFromListUiItem extends UiItem {

    private final SerializedItem item;
    private final int listPage;

    public ManageItemFromListUiItem(UiScreen screen, Player player, SerializedItem item, int listPage) {
        super(screen, player);
        this.item = item;
        this.listPage = listPage;
    }

    /** True if the stored item can be shown, edited and given (it has a real material and an ItemMeta). */
    public static boolean isUsable(SerializedItem item) {
        final ItemStack stack = item.getItemStack();
        return stack != null && stack.getType() != Material.AIR && stack.getItemMeta() != null;
    }

    @Override
    protected ItemStack onRender() {
        ItemStack icon = item.getListIcon();
        if (icon != null) {
            return icon;
        }
        try {
            if (isUsable(item)) {
                icon = ItemBuilder.of(item.getItemStack().clone())
                        .withAppendLore("", "§8§l* §e" + item.getName(), "  §7Click to §omanage §7this item")
                        .build();
            }
        } catch (RuntimeException ex) {
            UltimateItemsEditor.getInstance().getLogger()
                    .log(Level.WARNING, "Could not build the saved item icon for '" + item.getName() + "'", ex);
        }
        if (icon == null) {
            icon = ItemBuilder.uiItem(Material.BARRIER, "§c" + item.getName(),
                    "§cThis item cannot be previewed.", "Click to manage this entry.");
        }
        item.setListIcon(icon);
        return icon;
    }

    @Override
    protected void onClick(ClickType clickType) {
        if (clickType.isLeftClick() || clickType.isRightClick()) {
            open(new ItemActionsUiScreen(player, item.getName(), listPage));
        }
    }
}
